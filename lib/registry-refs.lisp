;; Посилання на authority замість копій (owner rule: consumer, not a mirror).
;; Sparse scope = Чотири Lisp-зоны, як у структуре my-lisp:
;;   /lib/ /tests/ /contracts/ /knowledge/ + два кореневые contract-файла.
;; Дайджести з scripts/sync-authority.sh; пін-шах модулю:
;;   my-lisp @ 9ce5101853c0a9f43aacb32a98bb2bc9ab2eec3b (2026-09-18)
(
 (registry     . "external/my-lisp/lib/surface/semantic-registry.lisp")
 (canon        . "external/my-lisp/lib/canon.lisp")
 (core         . "external/my-lisp/lib/core.lisp")
 (macro        . "external/my-lisp/lib/macro.lisp")
 (meta-eval    . "external/my-lisp/lib/meta-eval.lisp")
 (authority-boundary . "external/my-lisp/lib/machine/authority-boundary.lisp")
 (conformance  . "external/my-lisp/tests/fixtures/conformance.lisp")
 (constitution . "external/my-lisp/my-lisp-constitution.lisp")
 (contract     . "external/my-lisp/language-contract.lisp")
 (contracts-dir . "external/my-lisp/contracts/")
 (knowledge-dir . "external/my-lisp/knowledge/")
)
