# wsm-graalvm

**WSM / my-lisp на GraalVM Truffle — субстратний свідок, не головний рантайм.**
**WSM / my-lisp on GraalVM Truffle — a substrate witness, not the primary runtime.**

---

## Роль цього репозиторію / Role

my-lisp — semantic authority екосистеми: він сам читає власний контракт
(`tests/fixtures/conformance.lisp`), конституцію
(`my-lisp-constitution.lisp`) і таблицю функцій
(`lib/surface/semantic-registry.lisp`) мовою, яку перевіряє. Цей репо —
**четвертий субстратний свідок** у вертикалі:

```
Rust (оракул-ядро)  →  Lisp-owned x86 (lib/machine/)  →  нативне виконання (W^X)
                                                     →  mccarthy-eval (asm 1960)
                                                     →  wsm-graalvm (Truffle/JVM)  ← тут
```

## Контракти, які цей субстрат мусить пройти

1. `(canon-conforms?)` → `t` — виконуваний контракт Канону 0+7
   (`my-lisp/lib/canon.lisp`)
2. Прогін `tests/fixtures/conformance.lisp` (tier 1+2) — fixtures читаються
   *власним* reader-ом цієї реалізації і виконуються *власним* eval-ом
3. Розпізнавання мовних ідентичностей — **лише числові semantic IDs** з
   `lib/surface/semantic-registry.lisp`; жодних hardcoded поверхневих
   спеллінгів (`my-lisp/docs/CANON-MIGRATION-PLAN-2026-09-11.md`)

## Відомі ризики субстрату (вирішити до М2)

- **JVM не має proper tail calls.** Хвостові виклики — `LoopNode` у
  lambda-позиції (Enso-підхід) або trampoline (як у Rust-оцінювачі).
  Це семантичне рішення, не оптимізація — і його треба зафіксувати
  до побудови вузлів.
- **Пара на JVM:** 16-байт об'єкт (прямий порт `Rc`) або плоский масив з
  індексами (прецеденти: `x86-pair-layout` у
  `my-lisp/lib/machine/layout/pair-x86-64.lisp`, `mccarthy-eval`).
  Вибір фіксувати в ADR з ланцюгом тип→fields→байти→GC-вартість.

## Що тут НЕ зберігається

- Копій semantic-registry, conformance, canon — тільки шляхи + дайджести
  у `lib/registry-refs.lisp`
- Рішень щодо мовної семантики — authority лишається в my-lisp

## Ліцензія

[ВОЛЬНІСТЬ](LICENSE) — канонічний текст, дослівно.
