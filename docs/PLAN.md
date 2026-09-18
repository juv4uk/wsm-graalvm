# План вертикали Truffle-субстрату (M0–M3)

Дискусія з власником 2026-09-18 (anviksiki session, desktop).

## M0 — вертикальний зріз (мінімальний рантайм)
- Reader: s-вирази; апостроф внутрішньо-ідентифікаторна (об'єкт, п'ять,
  зв'язок — не розщеплювати); десяткова кома (`12,455` = `12.455`);
  символи UK/SA поверхонь
- Eval: Canon 0+7 + lambda/define/defmacro
- Canon-резолюція за числовими IDs (0001–0007) ДО лексичного пошуку;
  reserved-spelling binder → InvalidForm
- ErrorKind → RuntimeException-ієрархія (Parse/Arity/InvalidForm/Type/...)
- **Критерій M0:** локально прочитать `lib/canon.lisp`, обчислити
  `(canon-conforms?)` → `t`

## М1 — самозастосування
- Читати `tests/fixtures/conformance.lisp` власним reader-ом, проганяти
  власним eval-ом; порівняння з Rust-оракулом як Lisp-дані
- Об'єм орієнтовний: reader ~300–500 Java, ~20 AST-класів,
  environment+Canon ~150, LoopNode ~100, errors ~80 (≈1.5–2k рядків)

## M1 — dependency closure
- `refs/lisp-dependency-manifest.lisp` records the pinned upstream commit and the intentionally admitted M0/M1 Lisp source slice.
- `scripts/check-lisp-dependency-manifest.sh` fails closed when the manifest pin diverges from the submodule gitlink or required Lisp sources are missing.
- The dependency manifest is orthogonal to the semantic gates already on `main`: it does not copy semantic source and does not replace the Lisp-owned registry.
- Full-tree classification remains tracked separately in issue #30; upstream `scripts/build-dependency-classification.lisp` is the source-confirmed fixture dependency analyzer.

## М2 — семантичні рішення субстрату (до/під час вузлів)
- TCO: LoopNode у lambda-позиції vs trampoline — ADR
- Пара: Java-object vs flat-array — ADR з ланцюгом representation

## М3 — registry consumption + (опційно) polyglot
- Генератор розпізнавання виконується У my-lisp і читається звідси
  (не дуплікація таблиць)
- Polyglot-interop — опція, не контракт

## Демаркація мова ↔ субстрат
- **Мова** (інваріант): reader/eval/Canon/IDs, exactness S1–S3, ErrorKind
- **Субстрат** (цього репо): JVM memory/GC/threading, відсутність TCO
  в ABI → LoopNode-семантика
