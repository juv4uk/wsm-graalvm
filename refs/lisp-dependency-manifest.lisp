;; GraalVM consumer manifest for the pinned my-lisp Lisp authority.
;;
;; Authority pin at the M1 branch baseline:
;;   my-lisp @ 9ce5101853c0a9f43aacb32a98bb2bc9ab2eec3b
;;
;; This file is a dependency declaration, not a copy of Lisp semantics.
;; The semantic registry, Canon, contract, and conformance remain upstream.
;;
;; Classification is intentionally scoped:
;;   authority        = read/verify, never part of semantic implementation
;;   bootstrap        = needed to materialize the language-owned bootstrap
;;   witness          = executed evidence, not a runtime dependency
;;   optional-library = loadable language library, outside the closed core
;;   machine-specific = hardware/backend substrate witness
;;   research         = experimental/reference material
;;
;; The bootstrap closure currently implemented/planned by wsm-graalvm is:
;;   registry -> Canon substrate -> macro.lisp -> core.lisp
;; The metacircular evaluator and all other libraries are explicit later
;; surfaces; they must not be silently pulled into M0 by directory scanning.

(
  (schema . 1)
  (upstream . "juv4uk/my-lisp")
  (pin . "9ce5101853c0a9f43aacb32a98bb2bc9ab2eec3b")

  (authority
    ("language-contract.lisp")
    ("my-lisp-constitution.lisp")
    ("lib/surface/semantic-registry.lisp"))

  (bootstrap-required
    ("lib/canon.lisp" . (gate M0))
    ("lib/macro.lisp" . (gate M1))
    ("lib/core.lisp" . (gate M1)))

  (witness
    ("tests/fixtures/conformance.lisp" . (gate M1))
    ("lib/machine/authority-boundary.lisp" . (guard))
    ("knowledge/guard-reference.lisp" . (guard)))

  (optional-library
    ;; Classification comes from the upstream executable dependency
    ;; classifier rather than a hand-copied library-name table.
    ("lib/*.lisp" . (exclude
                      "lib/canon.lisp"
                      "lib/macro.lisp"
                      "lib/core.lisp"
                      "lib/machine/**")))

  (machine-specific
    ("lib/machine/**"))

  (research
    ("evidence/**")
    ("docs/research/**")
    ("prototype/**"))

  (generated-or-projection
    ("lib/generated/**"))

  (rule
    . "No file enters bootstrap merely because it exists under lib/. Add a file only after a source-confirmed dependency edge and a witness requiring it."))

