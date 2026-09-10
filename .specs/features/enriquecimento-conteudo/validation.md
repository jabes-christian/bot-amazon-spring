# Enriquecimento de Conteúdo Validation

## Validation: enriquecimento-conteudo - FAIL ❌

> **Iteration 2 — supersedes iteration 1.** Iteration 1 (2026-09-08) found 1 blocking gap: ENRICH-08's happy-path banner test (`BannerImageServiceIT.downloadComSucessoEImagemValidaGeraBannerJpeg800x800`) only asserted dimensions/format, never that the discount%/de-por overlay was actually drawn. Fix task T7 (commit `16fe0f3`) added a pixel-comparison assertion to that test (comparing a pure-background pixel against a pixel inside the overlay bar) and extended the two `CopyGenerationServiceTest` LLM-failure tests (exception, empty response) to assert WARN log content, matching what the timeout test already did. This iteration is a full independent re-verification (not a diff-only patch review) of the whole feature, re-run from scratch, with an adversarial focus on whether the T7 fix is actually sound or just superficially plausible. **Verdict: the T7 fix for the secondary log-content note is sound and confirmed killed by mutation testing. The T7 fix for the primary ENRICH-08 gap is NOT sound — the discrimination sensor proves the new pixel assertion only detects the presence of the semi-transparent black background bar, not the discount%/de-por text itself. The gap is still open, now with empirical proof.**

**Date**: 2026-09-08
**Spec**: `.specs/features/enriquecimento-conteudo/spec.md`
**Diff range**: `a4bee92..HEAD` (branch `tasks/enriquecimento-conteudo`) — `git log a4bee92..HEAD --oneline` confirms 7 commits, `3b2d40a`..`16fe0f3`, T1 through T7
**Verifier**: independent sub-agent (author ≠ verifier), iteration 2 of the fix→re-verify cycle (max 3 before escalation)

---

## Task Completion

| Task | Status  | Notes |
| ---- | ------- | ----- |
| T1: Migration V2 (seed config) + extensão smoke test | ✅ Done | `V2__seed_enriquecimento_config.sql:1-4` inserts the 3 keys; `BotAmazonSpringApplicationIT.java:36-37,43-45` asserts `app_config` count=7 and `enriquecimento.llm-timeout-segundos`='15' |
| T2: `findMenorPrecoDesde` | ✅ Done | `PriceHistoryRepository.java:20-21`; `PriceHistoryRepositoryIT.java:77-106` (2 tests, window boundary both directions) |
| T3: `LlmConfig` (bean `ChatModel`) | ✅ Done | No dedicated test per task design (external-credential bean); indirectly exercised via mocked `ChatModel` in T4's tests |
| T4: `CopyResultadoDTO` + `CopyGenerationService` | ✅ Done | 8 tests in `CopyGenerationServiceTest.java` |
| T5: `pom.xml` (RestClient) + `BannerImageService` | ✅ Done | 5 tests in `BannerImageServiceIT.java` |
| T6: `ConteudoEnriquecidoDTO` + `EnriquecimentoService` | ✅ Done | 3 tests in `EnriquecimentoServiceTest.java` |
| T7: Fecha gap ENRICH-08 + precisão de log ENRICH-04 | ⚠️ Partially effective | See ENRICH-08/ENRICH-04 rows below — the log-content half of T7 is confirmed sound; the overlay-pixel half of T7 does not close the original gap (still no assertion tied to the discount%/de-por text itself) |

All 7 tasks committed (`3b2d40a`..`16fe0f3`). No blocked/partial *tasks* in the tracking sense — T7 executed as designed, but its core deliverable (proving ENRICH-08's text overlay renders) did not land, per the sensor result below.

---

## Spec-Anchored Acceptance Criteria

| Requirement | Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- | --- |
| ENRICH-01 | WHEN produto candidato selecionado THEN solicita ao LLM copy pt-BR com nome, preço de/por, percentual, CTA | `viaLlm=true`, texto contém nome/preços/percentual/CTA | `CopyGenerationService.java:66-78` (`montarPrompt`) + `CopyGenerationServiceTest.java:77-92` `llmDentroDoTimeoutRetornaViaLlmTrueComTextoDoModelo` — asserts `viaLlm()` true and `texto()` contains title/prices/percent/CTA | ✅ PASS |
| ENRICH-02 | THE sistema SHALL gerar a copy 1x por produto/evento, reutilizada para todos canais | `gerarCopy` called exactly once | `EnriquecimentoServiceTest.java:39-49` — `verify(copyGenerationService, times(1)).gerarCopy(candidato)` | ✅ PASS |
| ENRICH-03 | THE sistema SHALL inserir o link de afiliado na copy final, substituindo qualquer link do LLM | link `produto.urlProduto + "?tag=" + tag` sempre presente | `CopyGenerationService.java:86-96` (`montarResultado`/`montarLinkAfiliado`) + `CopyGenerationServiceTest.java:161-173`, `:176-186` | ✅ PASS — confirmed by mutation (see Sensor #3): renaming `?tag=` broke 4 tests |
| ENRICH-04 | IF LLM exceder timeout OU erro (rate limit/indisponível/vazio) THEN usa fallback + loga WARN com motivo | `viaLlm=false`, WARN log with ASIN+reason, for all 3 sub-cases | `CopyGenerationService.java:55-63` (single catch, single log statement covers `TimeoutException`/`ExecutionException`/`InterruptedException`/`IllegalStateException`) + `CopyGenerationServiceTest.java:94-110` (timeout), `:112-126` (exception, now asserts log incl. "rate limit"), `:128-142` (empty response, now asserts log incl. "resposta vazia do LLM") | ✅ PASS — **T7 gap closed and confirmed**: mutation sensor #2 (removing the `log.warn(...)` call) failed all 3 tests, proving each sub-case's log-content assertion is real, not tautological |
| ENRICH-05 | IF LLM falhar THEN monta copy com template fixo, sem chamar LLM de novo | template built without re-invoking LLM | `CopyGenerationService.java:80-84` (`montarCopyTemplate`) + `CopyGenerationServiceTest.java:144-158` — `verify(chatModel, times(1)).chat(anyString())` | ✅ PASS |
| ENRICH-06 | THE sistema SHALL garantir 100% dos disparos com link de afiliado, LLM ou template | link present both paths | `CopyGenerationServiceTest.java:160-173` `linkDeAfiliadoPresenteTantoNoCaminhoLlmQuantoNoTemplate` | ✅ PASS |
| ENRICH-07 (per-product signal, per design split with `canais-disparo`) | THE sistema SHALL registrar quantos produtos usaram LLM vs template | `CopyResultadoDTO.viaLlm()`/`ConteudoEnriquecidoDTO.copyViaLlm()` correct per product | `CopyGenerationServiceTest.java` (8 tests assert `viaLlm`) + `EnriquecimentoServiceTest.java:51-62`,`:64-76` (propagation) | ✅ PASS (scope matches design.md's documented split) |
| ENRICH-08 | WHEN produto selecionado E imagem baixada com sucesso THEN compõe banner sobrepondo percentual e preço de(riscado)/por(atual) | banner with dimensions/format contract **and** discount%/preço de-por text actually rendered | `BannerImageService.java:112-144` (`compor` draws `textoDesconto`/`textoDePor` at lines 121-130) + `BannerImageServiceIT.java:99-126` `downloadComSucessoEImagemValidaGeraBannerJpeg800x800` — samples green channel at `(400,400)` (pure background) vs `(750,685)` (inside the overlay bar, `assertThat(gOverlay).isLessThan(gFundo - 80)`) | ❌ **GAP CONFIRMED STILL OPEN** — see Discrimination Sensor #1. The pixel check only proves the semi-transparent black `fillRect` (the overlay's *background*) darkened that region; it passes unchanged even with both `drawString` calls (the discount% and de/por text itself, the literal spec content) deleted. Per evidence-or-zero, the specific claim in the spec — that percentual and preço de/por are overlaid — is still uncovered by any assertion that would fail if that text were never drawn |
| ENRICH-09 | THE sistema SHALL gerar o banner 1x por produto, reutilizado para todos canais | `gerarBanner` called exactly once | `EnriquecimentoServiceTest.java:39-49` — `verify(bannerImageService, times(1)).gerarBanner(candidato)` | ✅ PASS |
| ENRICH-10 | IF download falhar OU conteúdo não é imagem válida THEN `Optional.empty()` + WARN com ASIN e motivo | `Optional.empty()`, WARN log w/ ASIN+reason | `BannerImageService.java:52-58`,`:60-66` + `BannerImageServiceIT.java:132-142` (HTTP 500), `:144-155` (non-image content) | ✅ PASS — real socket-timeout sub-case not simulated (self-documented, justified in `tasks.md` T5 note: same `catch(RestClientException)` covers `ResourceAccessException` and HTTP errors alike) |
| ENRICH-11 | THE sistema SHALL armazenar banner em dir temp e remover após disparo concluído | temp file created; `removerBanner` deletes it | `BannerImageService.java:71`,`:83-90` + `BannerImageServiceIT.java:157-165` `removerBannerApagaOArquivo` | ✅ PASS — timing of the removal call (after all channels done) is `canais-disparo`'s contract responsibility, correctly out of scope |
| ENRICH-12 | IF produto marcado texto puro THEN Telegram sinaliza `sendMessage`, WhatsApp sinaliza texto sem mídia | `ConteudoEnriquecidoDTO.bannerPath()==null` signals text-only mode | `EnriquecimentoServiceTest.java:64-76` — `assertThat(resultado.bannerPath()).isNull()` | ✅ PASS (signal-production scope only; channel consumption is `canais-disparo`) |
| ENRICH-13 | THE sistema SHALL preservar no texto modo-texto-puro todos elementos da copy | copy content unaffected by banner absence | `EnriquecimentoServiceTest.java:64-76` — `assertThat(resultado.copy()).isEqualTo("copy completa sem banner")` | ✅ PASS |
| ENRICH-14 | WHERE preço atual for o menor valor dos últimos N dias THEN inclui selo | selo drawn (pixel-verifiable) | `BannerImageService.java:103-110`,`:132-139` + `BannerImageServiceIT.java:167-189` `precoAtualIgualAoMenorDosUltimosDiasIncluiSeloNoBanner` (samples RGB in badge region, confirms red) + `PriceHistoryRepositoryIT.java:92-106` | ✅ PASS |
| ENRICH-15 (edge case) | IF texto exceder 1024 chars THEN trunca preservando link íntegro no final | `texto.length() <= limite`, `endsWith(link)` | `CopyGenerationService.java:99-105` (`truncarCorpo`) + `CopyGenerationServiceTest.java:188-199` | ✅ PASS |
| ENRICH-16 (edge case) | IF LLM retornar conteúdo que remove/altera o link THEN reinsere o link correto | LLM's own URL stripped, correct link present | `CopyGenerationService.java:86-88` (regex strip + rebuild) + `CopyGenerationServiceTest.java:175-186` | ✅ PASS |

**Status**: ❌ 1 gap present (ENRICH-08 — still uncovered after the T7 fix attempt) / 15 of 16 requirements fully covered with precise evidence

---

## Discrimination Sensor

| Mutation | File:line | Description | Killed? |
| -------- | --------- | ------------ | ------- |
| 1 | `BannerImageService.java:127-130` (in scratch worktree) | Removed both `g.drawString(textoDesconto, ...)` and `g.drawString(textoDePor, ...)` calls, keeping the `fillRect` translucent-bar background and the font-setting lines untouched | ❌ **SURVIVED** — `mvn verify -Dit.test=BannerImageServiceIT` still reported `Tests run: 5, Failures: 0`. `downloadComSucessoEImagemValidaGeraBannerJpeg800x800`'s new pixel assertion (`gOverlay < gFundo - 80` at `(750,685)`) passed even with the discount%/de-por text entirely absent from the banner, because the semi-transparent black `fillRect(0, 680, 800, 120)` alone darkens that pixel regardless of whether any text is drawn on top of it. **This proves the T7 fix does not verify the spec-required content (percentual/preço de-por), only the presence of the overlay's background rectangle.** |
| 2 | `CopyGenerationService.java:60-61` | Removed the `log.warn("ENRIQUECIMENTO: falha ao gerar copy via LLM...")` call entirely from the shared catch block (all 3 failure sub-cases route through it) | ✅ Killed — all 3 dependent tests failed: `timeoutExcedidoCaiParaTemplateComViaLlmFalseELogaWarn`, `excecaoLancadaPeloLlmCaiParaTemplateComViaLlmFalse`, `respostaVaziaDoLlmCaiParaTemplateComViaLlmFalse`. Confirms the T7 log-content assertions added to the latter two tests are real and not tautological — they would catch a regression that deletes the WARN log |
| 3 | `CopyGenerationService.java:96` | Changed affiliate link query-param name `"?tag="` → `"?tagg="` in `montarLinkAfiliado` | ✅ Killed — 4 tests failed: `linkDeAfiliadoPresenteTantoNoCaminhoLlmQuantoNoTemplate`, `montarCopyTemplateContemTituloDePorPercentualELinkSemChamarLlmNovamente`, `textoQueExcederiaOLimiteEhTruncadoPreservandoOLinkIntegro`, `urlDiferenteDoLinkDeAfiliadoNoTextoDoLlmEhRemovidaESubstituida` |

**Sensor depth**: lightweight (standard-risk feature), 3 mutations, deliberately targeting the exact T7 fix under scrutiny (mutation 1) plus two control mutations on adjacent, previously-verified logic (mutations 2, 3) to confirm the sensor methodology itself and the rest of the suite are sound.

**Sensor outcome**: 2/3 killed, **1/3 survived** (mutation 1) → FAIL — surviving mutant confirms ENRICH-08 remains an open gap; a regression that deletes the discount%/de-por text-drawing code would ship undetected by the current test suite.

**Isolation**: Performed in `git worktree add ../bas-verify-scratch HEAD` (never `git stash`). Baseline `git status --porcelain` on the real tree was empty before sensor work; confirmed empty again after `git worktree remove --force ../bas-verify-scratch`. `git worktree list` afterward shows only the real tree.

---

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code | ✅ |
| Surgical changes | ✅ — T7 touched only the 2 test files it targeted |
| No scope creep | ✅ |
| Matches patterns | ✅ (`ListAppender`/logback log-capture pattern, pixel-sampling pattern both consistent with pre-existing tests in the same files) |
| Spec-anchored outcome check (asserted values match spec) | ⚠️ 15/16 — ENRICH-08's assertion targets the overlay *background*, not the spec-defined text content (percentual, preço de/por) |
| Per-layer Coverage Expectation met (domain 1:1 ACs; routes happy+edge+error) | ⚠️ Domain layer (`CopyGenerationService`) is 1:1 with ACs; `BannerImageService` happy-path test still doesn't verify its core visual claim after the fix attempt |
| Every test maps to a spec requirement - no unclaimed tests | ✅ — no unclaimed tests; the gap is under-coverage of an existing claim, not an orphan test |
| Documented guidelines followed: [file(s) or "none - strong defaults applied"] | none found (no `AGENTS.md`/coverage doc) — strong defaults applied, consistent with `scraping-coleta`/iteration-1 precedent |

**Adversarial scrutiny of the T7 fix (per reviewer brief)**:
- **`BannerImageServiceIT` overlay pixel check**: Concretely reasoned and empirically confirmed (Sensor #1) that `assertThat(gOverlay).isLessThan(gFundo - 80)` is satisfied by the `g.setColor(new Color(0,0,0,160)); g.fillRect(0, 680, 800, 120);` call alone. The coordinate `(750, 685)` is inside the fillRect's bounds (`x∈[0,800)`, `y∈[680,800)`) but is chosen specifically to avoid the drawn glyphs (which start at `x=20`) — which means it structurally cannot distinguish "bar drawn, no text" from "bar drawn, text drawn on top", because a translucent black pixel darkened only by the rectangle looks the same at that sampled point regardless of the text. The fix is not tautological in the sense of asserting something always true — it does correctly detect "some kind of overlay is present" (an improvement over dimensions-only) — but it does not close the specific ENRICH-08 claim about the discount%/de-por *text*, contrary to what `tasks.md` T7's "Done when" criterion and the commit message assert. Reusing the selo test's pixel technique was the right idea, but it was applied to a coordinate/property (dark-rectangle background) that isn't unique to the text-drawing code path; the selo test works because the selo's red fill color IS the entire visual claim, whereas here the fill is a backdrop and the text is the claim.
- **`CopyGenerationServiceTest` log-content additions**: Concretely reasoned and empirically confirmed (Sensor #2) that deleting the `log.warn(...)` call fails all 3 dependent tests, including the 2 new ones. Each asserted substring (`"rate limit"`, `"resposta vazia do LLM"`) is the literal exception message surfaced via `e.getMessage()` inside the log call — not a hardcoded string coincidentally matching the log. Sound, non-tautological fix.

---

## Edge Cases

- [x] Truncamento de legenda preservando link (ENRICH-15): Handled correctly — `CopyGenerationServiceTest.java:188-199`
- [x] LLM altera/remove o link de afiliado (ENRICH-16): Handled correctly — `CopyGenerationServiceTest.java:175-186`

---

## Gate Check

- **Gate command**: `mvn clean verify`
- **Gate outcome**: 65 passed, 0 failed, 0 skipped (BUILD SUCCESS)
  - Unit (surefire): 47 passed, 0 failed (`CopyGenerationServiceTest`: 8, `EnriquecimentoServiceTest`: 3, plus unrelated pre-existing suites)
  - Integration (failsafe): 18 passed, 0 failed (`BotAmazonSpringApplicationIT`: 1, `PriceHistoryRepositoryIT`: 4, `ProductRepositoryIT`: 3, `BannerImageServiceIT`: 5, plus unrelated pre-existing suites)
- **Test count before this feature** (per `scraping-coleta`'s closing state): 47 total (36 unit + 11 integration)
- **Test count after T1-T7**: 65 total (47 unit + 18 integration) — unchanged from iteration 1, as expected: T7 added assertions to 2 existing test methods, no new test methods
- **Delta**: +18 tests vs. pre-feature baseline
- **Skipped tests**: none
- **Failures**: none

---

## Fix Plans (if issues found)

### Fix 1 (carried over from iteration 1, still open): ENRICH-08 discount%/de-por overlay text content still not verified by test

- **Root cause**: `BannerImageServiceIT.downloadComSucessoEImagemValidaGeraBannerJpeg800x800` (`BannerImageServiceIT.java:99-126`) now pixel-samples `(750, 685)` to prove *some* overlay was drawn, but that coordinate falls inside the semi-transparent black `fillRect` background rectangle (`BannerImageService.java:124-125`) which is drawn unconditionally, independent of the two `drawString` calls that render the actual percentual and preço de/por text (`BannerImageService.java:128,130`). Empirically confirmed by Sensor #1: deleting both `drawString` calls leaves all 5 `BannerImageServiceIT` tests green.
- **Fix task**: Sample a pixel that is only white when the actual text glyphs are rendered — e.g., locate a coordinate known to fall inside a glyph stroke of `textoDesconto` or `textoDePor` at the fixed font/size/position used (`Font("SansSerif", Font.BOLD, 36)` at `(20, 730)`, or `Font("SansSerif", Font.PLAIN, 24)` at `(20, 770)`), and assert that pixel is near-white (e.g., all RGB channels > ~200), contrasting it against a nearby non-glyph point inside the same bar that stays dark. Because anti-aliased font rendering can vary slightly by JVM/OS, prefer sampling a broad known-solid stroke region (e.g., the vertical stroke of a digit) or scanning a small row/column range for the presence of any near-white pixel rather than a single exact coordinate, to avoid flakiness. Alternatively (simpler, less precise but robust): assert that the *maximum* brightness pixel within the text's bounding box exceeds a threshold, distinguishing "text drawn" from "bar only, no text" — since the sensor showed the latter case renders uniformly dark at every point.
- **Priority**: Major (core P1 MVP visual claim — the actual discount%/de-por text — still unverified by any automated test after 2 fix attempts)

---

## Requirement Traceability Update

Update spec.md requirement statuses:

| Requirement | Previous Status (iteration 1) | New Status (iteration 2) |
| --- | --- | --- |
| ENRICH-01 | ✅ Verified | ✅ Verified |
| ENRICH-02 | ✅ Verified | ✅ Verified |
| ENRICH-03 | ✅ Verified | ✅ Verified |
| ENRICH-04 | ✅ Verified (minor gap noted) | ✅ Verified (T7 closed the minor gap, confirmed by mutation sensor) |
| ENRICH-05 | ✅ Verified | ✅ Verified |
| ENRICH-06 | ✅ Verified | ✅ Verified |
| ENRICH-07 | ✅ Verified (per-product signal scope) | ✅ Verified (per-product signal scope) |
| ENRICH-08 | ❌ Needs Fix | ❌ **Still Needs Fix** — T7's attempted fix does not close the gap (see Fix 1 and Sensor #1) |
| ENRICH-09 | ✅ Verified | ✅ Verified |
| ENRICH-10 | ✅ Verified | ✅ Verified |
| ENRICH-11 | ✅ Verified | ✅ Verified |
| ENRICH-12 | ✅ Verified | ✅ Verified |
| ENRICH-13 | ✅ Verified | ✅ Verified |
| ENRICH-14 | ✅ Verified | ✅ Verified |
| ENRICH-15 | ✅ Verified | ✅ Verified |
| ENRICH-16 | ✅ Verified | ✅ Verified |

`spec.md`'s Requirement Traceability table is left unchanged (ENRICH-08 already shows ❌ Needs Fix from iteration 1) since the status has not changed — the fix attempt did not succeed.

---

## Summary

**Overall**: ❌ Not Ready — 1 gap still present after 1 fix attempt (T7), no new logic defects found

**Spec-anchored check**: 15/16 ACs matched spec outcome with precise, sensor-confirmed evidence; 1 gap (ENRICH-08) remains open
**Sensor**: 2/3 mutations killed, 1/3 survived (the survived mutation directly targets the T7 fix under review)
**Gate**: 65 passed, 0 failed, 0 skipped

**What works**: Everything verified in iteration 1 remains correct on independent re-derivation. T7's log-content fix for ENRICH-04's two previously-unasserted sub-cases (`excecaoLancadaPeloLlmCaiParaTemplateComViaLlmFalse`, `respostaVaziaDoLlmCaiParaTemplateComViaLlmFalse`) is sound and confirmed by mutation testing — this part of the fix is genuinely closed. LLM copy generation, fallback, link reinsertion/truncation, banner download/error-handling/temp-file lifecycle, the "menor preço" selo, and orchestration all remain correctly implemented and tested. Build gate green (65/65).

**Issues found**: T7's attempt to close ENRICH-08 added a pixel assertion to `BannerImageServiceIT.downloadComSucessoEImagemValidaGeraBannerJpeg800x800` that samples a point inside the overlay's translucent black background rectangle, not inside the discount%/de-por text glyphs themselves. The discrimination sensor proved this concretely: deleting both `drawString` calls for the discount percentage and de/por price (the actual spec-required content) leaves the test green, because the background `fillRect` alone darkens the sampled pixel. The core visual claim of ENRICH-08 — that the percentual and preço de/por are actually rendered onto the banner — remains unverified by any automated assertion, for the second iteration in a row.

**Next steps**: Route Fix 1 to an implementer: replace or supplement the current pixel sample with one that specifically targets rendered glyph pixels (near-white against the dark bar) rather than the background fill, per the concrete approach outlined in Fix 1. Re-run `mvn verify` and re-verify ENRICH-08 with a fresh discrimination-sensor mutation removing the `drawString` calls, confirming the new assertion fails in that scenario before marking the requirement Verified. This is iteration 2 of the fix→re-verify cycle; 1 iteration remains (max 3) before escalating to the user per the skill's guardrail.
