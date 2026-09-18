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

## Authority layout: жодного submodule і жодної копії

Локально `my-lisp` і `wsm-graalvm` мають бути sibling working trees:

```
parent/
├── my-lisp/
└── wsm-graalvm/
    ├── lib -> ../my-lisp/lib
    ├── tests -> ../my-lisp/tests
    ├── contracts -> ../my-lisp/contracts
    ├── knowledge -> ../my-lisp/knowledge
    ├── language-contract.lisp -> ../my-lisp/language-contract.lisp
    └── my-lisp-constitution.lisp -> ../my-lisp/my-lisp-constitution.lisp
```

Shell/CI code resolves authority through `WSM_LISP_HOME` when set and otherwise
uses `../my-lisp`. `MYLISP` remains a compatibility override. Missing authority
fails closed. This repository does not contain a `.gitmodules` entry or a
`my-lisp` gitlink.

## Контракти, які цей субстрат мусить пройти

1. `(canon-conforms?)` — виконуваний контракт Канону 0+7
   (`lib/canon.lisp`, directly linked from my-lisp)
2. Прогін `tests/fixtures/conformance.lisp` (tier 1+2) — fixtures читаються
   *власним* reader-ом цієї реалізації і виконуються *власним* eval-ом
3. Розпізнавання мовних ідентичностей — **лише числові semantic IDs** з
   `lib/surface/semantic-registry.lisp`; жодних hardcoded поверхневих
   спеллінгів

## Відомі ризики субстрату (вирішити до М2)

- **JVM не має proper tail calls.** Хвостові виклики — `LoopNode` у
  lambda-позиції або trampoline. Це семантичне рішення, не оптимізація.
- **Пара на JVM:** representation має бути зафіксована окремим ADR на основі
  вимірювань, не здогадки.

## Що тут НЕ зберігається

- копії semantic-registry, conformance, canon або інших Lisp-authority files;
- dependency pin, який робить GraalVM власником версії мови;
- рішення щодо мовної семантики — authority лишається в my-lisp.

## Ліцензія

[ВОЛЬНІСТЬ](LICENSE) — канонічний текст, дослівно.
