# AGENTS.md — wsm-graalvm

Primary discipline — see `/home/agents/ecosystem/AGENTS.md`. Найважливіше тут:

1. **Semantic authority НЕ тут.** Зміни контракту — у my-lisp; тут тільки
   реалізація + посилання на контракти.
2. **Числові ID, не спеллінги** (`semantic-registry.lisp`, 0001–0012...).
3. **Перед комітом `.lisp`:** `my-lisp --oracle-check <file>`.
4. **git fetch перед git push** — агенти працюють паралельно.
5. «Не моє» — не валідна категорія: розслідуй (git log, mtime, claimed-by).
6. Мета репо — субстратний свідок: критерій успіху поведінковий
   (`(canon-conforms?)` → `t`, conformance tier 1+2), не LOC, не бенчмарк.
