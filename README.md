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
того самого Lisp на інший субстрат.

Водночас upstream може мати незавершені семантичні міграції. Тоді
`wsm-graalvm` може тимчасово містити **upstream-directed migration work**:
ми завершуємо зміну в термінах самого `my-lisp`, доводимо її executable
witness'ами на Graal, а потім повертаємо canonical зміну в upstream.

Тому:

- `my-lisp` залишається canonical semantic authority;
- `wsm-graalvm` є execution substrate + evidence harness;
- Lisp-визначені `COND`, `defmacro`, `let`, `equal?` та інші закони
  не дублюються як окрема Graal-семантика;
- якщо upstream-закон ще не завершений, його migration work може бути
  реалізований тут, але має бути спрямований на upstream і зрештою
  повернутися в canonical `my-lisp`;
- Java-код може містити вузькі substrate-механізми, необхідні для виконання,
  але не окрему семантичну владу;
- compatibility bridge є тимчасовим і має окремий retirement witness.

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

Graal має executable witness для реального
`canon → macro → core` bootstrap у **спільному Context**. Lisp-owned
`let` проходить через реальний MacroValue та lexical frame, а не через
Java-реалізацію `let`.

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

## Правило міграції / Migration rule

Кожна незавершена upstream-семантична міграція проходить цикл:

```
upstream contract / intent
        ↓
minimal Lisp-side migration
        ↓
Graal executable witness
        ↓
upstream PR / merge
        ↓
advance pinned source
        ↓
retire temporary substrate residue
```

Ми не закриваємо upstream gap Java-шорткатом, якщо відповідний закон
має бути Lisp-owned. Якщо для виконання потрібен substrate projection,
він має бути вузьким, semantics-blind і мати окремий witness.

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
6. Міграційна зміна, що стала canonical, має бути повернута в upstream;
   Graal не повинен назавжди ставати другим джерелом Lisp-семантики.

## Що тут НЕ зберігається / What is NOT stored

Цей репозиторій може містити pinned/mirrored Lisp-файли, потрібні для
міграції та executable verification. Їхня роль — **конкретний upstream
input/witness**, а не незалежне джерело істини.

Reference/digest дані фіксують, проти якої версії `my-lisp` працює доказ.
Після upstream merge pin оновлюється, а тимчасові migration-only зміни
повинні зникати або ставати звичайною частиною нового upstream baseline.

## Ліцензія / License

[ВОЛЬНІСТЬ](LICENSE) — канонічний текст, дослівно.

## Реліз / Product release

Release pipeline знаходиться в `.github/workflows/release.yml` і є manual/fail-closed.
Один immutable WSM source commit проходить semantic/bootstrap gate, після чого
native runners будують Linux x86_64 та Windows x86_64 Native Image.

### Release operator flow

1. Merge release pipeline у `main`.
2. Запусти `release-build` у GitHub Actions.
3. Передай SemVer без `v`, наприклад `0.1.0`.
4. Для відтворюваності передай точний `source_ref` commit.
5. Tag/GitHub Release створюється лише після semantic gate, Native Image, package
   install/run/uninstall smoke tests і перевірки всіх артефактів.

### Canonical v0.1.x artifacts

- Windows x86_64 installer: `wsm-graalvm-<version>-windows-x86_64-installer.exe`
- Windows x86_64 portable ZIP + raw Native Image executable
- Debian/Ubuntu x86_64: `wsm-graalvm-<version>-linux-x86_64.deb`
- RPM-family x86_64: `wsm-graalvm-<version>-linux-x86_64.rpm`
- Linux x86_64 portable ZIP + raw Native Image executable
- SHA256 sidecar for every distributable
- SPDX 2.3 SBOM inside every platform bundle

Every artifact carries the same WSM commit and exact `external/my-lisp` gitlink.
The portable bundle contains the complete pinned `my-lisp` source (without its
nested `.git` directory), not a Java semantic reimplementation.

Linux packages install a versioned private runtime under
`/usr/lib/wsm-graalvm/<version>/` and expose `/usr/bin/wsm`.
The Windows installer installs under `Program Files\\WSM\\<version>` and adds
that directory to the machine PATH.

### Semantic boundary

The release does not create a second Lisp implementation. Bootstrap remains:

`canon -> macro -> core -> user Lisp`

The pinned upstream Lisp source is the semantic authority; GraalVM/Truffle and
Native Image provide the execution substrate only.
