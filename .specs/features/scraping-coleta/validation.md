# Scraping & Coleta Validation

> **Iteration 2 of fix→re-verify loop.** Iteration 1 (2026-09-07) returned FAIL: SCRAPE-03's WARN branch was unreachable in production (`BaseScraper.aguardarElementos` threw `TimeoutException` on zero results instead of returning an empty list), plus four test-precision gaps (SCRAPE-04/16 log-content, SCRAPE-07/09 saved-content, SCRAPE-11 default-value readback). Fix commits `5da0dd2` (SCRAPE-03) and `ebbaf27` (the four precision gaps) landed as tasks T13/T14. This report re-derives every AC independently against current `HEAD` — it does not take iteration 1's or the fix commits' claims at face value — and **overwrites** the iteration-1 report below.

**Date**: 2026-09-07
**Spec**: `.specs/features/scraping-coleta/spec.md`
**Diff range**: `7b10c43..HEAD` (`b1bfbb1`..`ebbaf27`, 14 commits)
**Verifier**: independent sub-agent (author ≠ verifier), fresh re-derivation (iteration 1's report read for context only, not trusted)

---

## Task Completion

| Task | Status  | Notes |
| ---- | ------- | ----- |
| T1   | ✅ Done | Flyway+Testcontainers+migration; `BotAmazonSpringApplicationIT` confirms 4 tables, 5 categorias, 4 app_config rows |
| T2   | ✅ Done | `AppConfig` entity+repository, 3 integration tests |
| T3   | ✅ Done | `ConfigService`, 6 unit tests |
| T4   | ✅ Done | `CategoriaColeta` entity+repository, 2 integration tests |
| T5   | ✅ Done | `Product` entity+repository, 3 integration tests |
| T6   | ✅ Done | `PriceHistory` entity+repository, 2 integration tests |
| T7   | ✅ Done | `SeleniumConfig`, no dedicated test (documented, indirectly exercised by full context boot) |
| T8   | ✅ Done | `BaseScraper`, 6 unit tests |
| T9   | ✅ Done | `ScrapedProductDTO`, 4 unit tests |
| T10  | ✅ Done | `AmazonSelectorsProperties`+`AmazonProductScraper`, 6 unit tests (4 original + 2 from T13) |
| T11  | ✅ Done | `ColetaService`, 8 unit tests (assertions reinforced by T14, no new tests added) |
| T12  | ✅ Done | `CandidatoPromocaoDTO`+`PromotionDetectionService`, 6 unit tests |
| T13  | ✅ Done | `AmazonProductScraper.buscarPorKeyword` now catches `TimeoutException` specifically (not `Exception`) around `aguardarElementos`, returns `List.of()` — verified independently below |
| T14  | ✅ Done | Log-content and saved-content assertions added to `ColetaServiceTest` + `BotAmazonSpringApplicationIT` — verified independently below |

All 14 tasks marked `✅ Complete` in `tasks.md`. No partial/blocked tasks.

---

## Spec-Anchored Acceptance Criteria

> SCRAPE-NN IDs re-derived independently from `spec.md`'s own AC bullet order per story, same convention as iteration 1.

### P1: Coleta de produtos por categoria configurável

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-01: scheduler invoca → busca 1ª página por keyword de cada categoria ativa | `buscarPorKeyword` chamado com a keyword de cada categoria retornada por `findByAtivoTrue()` | `src/test/java/.../service/ColetaServiceTest.java:85` — `verify(amazonProductScraper).buscarPorKeyword("monitor gamer");` | ✅ PASS |
| SCRAPE-02: extrair ASIN, título, preço atual, preço riscado, imagem, URL | Todos os 6 campos populados corretamente a partir do card | `src/test/java/.../scraper/AmazonProductScraperTest.java:86-93` — asserts em todos os 6 campos do `ScrapedProductDTO` | ✅ PASS |
| SCRAPE-03: categoria sem produto → WARN identificando categoria+keyword, continua | (a) processamento continua para as demais categorias; (b) real Selenium wait path returns empty list (not exception) on zero cards, so the WARN branch is reachable; (c) other exception types still propagate/escalate to ERROR (SCRAPE-16), no blanket swallow | (a) `ColetaServiceTest.java:99-101`; (b) `AmazonProductScraperTest.java:119-125` — `buscarPorKeywordRetornaListaVaziaQuandoZeroCardsEncontrados`, exercises a **real** `WebDriverWait`(300ms) via `driver.findElements(...)` returning `List.of()` → `assertThat(produtos).isEmpty()` without throwing; (c) `AmazonProductScraperTest.java:128-133` — `buscarPorKeywordPropagaExcecoesQueNaoSaoTimeout`, `driver.findElements` throws `WebDriverException` → `assertThrows(WebDriverException.class, ...)` | ✅ PASS — **fixed**. `AmazonProductScraper.java:36-40` now catches `org.openqa.selenium.TimeoutException` specifically, not `Exception` (confirmed by reading the current source, not the commit message) |
| SCRAPE-04: preço ≤R$0 ou >R$50.000 → descarta + WARN com ASIN+valor bruto | (a) produto descartado, não persistido; (b) log WARN contém o ASIN e o valor bruto | (a) `ColetaServiceTest.java:121-123`; (b) `ColetaServiceTest.java:113-129` — `ListAppender` on `Logger.getLogger(ColetaService.class)`, `assertThat(appender.list).anyMatch(evento -> evento.getLevel()==Level.WARN && evento.getFormattedMessage().contains("B0INVALIDO") && evento.getFormattedMessage().contains("60000.00"))` | ✅ PASS — **fixed**, log-content now asserted, not just behavior |
| SCRAPE-05: ASIN existente → atualiza, não duplica | `save` chamado com o mesmo `id`, título/preço atualizados | `ColetaServiceTest.java:149-153` — `argThat(p -> p.getId().equals(42L) && p.getTitulo().equals("Titulo Novo") && p.getPrecoAtual().compareTo(new BigDecimal("90.00"))==0)` | ✅ PASS |
| SCRAPE-06: aguarda intervalo configurável entre categorias | Intervalo lido via `ConfigService` com a chave/default corretos | `ColetaServiceTest.java:183-187` — `verify(configService).getLong(CHAVE_INTERVALO_CATEGORIAS, 5L)` | ✅ PASS |

### P1: Histórico de preço por produto

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-07: cada produto processado grava histórico (ASIN+preço+timestamp) | Uma entrada de `PriceHistory` por produto processado, com o ASIN e o preço corretos | `ColetaServiceTest.java:157-176` — `ArgumentCaptor<PriceHistory>`, `verify(priceHistoryRepository, times(2)).save(capturado.capture())`, then `assertThat(salvos).extracting(ph -> ph.getProduct().getAsin()).containsExactlyInAnyOrder("B0A","B0B")` + per-ASIN `getPreco()).isEqualByComparingTo(...)` | ✅ PASS — **fixed**, saved content (ASIN + price) now asserted, not just call count |
| SCRAPE-08: histórico mantido indefinidamente, sem expiração automática | Ausência de qualquer job/lógica de purge | Requisito negativo — nenhum código de expiração existe (confirmado por leitura de todo o diff atual); não há teste positivo possível para "ausência de feature" | ⚠️ Spec-precision gap — satisfeito por omissão, não testável diretamente (unchanged from iteration 1, correctly not "fixed" since there was nothing to fix) |
| SCRAPE-09: 2+ coletas em dias diferentes → 1 entrada por coleta, nunca dedup por dia | `countByProduct` == 2 após 2 inserts distintos para o mesmo produto, com preços diferentes preservados | `src/test/java/.../repository/PriceHistoryRepositoryIT.java:54-61` — `assertThat(total).isEqualTo(2)`; content-level cross-check also in `ColetaServiceTest.java:170-175` (see SCRAPE-07 row) | ✅ PASS |

### P1: Detecção de queda de preço relevante

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-10: ≥2 entradas de histórico → base = menor preço do histórico | preço atual 80, base 100 (mín. histórico), 10% mínimo → candidato com 20.00% | `src/test/java/.../service/PromotionDetectionServiceTest.java:64-65` | ✅ PASS |
| SCRAPE-11: 10% padrão, configurável sem alteração de código | (a) alterar o percentual mínimo muda o resultado; (b) o valor `10` semeado em `app_config` para `coleta.percentual-minimo-queda` é lido de volta e confirmado | (a) `PromotionDetectionServiceTest.java:69-79` — 20% threshold vs 15% drop → `assertThat(candidatos).isEmpty()`; (b) `src/test/java/.../BotAmazonSpringApplicationIT.java:39-41` — `jdbcTemplate.queryForObject("SELECT valor FROM app_config WHERE chave = 'coleta.percentual-minimo-queda'", String.class)` then `assertThat(percentualMinimoQueda).isEqualTo("10")` | ✅ PASS — **fixed**, default value now read back and asserted, not just row-counted |
| SCRAPE-12: sinalizado → disponibilizado como candidato (contrato com canais-disparo) | Retorna `CandidatoPromocaoDTO` com produto+percentual corretos | `PromotionDetectionServiceTest.java:92-96` | ✅ PASS |
| SCRAPE-13: preço não caiu desde o último candidato → não sinaliza de novo | `lastCandidatoPreco == precoAtual` → lista vazia, `save` nunca chamado | `PromotionDetectionServiceTest.java:107-111` | ✅ PASS |

### P2: Fallback de detecção em cold start

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-14: <2 entradas + preço riscado presente → base = preço riscado | count=1, riscado=100, atual=80 → candidato 20.00%, `findMenorPrecoByProduct` nunca chamado | `PromotionDetectionServiceTest.java:114-126` | ✅ PASS |
| SCRAPE-15: <2 entradas + sem preço riscado → não sinaliza | count=0, riscado=null → lista vazia, `save` nunca chamado | `PromotionDetectionServiceTest.java:129-138` | ✅ PASS |

### Edge Cases

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-16: falha WebDriver / CAPTCHA → ERROR log com categoria, continua as demais | (a) exceção isolada por categoria, continua para a próxima; (b) ERROR loga a categoria afetada | (a) `ColetaServiceTest.java:191-209` — `assertDoesNotThrow`, then `verify(productRepository).save(argThat(p -> p.getAsin().equals("B0Y")))` for the 2nd category after an exception in the 1st; (b) `ColetaServiceTest.java:201-214` — `ListAppender`, `assertThat(appender.list).anyMatch(evento -> evento.getLevel()==Level.ERROR && evento.getFormattedMessage().contains("MONITOR"))` | ✅ PASS — **fixed**, ERROR log content now asserted |
| SCRAPE-17: zero produtos no ciclo inteiro → WARN, não fatal | WARN com a mensagem certa + não lança exceção | `ColetaServiceTest.java:218-235` — `assertDoesNotThrow` + `ListAppender` match on `Level.WARN` + message containing "ciclo inteiro extraiu zero produtos" | ✅ PASS |

**Status**: ✅ All 17 ACs covered, spec-anchored outcome confirmed. 1 unavoidable spec-precision gap remains (SCRAPE-08, a negative requirement — no test can positively prove the absence of a purge job beyond reading the full diff, which was done). No behavioral gaps remain.

---

## Independent confirmation of the two fix commits

**`5da0dd2` (SCRAPE-03)** — Read `AmazonProductScraper.java:36-40` directly (not the commit message): the catch clause is `catch (TimeoutException e)`, explicitly importing `org.openqa.selenium.TimeoutException`, not `java.lang.Exception` or `WebDriverException`. `BaseScraper.aguardarElementos` (`BaseScraper.java:27-29`) is untouched — it still calls `wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(locator))`, which still throws `TimeoutException` on zero elements (this is Selenium's own semantics, not something this codebase controls); the fix moved the catch to the caller instead of changing wait semantics. Two tests exercise this with a **real** `WebDriverWait` (`Duration.ofMillis(300)`, not a scraper-level mock that bypasses Selenium's wait behavior): `buscarPorKeywordRetornaListaVaziaQuandoZeroCardsEncontrados` (zero cards → empty list, no throw) and `buscarPorKeywordPropagaExcecoesQueNaoSaoTimeout` (a `WebDriverException` from `driver.findElements` still propagates). Both ran and passed in the full `mvn clean verify` gate (see below) and both were used as discrimination-sensor targets (see below) — mutating the catch back to `Exception` killed the second test, proving it would actually catch a regression to a blanket catch.

**`ebbaf27`** (T14, four precision gaps) — Read the current `ColetaServiceTest.java` and `BotAmazonSpringApplicationIT.java` directly. All four claimed reinforcements are present and use the mechanisms claimed: `ListAppender` for SCRAPE-04/16 log content, `ArgumentCaptor<PriceHistory>` for SCRAPE-07/09 saved content, and a direct `jdbcTemplate.queryForObject` readback for SCRAPE-11's seeded `10`. None of these are shallow — each asserts the specific text/value the spec cares about, not merely that a call happened.

---

## Discrimination Sensor

Isolation: `git worktree add <scratchpad>/wt-sensor HEAD` (temporary git worktree, never `git stash`). Baseline `git status --porcelain` of the real tree: empty, before and after. This iteration's sensor targeted the two fixed areas specifically, per the task brief.

| Mutation | File:line | Description | Killed? |
| --- | --- | --- | --- |
| 1 | `scraper/AmazonProductScraper.java:38` | Widened `catch (TimeoutException e)` → `catch (Exception e)` around `aguardarElementos` | ✅ Killed — `mvn test -Dtest=AmazonProductScraperTest`: 1 failure (`buscarPorKeywordPropagaExcecoesQueNaoSaoTimeout`, which now expects a `WebDriverException` to propagate but the widened catch swallows it) |
| 2 | `service/ColetaService.java:79` | Removed ASIN/price from the SCRAPE-04 WARN log message (`log.warn("COLETA: preco fora da faixa de sanidade")`, no args) | ✅ Killed — `mvn test -Dtest=ColetaServiceTest`: 1 failure (`precoForaDaFaixaDeSanidadeDescartaProdutoEContinua`, whose `ListAppender` assertion checking for `"B0INVALIDO"`/`"60000.00"` in the message no longer matches) |

**Sensor depth**: lightweight (2 mutations; feature is not P0/payment/auth). Both mutations targeted the exact code this iteration exists to re-scrutinize (the SCRAPE-03 fix and one of the four T14 reinforcements) and both were killed cleanly, with no ambiguity in the failure output.
**Result**: 2/2 killed - PASS ✅

Worktree cleanup: `git worktree remove --force` hit a Windows long-path error on this machine's temp path (`Filename too long`) but had already deregistered the worktree from git's internal state; the leftover directory was deleted with `rm -rf`, then confirmed absent. `git worktree list` after cleanup shows only the real repo worktree. `git status --porcelain` and `git rev-parse HEAD` on the real tree confirmed identical to the pre-sensor baseline (empty, `ebbaf27`).

---

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code | ✅ — T13/T14 changes are surgical: one narrowed catch clause, plus assertion-only additions to already-existing tests. No new production classes, no new test files. |
| Surgical changes | ✅ — diff restricted to `AmazonProductScraper.java`, `ColetaServiceTest.java`, `BotAmazonSpringApplicationIT.java`, plus `tasks.md`/`STATE.md` bookkeeping |
| No scope creep | ✅ — no unrelated refactors; `BaseScraper.aguardarElementos` deliberately left untouched (the fix lives at the call site, matching the design decision that the scraper — not the generic base helper — owns the zero-results-is-not-an-error semantics) |
| Matches patterns | ✅ — the T14 `ListAppender` pattern reuses the exact pattern already established in SCRAPE-17's test (as the task's own "Reuses" note claims, and confirmed by reading both) |
| Spec-anchored outcome check (asserted values match spec) | ✅ — all 17 ACs now have a precise, non-shallow assertion; only SCRAPE-08 remains a genuine (unavoidable) spec-precision gap |
| Per-layer Coverage Expectation met (domain 1:1 ACs; routes happy+edge+error) | ✅ — no route/e2e layer in this feature (headless); domain layer has 1:1 coverage of all 17 ACs |
| Every test maps to a spec requirement - no unclaimed tests | ✅ — no new test methods were added by T14 (assertion-only reinforcement of existing tests); T13 added exactly 2 tests, both mapped to SCRAPE-03 |
| Documented guidelines followed: [file(s) or "none - strong defaults applied"] | none — same as iteration 1, `tasks.md`'s Test Coverage Matrix confirms no formal repo guideline; strong defaults applied consistently |

---

## Edge Cases

- [x] WebDriver falha ao carregar página de busca: isolado por categoria, ciclo continua, ERROR log content confirmed via `ListAppender` (`ColetaServiceTest.java:191-215`)
- [x] Amazon retorna CAPTCHA/bloqueio: now correctly distinguishable from "zero resultados legítimo" — the SCRAPE-03 fix means a real zero-cards timeout returns an empty list (WARN path), while any other WebDriver failure (dead session, navigation error, etc.) still escalates through the generic per-category catch to ERROR (SCRAPE-16); previously both were indistinguishable ERROR paths
- [x] Zero produtos extraídos no ciclo inteiro: WARN não fatal, tested precisely via `ListAppender` (`ColetaServiceTest.java:218-235`)

---

## Gate Check

- **Gate command**: `mvn clean verify` (via wrapper `C:\Users\jmartinsc\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`)
- **Result**: 47 passed (36 unit via Surefire + 11 integration via Failsafe/Testcontainers Postgres), 0 failed, 0 skipped. `BUILD SUCCESS`.
- **Test count before this feature**: 1 (placeholder `BotAmazonSpringApplicationTests.contextLoads()`)
- **Test count after iteration 1 (T1-T12)**: 45 (34 unit + 11 integration, per iteration 1's report)
- **Test count after iteration 2 (T1-T14, current HEAD)**: 47 (36 unit + 11 integration)
- **Delta since iteration 1**: +2 new tests (both in `AmazonProductScraperTest`, from T13: `buscarPorKeywordRetornaListaVaziaQuandoZeroCardsEncontrados`, `buscarPorKeywordPropagaExcecoesQueNaoSaoTimeout`). T14 added zero new test methods (assertion-only reinforcement), matching its own "Done when" claim of "nenhum teste novo adicionado". Count matches expectation — no unexplained deltas.
- **Skipped tests**: none
- **Failures**: none

---

## Fix Plans

None. No gaps requiring a fix task remain. SCRAPE-08 stays a documented spec-precision gap (a negative requirement — "no purge exists" — that has no positive test to write; already correctly flagged, not newly discovered).

---

## Requirement Traceability Update

| Requirement | Previous Status (iteration 1) | New Status |
| --- | --- | --- |
| SCRAPE-01 | ✅ Verified | ✅ Verified |
| SCRAPE-02 | ✅ Verified | ✅ Verified |
| SCRAPE-03 | ❌ Needs Fix | ✅ Verified (fixed — real Selenium wait path confirmed reachable, non-timeout exceptions confirmed to still propagate) |
| SCRAPE-04 | ⚠️ Verified (test-precision gap) | ✅ Verified (log content now asserted) |
| SCRAPE-05 | ✅ Verified | ✅ Verified |
| SCRAPE-06 | ✅ Verified | ✅ Verified |
| SCRAPE-07 | ⚠️ Verified (test-precision gap) | ✅ Verified (saved content now asserted) |
| SCRAPE-08 | ⚠️ Verified (not independently testable) | ⚠️ Verified (not independently testable — unchanged, unavoidable) |
| SCRAPE-09 | ✅ Verified | ✅ Verified |
| SCRAPE-10 | ✅ Verified | ✅ Verified |
| SCRAPE-11 | ⚠️ Verified (default value not asserted) | ✅ Verified (default value now read back and asserted) |
| SCRAPE-12 | ✅ Verified | ✅ Verified |
| SCRAPE-13 | ✅ Verified | ✅ Verified |
| SCRAPE-14 | ✅ Verified | ✅ Verified |
| SCRAPE-15 | ✅ Verified | ✅ Verified |
| SCRAPE-16 | ⚠️ Verified (test-precision gap) | ✅ Verified (ERROR log content now asserted) |
| SCRAPE-17 | ✅ Verified | ✅ Verified |

---

## Summary

**Overall**: ✅ Ready

**Spec-anchored check**: 16/17 ACs matched spec outcome cleanly; 1 unavoidable spec-precision gap (SCRAPE-08, negative requirement). Zero behavioral gaps.
**Sensor**: 2/2 mutations killed
**Gate**: 47 passed, 0 failed, 0 skipped

**What works**: All of iteration 1's findings are independently confirmed fixed. SCRAPE-03's WARN branch is now genuinely reachable — verified by reading `AmazonProductScraper.java` directly (the catch is narrowly typed to `TimeoutException`, not a blanket `Exception`) and by a test that exercises a real `WebDriverWait` rather than mocking the scraper's own behavior away. A companion test confirms non-timeout exceptions still propagate, so the fix isn't a silent broad catch that would also defeat SCRAPE-16. All four precision gaps (SCRAPE-04/16 log content, SCRAPE-07/09 saved content, SCRAPE-11 default readback) are reinforced with the exact assertions iteration 1 asked for. The discrimination sensor confirms both fixes are load-bearing: reverting either one (widening the catch, or stripping log content) fails the corresponding test.

**Issues found**: None.

**Next steps**: Feature is ready to be marked done. No further fix→re-verify iteration needed.
