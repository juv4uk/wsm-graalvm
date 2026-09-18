;; wsm-graalvm task registry (swarm-node --auto-sync format)
;; Division of work: architecture/issues + SemanticRef(ID) contract = owner;
;; code/toolchain/runs = local agent. Gates order per issue #9.
(
 ("GRAALVM-M0-READER-SEVEN-PRIMITIVES" .
  ((priority . 9.5) (capabilities . (java truffle reader lisp canon)) (origin . my-lisp)
   (done . (t . "loc-anviksiki-1 2026-09-18, gen 1: (canon-conformance satisfied) produced from real pinned my-lisp/lib/canon.lisp on GraalVM CE 25.3.4.1 by the own reader/eval; commit f024247. Truffle-interpreter-only, registry consumed by numeric IDs, zero hardcoded spellings."))))

 ("GRAALVM-M1-DEPENDENCY-MANIFEST" .
  ((priority . 9.1) (capabilities . (lisp dependency graph manifest ci)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (issue . 30)
   (description . "Declare and fail-closed-check the pinned my-lisp M0/M1 bootstrap closure without copying semantic source. Full-tree classification remains a separate follow-up.")))

 ("GRAALVM-M1-DEPENDENCY-CLASSIFICATION-EVIDENCE" .
  ((priority . 9.0) (capabilities . (lisp dependency graph evidence ci)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-DEPENDENCY-MANIFEST))
   (issue . 30)
   (upstream-generator . "juv4uk/my-lisp@scripts/build-dependency-classification.lisp")
   (description . "Record the source-confirmed pinned dependency-classification artifact as downstream evidence. This does not replace the eventual full reachable *.lisp file classification or introduce a second semantic authority.")))

 ("GRAALVM-M1-NUMERIC-SEMANTIC-ID-HEAD-RULE" .
  ((priority . 9.4) (capabilities . (lisp semantic-id reader compiler coordination)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (issue . 47)
   (upstream-issue . "juv4uk/my-lisp#611")
   (description . "Do not invent a downstream numeric-ID-vs-integer rule. Consume the Lisp-owned rule for executable numeric semantic IDs above 0999 once ratified; until then keep ordinary exact integer data semantics unchanged.")))

 ("GRAALVM-M1-CONTEXT-OWNED-RUNTIME-STATE" .
  ((priority . 9.2) (capabilities . (java truffle context frames lisp)) (origin . my-lisp)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (issue . 26)
   (description . "One Truffle language context owns one persistent WSM registry + shared top-level definition frame. Sequential Context.eval calls must observe the same definitions; independent contexts must not.")))

 ("GRAALVM-M1-CONFORMANCE-TIER1" .
  ((priority . 9.0) (capabilities . (truffle conformance lisp fixtures java)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M1-CONTEXT-OWNED-RUNTIME-STATE))
   (issue . 27)
   (description . "Run pinned external/my-lisp/tests/fixtures/conformance.lisp tier-1 fixtures through the substrate's own reader+eval. Report each fixture and fail closed on any expected-value or ErrorKind divergence.")))

 ("GRAALVM-M1-ERRORKIND-PARITY" .
  ((priority . 8.8) (capabilities . (java truffle lisp errors contract)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Match my-lisp ErrorKind vocabulary exactly (Parse/Arity/InvalidForm/Type/DivisionByZero/NumericOverflow/UnknownSymbol/OutOfMemory) and trace every throwing path to its upstream contract source.")))

 ("GRAALVM-M1-NATIVE-IMAGE-WITNESS" .
  ((priority . 8.7) (capabilities . (graalvm native-image truffle polyglot lisp)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M0-READER-SEVEN-PRIMITIVES))
   (issue . 28)
   (description . "Build a native executable containing the WSM Truffle provider, run the real external/my-lisp/lib/canon.lisp witness, and fail if language discovery or Canon conformance diverges.")))

 ("GRAALVM-M1-LET-LEXICAL-CUTOVER" .
  ((priority . 10.0) (capabilities . (graalvm truffle compiler frames macro-bootstrap)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M1-NATIVE-IMAGE-WITNESS))
   (issue . 160)
   (done . (t . "current-main M0 run 35368800960: real Lisp bootstrap cutover passed, including source-level let witness; resolved without Java let semantics."))
   (description . "Resolve source-level Lisp-owned let expansion losing lexical scope on Graal while the raw expansion witness is green. Fix only compiler/Truffle frame plumbing; no Java let semantics.")))

 ("GRAALVM-P0-RELEASE-ARTIFACT-CONTRACT" .
  ((priority . 10.0) (capabilities . (release packaging provenance windows deb rpm semver)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-M1-LET-LEXICAL-CUTOVER))
   (issue . 161)
   (description . "Define one fail-closed v0.1.x artifact contract: native runtime, canonical launcher, exact pinned my-lisp authority slice, release metadata, checksums/provenance, FHS layout, and install lifecycle.")))

 ("GRAALVM-P0-WINDOWS-INSTALLER" .
  ((priority . 9.9) (capabilities . (windows native installer msi provenance)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-P0-RELEASE-ARTIFACT-CONTRACT))
   (issue . 162)
   (description . "Produce the Windows x86_64 installable package around the native executable and canonical launcher, with clean install/upgrade/uninstall smoke coverage.")))

 ("GRAALVM-P0-DEB-PACKAGE" .
  ((priority . 9.8) (capabilities . (linux deb dpkg FHS packaging)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-P0-RELEASE-ARTIFACT-CONTRACT))
   (issue . 163)
   (description . "Produce the Debian/Ubuntu x86_64 package from the same native release payload; validate install/run/upgrade/remove and provenance.")))

 ("GRAALVM-P0-RPM-PACKAGE" .
  ((priority . 9.8) (capabilities . (linux rpm rpmbuild FHS packaging)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-P0-RELEASE-ARTIFACT-CONTRACT))
   (issue . 164)
   (description . "Produce the RPM-family x86_64 package from the same native release payload; validate install/run/upgrade/erase and provenance.")))

 ("GRAALVM-P0-CROSS-PLATFORM-RELEASE" .
  ((priority . 10.0) (capabilities . (release ci windows deb rpm linux native-image atomic-publish)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-P0-WINDOWS-INSTALLER GRAALVM-P0-DEB-PACKAGE GRAALVM-P0-RPM-PACKAGE))
   (issue . 165)
   (description . "Unify Windows + DEB + RPM + portable release production from one immutable source revision, with package-level smoke tests, checksums, provenance/SBOM and atomic publication.")))

 ("GRAALVM-P1-PACKAGE-REPOSITORIES" .
  ((priority . 7.0) (capabilities . (apt rpm repository signing key-management upgrades rollback)) (origin . wsm-graalvm)
   (depends-on . (GRAALVM-P0-CROSS-PLATFORM-RELEASE))
   (issue . 166)
   (description . "Post-v0.1.x distribution repositories for signed APT/RPM metadata, key rotation/revocation, upgrade policy, retention and rollback. Must not block first package artifacts.")))

 ("GRAALVM-M2-TCO-DECISION-ADR" .
  ((priority . 9.0) (capabilities . (truffle jvm tco lisp semantics adr)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Decide tail calls: Truffle LoopNode in lambda tail position vs trampoline. Semantics, not optimization. ADR must cite current my-lisp tail-position contract and measured behavior; recorded BEFORE node redesign. Owner is design authority; local agent implements after sign-off.")))

 ("GRAALVM-M3-PAIR-REPRESENTATION-ADR" .
  ((priority . 8.8) (capabilities . (jvm memory lisp truffle layout adr)) (origin . my-lisp)
   (depends-on . (GRAALVM-M1-CONFORMANCE-TIER1))
   (description . "Decide pair layout: 16-byte Java object (Rc port) vs flat long-indexed arena (x86-pair-layout + mccarthy-eval precedent). ADR = type -> fields -> bytes -> GC cost chain, measured not asserted."))))
