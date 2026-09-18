;; GraalVM consumer manifest for the live my-lisp Lisp authority.
;;
;; This declares the current M0/M1 runtime slice. It is not a copy of Lisp
;; semantics. Semantic identity, laws, and source meaning remain upstream.
;;
;; Transport is deliberately NOT a Git submodule: wsm-graalvm consumes a
;; sibling my-lisp working tree (default ../my-lisp; WSM_LISP_HOME may override).

(
  (schema . 2)
  (upstream . "juv4uk/my-lisp")
  (authority-mode . "sibling-worktree")
  (default-root . "../my-lisp")

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

  (not-bootstrap-by-default
    ("lib/meta-eval.lisp" . "explicit later self-hosting witness")
    ("lib/machine/**" . "machine-specific witness/evidence")
    ("lib/generated/**" . "generated projection; never semantic authority")
    ("evidence/**" . "evidence artifacts")
    ("docs/research/**" . "research material")
    ("prototype/**" . "experimental material"))

  (rule
    . "A new bootstrap source requires a source-confirmed dependency edge plus a witness that needs it. Directory presence alone is not admission."))
