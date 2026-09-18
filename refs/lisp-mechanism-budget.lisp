;; GraalVM host mechanism budget against pinned my-lisp authority.
;; Classifications are evidence pointers, not semantic definitions.
(
  (schema . 1)
  (max-lisp-defined-java-debt . 1)

  (mechanism "0002" substrate-required
    (meaning-source . "lib/canon.lisp")
    (evidence . "contracts/answer-contract.lisp"))
  (mechanism "0003" substrate-required
    (meaning-source . "lib/canon.lisp")
    (evidence . "contracts/answer-contract.lisp"))
  (mechanism "0004" substrate-required
    (meaning-source . "lib/canon.lisp"))
  (mechanism "0005" substrate-required
    (meaning-source . "lib/canon.lisp"))
  (mechanism "0006" substrate-required
    (meaning-source . "lib/canon.lisp"))

  (mechanism "1014" substrate-required
    (meaning-source . "contracts/exact-q-binary-contract.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp"))
  (mechanism "1015" substrate-required
    (meaning-source . "contracts/exact-q-binary-contract.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp"))
  (mechanism "1016" substrate-required
    (meaning-source . "contracts/exact-q-binary-contract.lisp")
    (evidence . "contracts/structural-query-inventory.lisp")
    (identity-source . "lib/surface/semantic-registry.lisp"))

  (retired "1017" lisp-defined
    (meaning-source . "lib/core.lisp")
    (evidence . "contracts/exact-q-binary-contract.lisp")
    (note . "removed from Java table on main before this ledger landed"))

  (mechanism "1022" lisp-defined-retire-java
    (meaning-source . "lib/core.lisp")
    (evidence . "contracts/structural-query-inventory.lisp"))

  (mechanism "1043" substrate-required
    (meaning-source . "lib/core.lisp string-append peer derivation")
    (identity-source . "lib/surface/semantic-registry.lisp (1043 stable)")
    (evidence . "NumericHeadRouteContract: 1043 head routes to admitted mechanism"))

  (mechanism "1052" substrate-required
    (identity-source . "lib/surface/semantic-registry.lisp"))

  (mechanism "1061" substrate-required
    (identity-source . "lib/surface/semantic-registry.lisp")
    (law-source . "tests/fixtures/conformance.lisp"))

  (rule . "No new Java mechanism ID may appear without classification here. Lisp-defined Java debt may only shrink.")
)
