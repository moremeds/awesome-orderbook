# AI Usage

AI-assisted tooling was used to:

- enumerate alternative data structures and challenge the default `TreeMap` reflex;
- pressure-test complexity claims — e.g. distinguishing level *location* from order
  *materialization*, and correcting `getByLevel(k)` on a `TreeMap` to **O(log P + k)** rather than O(k);
- keep the cache-locality argument framed as a *measured hypothesis*, not an asserted constant;
- generate adversarial edge cases and review invariant / benchmark coverage;
- flag over-engineering and trim scope.

AI output was treated as untrusted. Material claims were accepted only after validation through
executable unit + contract tests, a seeded differential test against an independent reference
implementation, executable invariants, source inspection, and reproducible JMH benchmarks.

Suggestions rejected on engineering judgment:

- concurrent collections — the book is intentionally single-threaded / thread-confined;
- a full tick-indexed ladder — the instrument's tick domain and operating range were unspecified;
- a custom primitive hash map — added correctness risk without being central to the exercise;
- a third `TreeMap` + intrusive-list benchmark variant — research-useful, but not load-bearing here;
- framework-heavy structure (DI, services) — clarity and hot-path simplicity matter more.
