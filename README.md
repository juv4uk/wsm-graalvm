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
        ├── Rust substrate
        ├── GraalVM / Truffle substrate  ← тут
        └── одна семантика, різні субстрати
```

**Принцип:** ми не портуємо семантику Lisp у Java. Ми переносимо виконання
того самого Lisp на інший субстрат.

---

## Поточний bootstrap / Current bootstrap

Головний proof path:

```
pinned my-lisp
     │
  canon.lisp
     │
  macro.lisp
     │
  core.lisp
     │
  Lisp-owned semantics
```

Graal має executable witness для реального `canon → macro → core` bootstrap
у спільному Context. Lisp-owned `let`, `equal?` та інші визначення не
підміняються окремою Java-семантикою.

---

## Release artifacts

Перші production-релізи мають єдиний packaging contract для всіх платформ:

- Windows x86_64 installer/package + portable ZIP;
- Debian/Ubuntu x86_64 `.deb` + portable tar.gz;
- RPM-family x86_64 `.rpm` + portable tar.gz.

Кожен артефакт прив'язаний до **одного WSM commit і одного exact
`external/my-lisp` gitlink commit**. Release pipeline має fail-closed
перевіряти цей pin перед packaging; заборонено непомітно підміняти його
рухомим `origin/main`.

Повний контракт: [docs/RELEASE-ARTIFACT-CONTRACT.md](docs/RELEASE-ARTIFACT-CONTRACT.md).

`RELEASE.txt` і machine-readable `RELEASE.json` у кожному пакеті фіксують
версію, WSM commit, my-lisp commit, платформу та GraalVM toolchain.

Публікація GitHub Release відбувається **лише після** package-level
install/run/upgrade/remove smoke tests для всіх обов'язкових артефактів.

---

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

Ми не закриваємо upstream gap Java-шорткатом, якщо відповідний закон має
бути Lisp-owned. Java-код може містити лише вузькі substrate-механізми,
не окрему семантичну владу.

---

## Контракти, які цей субстрат мусить пройти

1. `(canon-conforms?)` → canonical conformance record.
2. `tests/fixtures/conformance.lisp` виконується власним reader/eval.
3. Мовні ідентичності розпізнаються через числові semantic IDs registry.
4. Bootstrap closure випливає з реальних dependency edges pinned Lisp.
5. Tier-1 evidence є монотонним.
6. Canonical migration повертається в upstream; Graal не стає другим
   джерелом Lisp-семантики.

## Ліцензія / License

[ВОЛЬНІСТЬ](LICENSE) — канонічний текст, дослівно.
