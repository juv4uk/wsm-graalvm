;; Source mirror manifest for the canonical-CON D migration lane.
;; Paths below are relative to mirror/my-lisp and must exist at the same
;; relative path in the pinned upstream my-lisp checkout.

(mylisp-mirror/1
  (upstream . "juv4uk/my-lisp")
  (pin . "9ce5101853c0a9f43aacb32a98bb2bc9ab2eec3b")
  (root . "mirror/my-lisp")
  (sources
    "lib/surface/semantic-registry.lisp"
    "lib/canon.lisp"
    "lib/macro.lisp"
    "lib/core.lisp"))
