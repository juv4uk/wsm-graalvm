;; wsm-graalvm task registry (swarm-node --auto-sync format)
;; Division of work: architecture/issues + SemanticRef(ID) contract = owner;
;; code/toolchain/runs = local agent. Gates order per issue #9.
(
 ("GRAALVM-M0-READER-SEVEN-PRIMITIVES" .
  ((priority . 9.5) (capabilities . (java truffle reader lisp canon)) (origin . my-lisp)
   (done . (t . "loc-anviksiki-1 2026-09-18, gen 1: (canon-conformance satisfied) produced from real pinned my-lisp/lib/canon.lisp on GraalVM CE 25.3.4.1 by the own reader/eval; commit f024247. Truffle-interpreter-only, registry consumed by numeric IDs, zero hardcoded spellings."))))

 ("GRAALVM-M1-LEXICAL-FRAME-WITNESSES" .
  ((priority . 9.2) (capabilities . (java truffle lexical closure frames tests)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (issue . 6)
   (description . "Exercise current Truffle lexical frames with nested capture, shadowing, and multi-level capture; CI gate uses the real pinned registry and runtime path. Acceptance: 42, 7, 42 witnesses and no compile-time Environment value capture.")))

 ;; issue #11 Phase B (gates 6+): goal adjunct is the current fixture expectation
 ("GRAALVM-M1-CONFORMANCE-TIER1" .
  ((priority . 9.0) (capabilities . (truffle conformance lisp fixtures java)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (description . "Run pinned external/my-lisp/tests/fixtures/conformance.lisp tier-1 fixtures through the substrate's own reader+eval. Acceptance: every tier-1 (expr expected) pair either agrees or names the exact divergent fixture with kind; no silent pass.")))

 ("GRAALVM-M1-ERRORKIND-PARITY" .
  ((priority . 8.8) (capabilities . (java truffle lisp errors contract)) (origin . my-lisp)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (description . "Match my-lisp ErrorKind vocabulary exactly (Parse/Arity/InvalidForm/Type/DivisionByZero/NumericOverflow/UnknownSymbol...) against the current contract; each throwing path in Java must cite its ErrorKind source location in my-lisp crates.")))

 ("GRAALVM-M2-TCO-DECISION-ADR" .
  ((priority . 9.0) (capabilities . (truffle jvm tco lisp semantics adr)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Decide tail calls: Truffle LoopNode in lambda tail position vs trampoline. Semantics, not optimization. ADR must cite current my-lisp tail-position contract and measured behavior; recorded BEFORE node redesign. Owner is design authority; local agent implements after sign-off.")))

 ("GRAALVM-M3-PAIR-REPRESENTATION-ADR" .
  ((priority . 8.8) (capabilities . (jvm memory lisp truffle layout adr)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Decide pair layout: 16-byte Java object (Rc port) vs flat long-indexed arena (x86-pair-layout + mccarthy-eval precedent). ADR = type -> fields -> bytes -> GC cost chain, measured not asserted. Owner design authority."))))
