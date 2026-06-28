# AI Usage

## Where AI helped

AI-assisted tooling was used to:

- enumerate alternative data structures and challenge the default `TreeMap` reflex;
- pressure-test complexity claims — e.g. distinguishing level *location* from order
  *materialization*, and correcting `getByLevel(k)` on a `TreeMap` to **O(log P + k)** rather than O(k);
- keep the cache-locality argument framed as a *measured hypothesis*, not an asserted constant;
- generate adversarial edge cases and review invariant / benchmark coverage;
- flag over-engineering and trim scope.

## How I directed AI

AI was my fast path for exploration, not the source of truth. I set the bar up front — a correctness
floor (a seeded differential test against an *independent* reference, plus executable invariants) and a
performance bar (reproducible, forked JMH) — and treated every AI output as untrusted: a claim shipped
only after it cleared that bar through executable tests, source inspection, or measurement, however
confident the suggestion sounded.

The loop in practice: I used AI to widen the option space (alternative structures, edge cases,
adversarial inputs), treated each suggestion as a hypothesis, confirmed or refuted it against the
evidence, and **overrode the AI when the two disagreed**. The mislabeled "iteration" benchmark in
*What I learnt* is the clearest instance — a plausible AI-assisted result that measured the wrong thing,
caught on an independent review pass and corrected rather than trusted.

Net: AI raised my speed of exploration; the standard of proof stayed mine.

## What I learnt

A few of my conclusions changed as I reviewed and measured this — the shifts in engineering
judgment, not data-structure trivia:

- **Big-O wasn't the whole objective function.** I started from asymptotic complexity, but for the
  shallow active depths this exercise illustrates, memory-access shape, allocation, and bounded
  structural work mattered just as much.
- **I had to separate lookup cost from result materialization.** The active-level array locates level
  k in O(1), but I still pay O(M) to produce a snapshot of its M orders — exactly as the `TreeMap`
  does. Conflating the two overstates the array's edge.
- **A benchmark can be valid and still answer the wrong question.** My first "iteration" benchmark
  repeatedly called `getByLevel(i)`, which restarted the `TreeMap` traversal and allocated a fresh
  iterator on every call. I split point access from true single-pass iteration before trusting any
  number.
- **Every performance claim needs a boundary.** My results hold for the documented JVM, hardware, and
  workload shape; a deeper or different book could move the crossover between bounded array shifts and
  tree mutation. I stopped saying "the array is faster" and started saying "faster for this measured
  shape."
- **An oracle earns trust only after an independent audit.** Before using the `TreeMap` as the
  differential-test reference, I checked it against hand-written contract tests — otherwise a bug it
  shared with the primary implementation would pass unnoticed.

## Suggestions rejected on engineering judgment

- concurrent collections — the book is intentionally single-threaded / thread-confined;
- a full tick-indexed ladder — the instrument's tick domain and operating range were unspecified;
- a custom primitive hash map — added correctness risk without being central to the exercise;
- a third `TreeMap` + intrusive-list benchmark variant — research-useful, but not load-bearing here;
- framework-heavy structure (DI, services) — clarity and hot-path simplicity matter more.
