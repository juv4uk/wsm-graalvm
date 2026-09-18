;; Посилання на authority замість копій (owner rule: consumer, not a mirror).
;; "Same structure" через symlink-папки в корені: lib, tests, contracts,
;; knowledge -> external/my-lisp/<...>. Список шляхів тут - метадані
;; конформності; симлінки не дублюють семантику.
;; Sparse scope = Чотири Lisp-зоны, як у структуре my-lisp:
;;   /lib/ /tests/ /contracts/ /knowledge/ + два кореневые contract-файла.
;; Дайджести з scripts/sync-authority.sh; пін-шах модулю:
;;   my-lisp @ 9ce5101853c0a9f43aacb32a98bb2bc9ab2eec3b (2026-09-18)
(
 (registry     . "lib/surface/semantic-registry.lisp")
 (canon        . "lib/canon.lisp")
 (core         . "lib/core.lisp")
 (macro        . "lib/macro.lisp")
 (meta-eval    . "lib/meta-eval.lisp")
 (authority-boundary . "lib/machine/authority-boundary.lisp")
 (conformance  . "tests/fixtures/conformance.lisp")
 (constitution . "my-lisp-constitution.lisp")
 (contract     . "language-contract.lisp")
 (contracts-dir . "contracts/")
 (knowledge-dir . "knowledge/")
)
