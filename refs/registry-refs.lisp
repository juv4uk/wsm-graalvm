;; Посилання на upstream authority замість копій.
;;
;; Кореневі lib/, tests/, contracts/, knowledge/ та два top-level contract files
;; є symlink-ами на sibling ../my-lisp. WSM_LISP_HOME використовується shell/CI
;; resolver-ом, коли робочі дерева розташовані інакше.
;;
;; Semantic authority залишається в my-lisp; цей файл містить лише шляхи
;; до runtime witnesses через ці посилання.
(
 (dependency-manifest . "refs/lisp-dependency-manifest.lisp")
 (dependency-classification . "refs/upstream-dependency-classification.lisp")
 (registry     . "lib/surface/semantic-registry.lisp")
 (canon        . "lib/canon.lisp")
 (core         . "lib/core.lisp")
 (macro        . "lib/macro.lisp")
 (meta-eval    . "lib/meta-eval.lisp")
 (authority-boundary . "lib/machine/authority-boundary.lisp")
 (conformance  . "tests/fixtures/conformance.lisp")
 (constitution . "my-lisp-constitution.lisp")
 (contract     . "language-contract.lisp")
)
