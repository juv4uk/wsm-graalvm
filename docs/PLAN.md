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

## M1 — dependency closure (started)
- `refs/lisp-dependency-manifest.lisp` records the pinned upstream commit and the current M0/M1 bootstrap slice.
- `scripts/check-lisp-dependency-manifest.sh` verifies the manifest pin equals the submodule gitlink and all required Lisp sources are present.
- Runtime bootstrap is explicit: `canon.lisp`, `macro.lisp`, and `core.lisp` are the current language-owned bootstrap slice; other libraries are not pulled in by directory scanning.
- Full-tree classification remains open in issue #30; upstream `scripts/build-dependency-classification.lisp` is the source-confirmed fixture dependency evidence and is not copied into this repository.

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
