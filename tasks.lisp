;; wsm-graalvm task registry (swarm-node --auto-sync format)
;; Division of work: architecture/issues + SemanticRef(ID) contract = owner;
;; code/toolchain/runs = local agent. Gates order per issue #9.
(
 ("GRAALVM-M0-READER-SEVEN-PRIMITIVES" .
  ((priority . 9.5) (capabilities . (java truffle reader lisp canon)) (origin . my-lisp)
   (done . (t . "loc-anviksiki-1 2026-09-18, gen 1: (canon-conformance satisfied) produced from real pinned my-lisp/lib/canon.lisp on GraalVM CE 25.3.4.1 by the own reader/eval; commit f024247. Truffle-interpreter-only, registry consumed by numeric IDs, zero hardcoded spellings."))))

 ("GRAALVM-M1-CONTEXT-OWNED-RUNTIME-STATE" .
  ((priority . 9.2) (capabilities . (java truffle context frames lisp)) (origin . my-lisp)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (issue . 26)
   (coordination . ((pr . 29) (current-base . "main@345d8cb") (preserve-alongside . (#35 #39 #40))))
   (description . "One Truffle language context owns one persistent WSM registry + shared top-level definition frame. Sequential Context.eval calls must observe the same definitions; independent contexts must not.")))

 ("GRAALVM-M1-CONFORMANCE-TIER1" .
  ((priority . 9.0) (capabilities . (truffle conformance lisp fixtures java)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M1-CONTEXT-OWNED-RUNTIME-STATE))
   (issue . 27)
   (coordination . ((inventory . "agent-#36/8e88a477") (error-parity . "agent-#39/d581143d")))
   (description . "Run pinned external/my-lisp/tests/fixtures/conformance.lisp tier-1 fixtures through the substrate's own reader+eval. Report each fixture and fail closed on any expected-value or ErrorKind divergence.")))

 ("GRAALVM-M1-ERRORKIND-PARITY" .
  ((priority . 8.8) (capabilities . (java truffle lisp errors contract)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Match my-lisp ErrorKind vocabulary exactly (Parse/Arity/InvalidForm/Type/DivisionByZero/NumericOverflow/UnknownSymbol/OutOfMemory) and trace every throwing path to its upstream contract source.")))

 ("GRAALVM-M1-NATIVE-IMAGE-WITNESS" .
  ((priority . 8.7) (capabilities . (graalvm native-image truffle polyglot lisp)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (issue . 28)
   (coordination . ((dependency-closure . "agent-#40/2224d2c") (native-stack . "local")))
   (description . "Build a native executable containing the WSM Truffle provider, run the real external/my-lisp/lib/canon.lisp witness, and fail if language discovery or Canon conformance diverges.")))

 ("GRAALVM-M2-TCO-DECISION-ADR" .
  ((priority . 9.0) (capabilities . (truffle jvm tco lisp semantics adr)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Decide tail calls: Truffle LoopNode in lambda tail position vs trampoline. Semantics, not optimization. ADR must cite current my-lisp tail-position contract and measured behavior; recorded BEFORE node redesign. Owner is design authority; local agent implements after sign-off.")))

 ("GRAALVM-M3-PAIR-REPRESENTATION-ADR" .
  ((priority . 8.8) (capabilities . (jvm memory lisp truffle layout adr)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Decide pair layout: 16-byte Java object (Rc port) vs flat long-indexed arena (x86-pair-layout + mccarthy-eval precedent). ADR = type -> fields -> bytes -> GC cost chain, measured not asserted."))))
