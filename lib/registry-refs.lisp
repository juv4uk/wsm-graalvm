;; Посилання на authority замість копій; paths + контрольна сума з
;; scripts/sync-authority.sh危机. Споживач під sparse-checkout бачить лише
;; чотири authority файла — читання, а не дублювання семантики.
(
 (registry     . "external/my-lisp/lib/surface/semantic-registry.lisp")
 (canon        . "external/my-lisp/lib/canon.lisp")
 (conformance  . "external/my-lisp/tests/fixtures/conformance.lisp")
 (constitution . "external/my-lisp/my-lisp-constitution.lisp")
)
