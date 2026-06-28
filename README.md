# Low-Latency L3 Order Book

Single-instrument Level-3 limit order book in Java. The primary implementation uses a compact
best-to-worst **array of active price levels**, an **intrusive FIFO queue per level**, and a
**global order-ID index**. A conventional `TreeMap` implementation is kept as a correctness
oracle and benchmark baseline — so the design choice is validated, not just asserted.

## Build & run

```bash
mvn test                       # unit, contract, invariant, and differential tests
./scripts/run-benchmarks.sh    # JMH latency comparison (~1 min; not part of `mvn test`)
```

Requires JDK 21+ and Maven 3.9+. (macOS: `brew install openjdk@21 maven`.)

## Design evolution: baseline → array → allocation-free

This wasn't one leap. It's a textbook baseline plus two deliberate, *measured* improvements — each
validated against the one before it.

**V1 — `TreeMap` baseline (the textbook-correct version).** `TreeMap<price, PriceLevel>` per side,
`LinkedHashMap<orderId, Order>` per level. Every operation O(log P); obviously correct; the version a
competent engineer writes first. It is still in the repo (`reference/TreeMapOrderBook`) — not thrown
away but *promoted* to the correctness oracle and benchmark baseline.
- *Trade-off:* a red-black tree pointer-chases, has no O(1) by-level access, and allocates on every
  read. Fine for correctness, not for a latency-sensitive, iteration-heavy workload.

**V2 — compact active-level array + intrusive FIFO (the data-structure redesign).** Replace the
per-side tree with a `PriceLevel[]` held physically best→worst, and the per-level map with an intrusive
doubly-linked FIFO plus a single global `orderId → OrderNode` index.
- *Wins:* `getByLevel(k)` → **O(1)**; best→worst iteration → sequential reference walk (locality);
  cancel → **O(1)** via the global index + intrusive unlink, with **no redundant per-level map**.
- *Trade-off accepted:* inserting/removing a *level* shifts up to P references via `System.arraycopy`
  (O(P)) — bounded, and measured to beat tree node-allocation + rebalancing at this depth. A price
  change loses time priority (modeled as remove + add — deliberate, not a bug).
- *Why:* the brief's workload is shallow, by-level-indexed and iteration-heavy — exactly where
  contiguous memory beats a tree.

**V3 — allocation-free iteration + measured hardening (the production-efficiency layer).** Add
`forEachLevel` / `forEachOrder` visitors that walk the whole book best→worst with **zero allocation**
(snapshots stay, but only for diagnostics and differential testing).
- This step was driven by a **review loop**: an independent review flagged that the original
  "iteration" benchmark actually measured *repeated indexed access* — the tree re-walked from the front
  and allocated an iterator on every call. Verified against the source, the benchmark was split into
  honest cases; the corrected, *fair* iterator benchmark **strengthened** the result (true iteration
  ~8.9× vs the earlier, flawed ~6×).
- *Trade-off:* two tiny `@FunctionalInterface`s of extra API — the zero-alloc shape the low-latency
  requirement demands, not a framework.

**Why this is the right stopping point.** Each version is *proven* equal-or-better, not asserted: a
400,000-op differential test pins V2/V3 to the V1 oracle, and every performance claim is a JMH
measurement with its caveats stated. The remaining ideas (see *Potential improvements*) trade real
complexity for wins this exercise doesn't need.

> The point of the AI-assisted workflow here isn't "AI wrote an order book." It's that AI ran **under a
> verification harness** — every evolution checked against the baseline by differential testing, plus an
> independent review pass that caught a benchmark measuring the wrong thing (see `AI_USAGE.md`). The
> leverage is speed *with* a correctness floor, not speed instead of one.

## Why this structure (in order of how decisive each point is)

1. **Required-operation advantage — `getByLevel(k)`.** Locating the k-th level is **O(1)** in the
   active-level array (`levels[k]`) versus **O(log P + k)** in the TreeMap baseline (find the first
   entry, then advance k — a red-black tree keeps no rank index). Materializing the M orders at that
   level is **O(M) for both**; the array's win is purely in *location*.
2. **Traversal layout.** Active levels are a contiguous array of references held best→worst, so
   best-to-worst iteration is a sequential reference walk rather than tree node-pointer chasing.
   (The `PriceLevel` objects are still heap-allocated — the win is reference locality, not packed
   structs.)
3. **Bounded structural mutation.** Inserting a new level shifts at most P references via
   `System.arraycopy` (P = current active depth). The exercise illustrates shallow depth; the array
   grows dynamically and `trim` bounds long-term depth.
4. **Cache locality.** Treated as a *measured hypothesis*, validated by JMH below — not asserted
   from CPU-latency constants.
5. **Measured results.** The data decides whether the trade was worth it (see Benchmark).

## Data structures

```
OrderBook (interface)
├── ActiveArrayOrderBook (primary)
│   ├── bids / asks : HalfBook  → PriceLevel[] held best→worst
│   │                            → each level: intrusive FIFO  head ⇄ … ⇄ tail
│   └── orderIndex  : HashMap<orderId, OrderNode>   (pre-sized)
└── reference.TreeMapOrderBook (oracle + benchmark baseline)
    └── TreeMap<price, PriceLevel> + LinkedHashMap per level
```

The primary uses **no per-level hash map**: the global index already locates any order by ID, so a
per-level map would be a redundant second index. Cancellation is a global-index lookup followed by
an O(1) intrusive unlink.

The two implementations share only immutable snapshot DTOs (`BookSnapshot` / `LevelSnapshot` /
`OrderSnapshot`) — **no mutation algorithm, comparator, or trim logic crosses the boundary**, which
is what makes the differential test meaningful.

**Iteration is allocation-free.** `forEachLevel(side, visitor)` and `forEachOrder(side, visitor)` walk
the book best→worst — FIFO within each level — over the intrusive links and hand the caller raw
primitives, allocating nothing. `snapshot()` / `getByLevel` remain the *allocating* path, for
diagnostics and differential testing where an immutable copy is the point.

**Abstraction budget.** The brief says *avoid unnecessary abstractions*. There is exactly one: the
`OrderBook` interface — and it earns its keep, because a second implementation is what makes the
differential test an *independent* oracle (without it, the book is only ever checked against itself).
Everything else was declined: no per-level map, no strategy/factory, no config object, no generic
price type. The two `@FunctionalInterface` visitors are not a framework — they are the allocation-free
shape the low-latency iteration requirement demands.

## Complexity

The five core operations the brief names explicitly. P = active price levels, O = total orders,
M = orders at the target level, D = levels dropped by a trim, R = orders evicted.

| Core op | Array (primary) | TreeMap (baseline) |
|---|---|---|
| **update** (qty) | **O(1)** — node holds its level reference | O(log P) — re-locates the level by price |
| **get-by-level(k)** | **O(1)** locate + O(M) materialize | O(log P + k) locate + O(M) materialize |
| **get-by-price** | O(log P) locate + O(M) materialize | O(log P) locate + O(M) materialize |
| **iteration** (whole book) | **O(P + O)**, allocation-free | O(P + O), one native iterator |
| **trim(t)** | O(D + R) | O(D + R) |

*Location vs materialization:* a snapshot of one level always copies its M orders — O(M) on both — so
the array's read advantage is purely in **locating** the level (O(1) index vs O(log P + k) tree walk),
not in building the result. The allocation-free `forEachLevel` / `forEachOrder` visitors skip
materialization entirely; that is what the iteration row measures.

Other mutations (array primary): best/worst **O(1)**; add at existing level **O(log P)**; add new
level **O(P)** arraycopy shift; remove (level survives) **O(1)** avg; remove final order **O(P)**.
Space **O(P + O)**.

## Semantics

`update` changes quantity only. A **price change** is `remove` + `add` (it resets time priority, so
it is semantically a new order — made explicit rather than hidden). `qty == 0` on update removes;
`qty < 0` is rejected; duplicate id and non-positive add are rejected; unknown id on update/remove
throws `NoSuchElementException`. All inputs are validated before any mutation.

## Correctness strategy

Three layers, strongest last:

1. **Contract tests** — hand-written expectations run against **both** implementations
   (`OrderBookContractTest`, parametrized). This audits the oracle *independently* before it is
   trusted: a shared bug cannot pass a human-written `assertEquals`.
2. **Executable invariants** — `validateInvariants()` checks ~20 structural properties (bidirectional
   index↔book consistency, FIFO link integrity, `totalQty`/`orderCount` agreement, strict best-to-worst
   ordering, no empty levels, no cycles).
3. **Seeded differential test** — `DifferentialTest` applies identical random op sequences
   (add / update / update→0 / remove / trim) to the primary and the reference, comparing normalized
   snapshots **and** running invariants after every operation. The suite exercises
   **200 scenarios × 2000 ops = 400,000 operations**; failures are reproducible by seed.

`mvn test` runs all of the above (72 test cases).

## Benchmark

JMH 1.37, `SampleTime`, **3×1s warmup + 5×1s measurement**. `@Fork(1)` in annotations, overridden to
in-process **`forks(0)`** by `BenchmarkRunner` (see caveat). Book = 100 levels × 10 orders (ask side).
Environment: **Apple M5, macOS 26.5.1, OpenJDK 21.0.11** (absolute ns shift with JDK/hardware — the
*ratios* are the signal). Read benchmarks accumulate primitives (no snapshot allocation); iteration
benchmarks use the allocation-free `forEachLevel` / `forEachOrder` visitor. Separate `@Benchmark`
methods per implementation keep interface dispatch out of the measured path.

| Operation | Array (mean) | TreeMap (mean) | Note |
|---|---:|---:|---|
| **iterate levels best→worst** (×100) | **29.1 ns** | 259.4 ns | array ~8.9×: sequential reference walk vs tree pointer-chasing |
| **iterate all orders** (full L3, ×1000) | **555 ns** | 2495 ns | array ~4.5×: intrusive-link walk vs per-level iterators |
| repeated get-by-index (×10, *not* iteration) | **12.4 ns** | 90.0 ns | array O(1)/call; tree re-walks from front + allocates an iterator each call |
| update qty | **14.7 ns** | 26.7 ns | array ~1.8×: node→level direct; tree re-locates level by price (O(log P)) |
| add + remove a middle level | **48.8 ns** | 72.2 ns | array faster even on its *theoretical* weak spot |
| getByLevel(5), single call | 16.7 ns | 16.7 ns | dead heat — see below |

**Honest reading of the numbers:**
- **True iteration validates the locality claim** — ~8.9× on levels, ~4.5× on the full order walk.
  The array is a sequential reference/link walk; the TreeMap chases node pointers across the heap even
  with a native iterator.
- **An earlier version of this benchmark mislabeled "repeated get-by-index" as "iteration."** Calling
  `getByLevel(i)` in a loop makes the TreeMap re-walk from the front *and allocate a fresh iterator*
  every call — conflating two different costs. They are now separate rows: repeated point-access
  (~7.3×) and true single-pass iteration (~8.9×).
- The **`getByLevel(5)` single-call dead heat is a measurement floor**, not a refutation: one ~17 ns op
  sits on `SampleTime`'s timer resolution. The O(1)-vs-O(log P + k) gap only surfaces once enough calls
  aggregate — which the repeated-index and iterate rows show.
- **update** is array-direct (the intrusive node reaches its level); the TreeMap re-locates the level by
  price, an extra O(log P).
- **adding a new middle level** — the array's theoretical weak spot (it shifts ~100 references) — is
  still faster: `arraycopy` of references beats a tree node allocation + rebalance *at this depth*.
  Do not generalize that past shallow books.

**Caveats.** Benchmarks run **in-process** (`forks(0)`): under `mvn exec:java` a forked JMH VM inherits
Maven's classpath, not the project's, so a fresh fork can't find the benchmark classes. In-process
runs are less isolated, so these are **relative** comparisons under one controlled workload, **not**
production latency guarantees. Hardware, JVM config, and workload shape all matter.

## Design goals & non-goals

**Goals:** correctness, determinism, predictable/bounded work, cache-conscious layout, clarity.

**Non-goals:** matching engine, networking, persistence, multi-instrument. **Single-threaded,
thread-confined** — no locks or concurrent collections (one logical mutation spans the index, the
level array, intrusive links, and aggregates; only single-writer confinement keeps that atomic). A
production deployment would feed mutations through an SPSC/MPSC queue to one book-owner thread.

## Potential improvements — and why they're out of scope

Ordered roughly by how much each would help a real deployment. None are built: each trades real
complexity for a win this single-threaded data-structure exercise doesn't ask for.

- **Tick-indexed price ladder** (slot = tick − base) + occupancy bitmap → **O(1) exact-price access**.
  The next specialization *once an instrument's tick domain and operating range are known*. Left out
  because those bounds were unspecified — and a flat tick array degrades badly under price drift (it
  scans empty slots), so it is a specialization, not a safe default.
- **Garbage-free hot path** — object-pool the `OrderNode`s and use a **primitive open-addressed
  `long → node` index** to drop `Long` boxing and `HashMap.Entry` allocation. The real production lever
  (no GC pauses on the critical path), but it turns an order-book exercise into a memory-management
  exercise; deferred deliberately.
- **Off-heap storage** (e.g. Agrona `DirectBuffer`) + a **single-writer pipeline** (SPSC/MPSC queue →
  one book-owner thread, LMAX-Disruptor style) — how this would actually run in production. Out of
  scope: the brief is explicitly single-threaded, and these are deployment concerns, not
  data-structure ones.
- **Destructive `trim` caveat** — after a destructive trim the instance is a depth-limited local book;
  later events referring to a trimmed order must follow the unknown-id policy or trigger a snapshot
  refresh.
- **Venue replace/priority semantics** belong in a feed-normalization layer that maps them to
  remove + add; not modeled here.
