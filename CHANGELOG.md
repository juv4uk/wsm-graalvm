# Changelog

Все значущі зміни в `wsm-graalvm` документуються у цьому файлі.
Формат — [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), стандарти — [SemVer](https://semver.org/).

---
## [0.1.0] — 2026-09-18

### Перший вертикальний день: my-lisp читає власний контракт на GraalVM

Той самий `my-lisp` authority (pinned `juv4uk/my-lisp@9ce5101`), той самий
pinned registry + canon + conformance fixtures — виконано на другий субстрат:
**GraalVM Community 25.3.4.1**, мова реалізована через **Truffle** (не через
polyglot-side interpreter).

#### Added
- **Truffle language `wsm`** (`WsmLanguage`) — з Reader, Compiler, `CanonRegistry`
  у first-class mechanisms (dispatch via `SemanticMechanismTable`).
- **Gate 5 self-verdict**: `(canon-conformance satisfied)` з pinned
  `external/my-lisp/lib/canon.lisp` — у JVM-lane (`scripts/run-canon.sh`) і в
  CI у Native-image-lane (`.github/workflows/native-image.yml`).
- **11 RED/green contract classes** в `src/test/java/wsm/graalvm/`:
  ReaderString · ConformanceInventory · RealTier1Inventory (selected=35) ·
  TruffleFrame · Tier1ErrorParity (8 / 7 blocked / 0 fail) ·
  VariadicLambda (F25/F26/F27) · Quote (3 surfaces, apostrophe leading/internal,
  dotted preserved) · WriteToString-1061 · MacroPeerInstaller (peers=3) ·
  BootstrapClosureTransport · NumericHeadRoute (issue #47: numeric head
  admitted iff its integer value equals a registry-row ID).
- **Mechanism budget firewall** per issue #77: `refs/lisp-mechanism-budget.lisp`
  — кожен Java-механізм класифікований, жодного unclassified entry.
- **Authority manifest driven**: `refs/lisp-dependency-manifest.lisp` — pinned
  load order: registry → canon → core → macro; sparse-checkout allowlist
  derived from the manifest, not hard-coded (`scripts/sync-authority.sh`).
- **Canonical conformance adapter**: all structural records preserved —
  `(structural-kind …)` / `(identity-relation …)` — жодних bool-bridges в core.
- **fetch + native-image witness scripts**: `scripts/fetch-third-party.sh`
  (pin-only Maven @ 25.3.4.1) з `unzip -t`-based integrity check.

#### Fixed
- Runtime classpath: Maven artifact line пов'язана точно з версією
  GraalVM-дистрибутиву 25.3.4.1; зник «version-mixed» InternalError з LibGraal.
- `bootstrap-closure-chain` fixed для `lib/macro.lisp`,
  semantic `0012` peers installer (Lisp-owned MacroValue binding) від
  upstream evidence (`macro_substrate.rs` ported 1:1).

#### Security / Integrity
- Disable «unclassified-Java-mechanism» CI check (issue #77) — fail-closed
  per-ID evidence ledger in `refs/lisp-mechanism-budget.lisp`.

#### Not included (planned for later)
- tier-2 conformance (S1 exact rational) — lane, not gate
- polyglot interop with other Truffle languages (issue #79)
- tail-calls ADR + pair-representation ADR (issue #98 trail)

[0.1.0]: https://github.com/juv4uk/wsm-graalvm/releases/tag/v0.1.0
