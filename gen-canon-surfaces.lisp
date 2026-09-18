;; gen-canon-surfaces.lisp — run in my-lisp from repo root of wsm-graalvm.
;; Reads the real registry (my-lisp authority), picks Canon rows 0001..0007,
;; emits "id.kind=...,id.surfaces=..." for the Java reader. No duplication
;; of meaning: this is a projection, not a second authority.
(load-file-if-canonical "../my-lisp/lib/surface/semantic-registry.lisp")
