# wsm-graalvm

**WSM / my-lisp на GraalVM Truffle — субстратний свідок, не головний рантайм.**  
**WSM / my-lisp on GraalVM Truffle — a substrate witness, not the semantic authority.**

---

## Роль цього репозиторію / Role

`my-lisp` — **семантична влада** екосистеми. Він сам читає й виконує власний
контракт (`tests/fixtures/conformance.lisp`), конституцію
(`my-lisp-constitution.lisp`) і таблицю функцій
(`lib/surface/semantic-registry.lisp`) мовою, яку перевіряє.

Цей репозиторій реалізує **субстрат GraalVM/Truffle** для того самого Lisp:

```
pinned my-lisp source
        │
        ├── semantic contract + executable laws
        │
        ├── Rust substrate
        ├── GraalVM / Truffle substrate  ← тут
        ├── WASM / C / інші substrates
        │
        └── одна семантика, різні субстрати
```

**Принцип:** ми не портуємо семантику Lisp у Java. Ми переносимо виконання
того самого pinned Lisp на інший субстрат.

Тому:

- `my-lisp` залишається semantic authority;
- `wsm-graalvm` є execution substrate + evidence harness;
- Lisp-визначені `COND`, `defmacro`, `let`, `equal?` та інші закони
  не дублюються Java-реалізаціями;
- Java-код може містити лише вузькі, семантично сліпі механізми, необхідні
  субстрату;
- будь-який compatibility bridge є тимчасовим і має окремий retirement
  witness.

## Поточний bootstrap / Current bootstrap

Головний M1 proof path:

```
pinned my-lisp
     │
     ▼
  canon.lisp
     │
     ▼
  macro.lisp
     │
     ├── real Lisp MacroValue
     ▼
  semantic 0012 peer installation
     │
     ▼
  core.lisp
     │
     ├── Lisp-owned let
     └── Lisp-owned semantic functions
```

Graal уже має executable witness для реального
`canon → macro → core` bootstrap у **спільному Context**. Зокрема,
Lisp-owned `let` проходить через реальний MacroValue та lexical frame,
а не через Java-реалізацію `let`.

## Контракти, які цей субстрат мусить пройти

1. `(canon-conforms?)` → canonical conformance record — виконуваний контракт
   Канону 0+7 (`my-lisp/lib/canon.lisp`).
2. Прогін `tests/fixtures/conformance.lisp` — fixtures читаються власним
   reader-ом цієї реалізації й виконуються власним eval-ом.
3. Розпізнавання мовних ідентичностей — через **числові semantic IDs** з
   `lib/surface/semantic-registry.lisp`; Java public spellings не є
   semantic authority.
4. Bootstrap closure має випливати з реальних dependency edges pinned Lisp,
   а не з присутності файлів у директорії.
5. Tier-1 evidence має бути монотонним: новий witness не може маскувати або
   знижувати вже доведений контракт.

## Canonical COND

Канонічна форма:

```lisp
(query expected-result expression)
```

де `expected-result` — **Lisp data**, а не загальна truthiness.

Структурні та identity-рішення порівнюються у своєму domain:
`structural-kind`, `identity-relation`, `structural-relation` тощо.

Історичний two-part COND існує лише як **migration-only bridge**. Він не є
новою семантикою й має бути видалений після міграції upstream consumers.

## Правило змін / Change rule

Перед видаленням Java semantic residue спочатку додається executable witness,
який доводить, що pinned Lisp уже реально виконує відповідний закон.

Потім:

```
Lisp witness → Java residue retirement → same Lisp witness remains green
```

Саме так поступово закривається semantic authority gap без переписування
мови в Java.

## Що тут НЕ зберігається / What is NOT stored

- Копії semantic-registry, conformance та canon не стають локальною
  semantic authority; репозиторій зберігає лише потрібні reference/digest дані.
- Рішень щодо мовної семантики тут немає — authority залишається в
  `my-lisp`.

## Ліцензія / License

[ВОЛЬНІСТЬ](LICENSE) — канонічний текст, дослівно.
