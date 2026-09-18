# AGENTS.md — wsm-graalvm

Primary discipline — see `/home/agents/ecosystem/AGENTS.md`. Найважливіше тут:

1. **Semantic authority НЕ тут.** Зміни контракту — у my-lisp; тут тільки
   реалізація + посилання на контракти.
2. **Authority transport:** НЕ створювати `external/my-lisp`, submodule або
   копії Lisp-файлів. За замовчуванням authority = sibling `../my-lisp`;
   для іншого layout використовувати `WSM_LISP_HOME`.
3. **Числові ID, не спеллінги** (`semantic-registry.lisp`, 0001–0012...).
4. **Перед комітом `.lisp`:** `my-lisp --oracle-check <file>`.
5. **git fetch перед git push** — агенти працюють паралельно.
6. «Не моє» — не валідна категорія: розслідуй (git log, mtime, claimed-by).
7. Мета репо — субстратний свідок: критерій успіху поведінковий
   (`(canon-conforms?)`, conformance tier 1+2), не LOC, не бенчмарк.
