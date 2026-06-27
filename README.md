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

## Complexity (work profile, not Big-O alone)

Level *location* vs result *materialization* (a level holds M orders):

| Op | Array (primary) | TreeMap (baseline) |
|---|---|---|
| locate level k | **O(1)** direct index | **O(log P + k)** find first, advance k |
| getByLevel(k) full result | **O(M)** | O(log P + k + M) |
| getByPrice locate / full | O(log P) / O(log P + M) | O(log P) / O(log P + M) |

Materializing M orders is O(M) on both — the array's advantage is purely *location*.

Mutations (array primary): best/worst **O(1)**; add at existing level **O(log P)**; add new level
**O(P)** shift; update qty **O(1)** avg; remove (level survives) **O(1)** avg; remove final order
**O(P)**; trim **O(D+R)**; full iteration **O(P+O)**. Space **O(P+O)**.

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

`mvn test` runs all of the above (66 test cases).

## Benchmark

JMH 1.37, `SampleTime`, 2×1s warmup + 5×1s measurement. Book = 100 levels × 10 orders (ask side).
Environment: **Apple M5, macOS 26.5.1, OpenJDK 21.0.11**. Read benchmarks use a primitive
`priceAtLevel` accessor so they measure traversal, not snapshot allocation. Separate `@Benchmark`
methods per implementation keep interface dispatch out of the measured path.

| Operation | Array (mean) | TreeMap (mean) | Note |
|---|---:|---:|---|
| iterate top 10 levels | **14.1 ns** | 84.4 ns | array ~6×: contiguous reads vs no random access |
| update qty | **14.8 ns** | 32.5 ns | array ~2.2×: node holds its level; tree does HashMap + O(log P) get |
| add + remove a middle level | **50.7 ns** | 75.7 ns | array faster even on its *theoretical* weak spot |
| getByLevel(5) | 18.0 ns | 17.6 ns | dead heat — see caveat |

**Honest reading of the numbers:**
- The array wins clearly where it should: **iteration** (no random access in a tree means each level
  step re-walks) and **update** (the intrusive node reaches its level directly).
- The **`getByLevel(5)` dead heat is a measurement artifact**, not a refutation. A single sub-10ns op
  is below `SampleTime`'s timer resolution (both implementations report p50 = 0 ns). The asymptotic
  O(1) vs O(log P + k) gap only becomes measurable *at scale* — which is exactly what the 10-call
  iterate benchmark exposes.
- Even **adding a new middle level** — the array's theoretical weak spot (it shifts ~100 references) —
  is faster here: `System.arraycopy` of references beats a tree node allocation + rebalance at this depth.

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

## Trade-offs & future work

- **TreeMap baseline kept on purpose** — it is the control group that makes the comparison honest.
- **Tick-indexed price ladder** (slot = tick − base) + occupancy bitmap would give O(1) exact-price
  access and is the next specialization once an instrument's tick domain and operating range are
  known. Not built here because those bounds were unspecified.
- **Primitive-key open-addressed order index** would remove `Long` boxing from the global index;
  deferred to avoid turning an order-book exercise into a hash-table exercise.
- **Destructive `trim` caveat** — after a destructive trim the instance is a depth-limited local book;
  later incremental events referring to a trimmed order must follow the unknown-id policy or trigger a
  snapshot refresh.
- **Venue replace/priority semantics** belong in a feed-normalization layer that maps them to
  remove + add; not modelled here.
