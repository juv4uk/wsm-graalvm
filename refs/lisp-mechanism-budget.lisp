;; GraalVM host mechanism budget against pinned my-lisp authority.
;; Classifications are evidence pointers, not semantic definitions.
(
  (schema . 1)
  (max-lisp-defined-java-debt . 0)

  (mechanism "00000010" substrate-required
    (meaning-source . "lib/canon.lisp")
    (evidence . "contracts/answer-contract.lisp"))
  (mechanism "00000011" substrate-required
    (meaning-source . "lib/canon.lisp")
    (evidence . "contracts/answer-contract.lisp"))
  (mechanism "00000100" substrate-required
    (meaning-source . "lib/canon.lisp"))
  (mechanism "00000101" substrate-required
    (meaning-source . "lib/canon.lisp"))
  (mechanism "00000110" substrate-required
    (meaning-source . "lib/canon.lisp"))

  (mechanism "00001101" substrate-required
    (meaning-source . "tests/fixtures/conformance.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp")
    (evidence . "tests/fixtures/conformance.lisp")
    (note . "Exact-rational subtraction/negation is a numeric substrate mechanism; Lisp-owned S1 fixtures define the observable results."))

  (mechanism "00011010" substrate-required
    (meaning-source . "contracts/exact-q-binary-contract.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp"))
  (mechanism "00011011" substrate-required
    (meaning-source . "contracts/exact-q-binary-contract.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp"))
  (mechanism "00011100" substrate-required
    (meaning-source . "contracts/exact-q-binary-contract.lisp")
    (evidence . "contracts/structural-query-inventory.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp"))

  (retired "1017" lisp-defined
    (meaning-source . "lib/core.lisp")
    (evidence . "contracts/exact-q-binary-contract.lisp")
    (note . "removed from Java table on main before this ledger landed"))

  (retired "1022" lisp-defined
    (meaning-source . "lib/core.lisp")
    (evidence . "contracts/structural-query-inventory.lisp")
    (note . "Java mechanism retired after current-main Lisp-owned equal? witness"))

  (mechanism "00111010" substrate-required
    (meaning-source . "lib/core.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp")
    (evidence . "NumericHeadRouteContract: exact 00111010 head routes; decimal 58/1043 do not become SID")
    (note . "string-append is exercised by Lisp-owned gensym; Java supplies only the irreducible string concatenation mechanism"))

  (mechanism "01000011" substrate-required
    (identity-source . "lib/surface/semantic-registry.lisp"))

  (mechanism "01001100" substrate-required
    (identity-source . "lib/surface/semantic-registry.lisp")
    (law-source . "tests/fixtures/conformance.lisp"))

  (rule . "No new Java mechanism ID may appear without classification here. Lisp-defined Java debt may only shrink.")
)
