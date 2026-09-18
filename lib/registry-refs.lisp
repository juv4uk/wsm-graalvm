;; Посилання на authority замість копій; paths + контрольна сума з
;; scripts/sync-authority.sh危机. Споживач під sparse-checkout бачить лише
;; чотири authority файла — читання, а не дублювання семантики.
(
 (registry     . "external/my-lisp/lib/surface/semantic-registry.lisp")
 (canon        . "external/my-lisp/lib/canon.lisp")
 (conformance  . "external/my-lisp/tests/fixtures/conformance.lisp")
 (constitution . "external/my-lisp/my-lisp-constitution.lisp")
)
;; Pinned submodule state (issue #1: dependency pin, not a semantic fork):
;;   my-lisp HEAD  = 9ce5101853c0a9f43aacb32a98bb2bc9ab2eec3b (2026-09-18)
;;   lib/canon.lisp  9b7b1086...
;;   tests/fixtures/conformance.lisp e0b50161...
