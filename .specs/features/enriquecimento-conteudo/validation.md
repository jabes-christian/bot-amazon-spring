# Enriquecimento de Conteúdo Validation

## Validation: enriquecimento-conteudo - FAIL ❌

**Date**: 2026-09-08
**Spec**: `.specs/features/enriquecimento-conteudo/spec.md`
**Diff range**: `a4bee92..HEAD` (branch `tasks/enriquecimento-conteudo`)
**Verifier**: independent sub-agent (author ≠ verifier)

---

## Task Completion

| Task | Status  | Notes |
| ---- | ------- | ----- |
| T1: Migration V2 (seed config) + extensão smoke test | ✅ Done | `V2__seed_enriquecimento_config.sql:1-4` inserts the 3 keys; `BotAmazonSpringApplicationIT.java:36,44` asserts count=7 and `enriquecimento.llm-timeout-segundos`='15' |
| T2: `findMenorPrecoDesde` | ✅ Done | `PriceHistoryRepository.java:20-21`; `PriceHistoryRepositoryIT.java:77-104` (2 new tests, window boundary both directions) |
| T3: `LlmConfig` (bean `ChatModel`) | ✅ Done | `LlmConfig.java:12-16` — no `.timeout(...)` in builder, defaults added per documented mid-execution correction (avoids breaking unrelated `@SpringBootTest`s) |
| T4: `CopyResultadoDTO` + `CopyGenerationService` | ✅ Done | `CopyGenerationService.java` full; 8 tests in `CopyGenerationServiceTest.java` (task promised 7, 8 delivered — extra granularity, not a shortfall) |
| T5: `pom.xml`(RestClient) + `BannerImageService` | ✅ Done, with 1 test-coverage gap | See AC table ENRICH-08 below — dimensions/format verified, discount%/de-por overlay content not asserted |
| T6: `ConteudoEnriquecidoDTO` + `EnriquecimentoService` | ✅ Done | `EnriquecimentoService.java` full; 3 tests in `EnriquecimentoServiceTest.java` |

All 6 tasks implemented and committed (`3b2d40a`..`359b5f8`). No blocked/partial tasks.

---

## Spec-Anchored Acceptance Criteria

| Requirement | Criterion (WHEN X THEN Y) | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- | --- |
| ENRICH-01 | WHEN produto candidato selecionado THEN solicita ao LLM copy pt-BR com nome, preço de/por, percentual, CTA | `viaLlm=true`, texto contém nome/preços/percentual/CTA | `CopyGenerationService.java:66-78` (`montarPrompt` builds prompt with `titulo`, `precoBase`, `precoAtual`, `percentualDesconto`, explicit CTA instruction) + `CopyGenerationServiceTest.java:77-92` `llmDentroDoTimeoutRetornaViaLlmTrueComTextoDoModelo` — `assertThat(resultado.viaLlm()).isTrue()`, `assertThat(resultado.texto()).contains(...)` for each element | ✅ PASS (minor: no `ArgumentCaptor` proves the *prompt sent* contains these fields — test's mock response happens to include them; `montarPrompt` source confirms it independently) |
| ENRICH-02 | THE sistema SHALL gerar a copy 1x por produto/evento, reutilizada para todos canais | `gerarCopy` called exactly once | `EnriquecimentoServiceTest.java:39-49` `enriquecerChamaGerarCopyEGerarBannerExatamenteUmaVezCada` — `verify(copyGenerationService, times(1)).gerarCopy(candidato)` | ✅ PASS |
| ENRICH-03 | THE sistema SHALL inserir o link de afiliado na copy final, substituindo qualquer link do LLM | link `produto.urlProduto + "?tag=" + tag` sempre presente | `CopyGenerationService.java:86-93` (`montarResultado` always rebuilds link) + `CopyGenerationServiceTest.java:152-165`/`167-178` | ✅ PASS |
| ENRICH-04 | IF LLM exceder timeout OU erro (rate limit/indisponível/vazio) THEN usa fallback + loga WARN com motivo | `viaLlm=false`, WARN log with reason | `CopyGenerationService.java:55-63` (single catch: `TimeoutException\|ExecutionException\|InterruptedException\|IllegalStateException`) + `CopyGenerationServiceTest.java:94-110` `timeoutExcedidoCaiParaTemplateComViaLlmFalseELogaWarn` (asserts WARN log content) | ✅ PASS — but only the timeout variant (:94-110) asserts the log line; `excecaoLancadaPeloLlmCaiParaTemplateComViaLlmFalse` (:112-122) and `respostaVaziaDoLlmCaiParaTemplateComViaLlmFalse` (:124-134) assert only `viaLlm=false`, not the WARN log, even though spec requires WARN for all 3 sub-cases. Same catch block/log statement handles all 3 (code-verified), so not a functional gap, but a minor test-assertion gap |
| ENRICH-05 | IF LLM falhar THEN monta copy com template fixo (título+de/por+percentual+link), sem chamar LLM de novo | template built without re-invoking LLM | `CopyGenerationService.java:80-84` `montarCopyTemplate` + `CopyGenerationServiceTest.java:136-150` `montarCopyTemplateContemTituloDePorPercentualELinkSemChamarLlmNovamente` — `verify(chatModel, times(1)).chat(anyString())` | ✅ PASS |
| ENRICH-06 | THE sistema SHALL garantir 100% dos disparos com link de afiliado, LLM ou template | link present both paths | `CopyGenerationServiceTest.java:152-165` `linkDeAfiliadoPresenteTantoNoCaminhoLlmQuantoNoTemplate` | ✅ PASS |
| ENRICH-07 (partial per-product signal, per design) | THE sistema SHALL registrar quantos produtos usaram LLM vs template (per-cycle tally is `canais-disparo`'s job; here only the per-product signal is in scope) | `CopyResultadoDTO.viaLlm()` / `ConteudoEnriquecidoDTO.copyViaLlm()` set correctly per product | `CopyGenerationServiceTest.java` (all 8 tests assert `viaLlm` true/false correctly) + `EnriquecimentoServiceTest.java:51-62`,`:64-76` (propagation to `copyViaLlm()`) | ✅ PASS (scope matches design.md's documented split) |
| ENRICH-08 | WHEN produto selecionado E imagem baixada com sucesso THEN compõe banner sobrepondo percentual e preço de(riscado)/por(atual) | banner file with dimensions/format contract **and** discount%/preço de-por actually overlaid | `BannerImageService.java:112-144` (`compor` draws `textoDesconto`/`textoDePor`) + `BannerImageServiceIT.java:99-116` `downloadComSucessoEImagemValidaGeraBannerJpeg800x800` — asserts `isPresent()`, 800×800, `.jpg` extension **only** | ❌ GAP — no assertion (pixel sample, OCR, or otherwise) verifies the discount%/de-por text was actually rendered onto the canvas; only the container (dimensions/format) is checked. The sibling selo test (`:153-175`) *does* pixel-sample its overlay, proving this technique was available and simply not applied to the primary discount/price overlay. Implementation reads correct on code inspection, but per evidence-or-zero this specific claim is uncovered |
| ENRICH-09 | THE sistema SHALL gerar o banner 1x por produto, reutilizado para todos canais | `gerarBanner` called exactly once | `EnriquecimentoServiceTest.java:39-49` — `verify(bannerImageService, times(1)).gerarBanner(candidato)` | ✅ PASS |
| ENRICH-10 | IF download falhar (timeout/URL inválida/erro HTTP) OU conteúdo não é imagem válida THEN `Optional.empty()` + WARN com ASIN e motivo | `Optional.empty()`, WARN log w/ ASIN+reason | `BannerImageService.java:52-58`,`:60-66` + `BannerImageServiceIT.java:118-128` (HTTP 500), `:130-141` (non-image content) | ✅ PASS — real-socket-timeout sub-case not simulated (self-documented, justified gap in `tasks.md` T5: `MockRestServiceServer` is synchronous; same `catch(RestClientException)` covers `ResourceAccessException`/HTTP errors alike) |
| ENRICH-11 | THE sistema SHALL armazenar banner em dir temp e remover após disparo concluído em todos canais | temp file created; `removerBanner` deletes it | `BannerImageService.java:71` (`Files.createTempFile`), `:83-90` (`removerBanner`) + `BannerImageServiceIT.java:143-151` `removerBannerApagaOArquivo` | ✅ PASS — the *timing* of calling `removerBanner` (after all channels done) is `canais-disparo`'s contract responsibility per `design.md` Integration Points #4, correctly out of scope here |
| ENRICH-12 | IF produto marcado texto puro THEN Telegram sinaliza `sendMessage`, WhatsApp sinaliza texto sem mídia | `ConteudoEnriquecidoDTO.bannerPath()==null` signals text-only mode | `EnriquecimentoServiceTest.java:64-76` `bannerAusenteResultaEmBannerPathNuloPreservandoACopyCompleta` — `assertThat(resultado.bannerPath()).isNull()` | ✅ PASS (signal-production scope only; channel-level consumption is `canais-disparo`, out of scope) |
| ENRICH-13 | THE sistema SHALL preservar no texto modo-texto-puro todos elementos da copy | copy content unaffected by banner absence | `EnriquecimentoServiceTest.java:64-76` — `assertThat(resultado.copy()).isEqualTo("copy completa sem banner")` | ✅ PASS |
| ENRICH-14 | WHERE preço atual for o menor valor dos últimos N dias THEN inclui selo "MENOR PREÇO EM N DIAS" | selo drawn (visually, pixel-verifiable) | `BannerImageService.java:103-110`,`:132-139` + `BannerImageServiceIT.java:153-175` `precoAtualIgualAoMenorDosUltimosDiasIncluiSeloNoBanner` (samples pixel RGB in badge region, confirms red) + `PriceHistoryRepositoryIT.java:77-104` (query-level window correctness) | ✅ PASS |
| ENRICH-15 (edge case) | IF texto exceder 1024 chars THEN trunca preservando link íntegro no final | `texto.length() <= limite`, `endsWith(link)` | `CopyGenerationService.java:99-105` `truncarCorpo` + `CopyGenerationServiceTest.java:180-191` `textoQueExcederiaOLimiteEhTruncadoPreservandoOLinkIntegro` | ✅ PASS |
| ENRICH-16 (edge case) | IF LLM retornar conteúdo que remove/altera o link THEN reinsere o link correto | LLM's own URL stripped, correct link present | `CopyGenerationService.java:86-88` (regex strip + rebuild) + `CopyGenerationServiceTest.java:167-178` `urlDiferenteDoLinkDeAfiliadoNoTextoDoLlmEhRemovidaESubstituida` | ✅ PASS |

**Status**: ❌ 1 gap present (ENRICH-08 — test-coverage gap, not a logic defect) / 15 of 16 requirements fully covered with precise evidence

---

## Discrimination Sensor

| Mutation | File:line | Description | Killed? |
| -------- | --------- | ------------ | ------- |
| 1 | `src/main/java/.../service/CopyGenerationService.java:91` | Removed affiliate-link reinsertion (`textoFinal = corpoTruncado.isBlank() ? link : corpoTruncado + SEPARADOR_LINK + link;` → `textoFinal = corpoTruncado;`) | ✅ Killed (4 tests failed: `linkDeAfiliadoPresenteTantoNoCaminhoLlmQuantoNoTemplate`, `montarCopyTemplateContemTituloDePorPercentualELinkSemChamarLlmNovamente`, `textoQueExcederiaOLimiteEhTruncadoPreservandoOLinkIntegro`, `urlDiferenteDoLinkDeAfiliadoNoTextoDoLlmEhRemovidaESubstituida`) |
| 2 | `src/main/java/.../service/BannerImageService.java:108` | Flipped selo comparison (`compareTo(menorPreco) <= 0` → `> 0`) | ✅ Killed (`BannerImageServiceIT.precoAtualIgualAoMenorDosUltimosDiasIncluiSeloNoBanner` failed) |
| 3 | `src/main/java/.../service/EnriquecimentoService.java:19` | Broke banner-absent null signal (`bannerPath.orElse(null)` → `bannerPath.orElse(Path.of("dummy-fallback.jpg"))`) | ✅ Killed (`EnriquecimentoServiceTest.bannerAusenteResultaEmBannerPathNuloPreservandoACopyCompleta` failed) |

**Sensor depth**: lightweight (standard-risk feature)
**Sensor outcome**: 3/3 killed (isolated per mutation — sensor itself is sound, but see the overall verdict above for the feature-level finding)

**Isolation**: Performed in `git worktree add <scratch> HEAD` (never `git stash`). Real-tree `git status --porcelain` was empty before sensor work and confirmed empty (matching baseline) after `git worktree remove --force`.

---

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code | ✅ |
| Surgical changes | ✅ |
| No scope creep | ✅ (doc updates to `STATE.md`/`design.md`/`tasks.md`/`scraping-coleta/tasks.md` are bookkeeping, not code scope creep) |
| Matches patterns | ✅ (`@Slf4j`, `ConfigService.getInt/getLong`, `@RequiredArgsConstructor`, `*IT.java` convention all consistent with `scraping-coleta`) |
| Spec-anchored outcome check (asserted values match spec) | ✅ mostly — precise values (`25.00%`, `899.00`, `1199.00`, exact link string) asserted throughout; 1 gap (ENRICH-08, see above) |
| Per-layer Coverage Expectation met (domain 1:1 ACs; routes happy+edge+error) | ⚠️ Domain layer (`CopyGenerationService`) is 1:1 with ACs incl. edge cases; `BannerImageService` happy-path test doesn't verify its most visually essential claim (overlay content) |
| Every test maps to a spec requirement - no unclaimed tests | ✅ — every test method traced above; none exist without a corresponding AC/Done-when |
| Documented guidelines followed: [file(s) or "none - strong defaults applied"] | none found (no `AGENTS.md`/coverage doc) — strong defaults applied, consistent with `scraping-coleta` precedent |

**Design-decision scrutiny (per reviewer brief)**:
- The long-lived (never-closed) `ExecutorService` field in `CopyGenerationService` (`llmExecutor`, line 31): reasoning holds up. `ExecutorService.close()` (JDK 21+ `AutoCloseable`) calls `shutdown()` then blocks the caller until in-flight tasks finish — using try-with-resources per call would defeat the very timeout ENRICH-04 requires. Virtual threads are daemon by JVM definition, so no shutdown-blocking risk. Confirmed by reading `java.util.concurrent.ExecutorService` semantics; no flaw found in this design choice.
- `chatAsync()` absence in `langchain4j-core:1.19.0` and the resulting fallback to `supplyAsync`+virtual-thread+`.cancel(true)`: consistent with what the design.md documents finding in the actual jar; `CopyGenerationServiceTest.timeoutExcedidoCaiParaTemplateComViaLlmFalseELogaWarn` (using `Thread.sleep(3000)` inside the mocked `chat()` call with a 1s timeout) empirically demonstrates the timeout enforcement works as intended at the caller side, regardless of whether the underlying (mocked) call is truly aborted.

---

## Edge Cases

- [x] Truncamento de legenda preservando link (ENRICH-15): Handled correctly — `CopyGenerationServiceTest.java:180-191`
- [x] LLM altera/remove o link de afiliado (ENRICH-16): Handled correctly — `CopyGenerationServiceTest.java:167-178`

---

## Gate Check

- **Gate command**: `mvn clean verify`
- **Gate outcome**: 65 passed, 0 failed, 0 skipped (BUILD SUCCESS)
  - Unit (surefire): 47 passed, 0 failed (includes `CopyGenerationServiceTest`: 8, `EnriquecimentoServiceTest`: 3)
  - Integration (failsafe): 18 passed, 0 failed (includes `BotAmazonSpringApplicationIT`: 1, `PriceHistoryRepositoryIT`: 4, `BannerImageServiceIT`: 5)
- **Test count before feature** (per `scraping-coleta`'s closing STATE.md/validation.md): 47 total (36 unit + 11 integration)
- **Test count after feature**: 65 total (47 unit + 18 integration)
- **Delta**: +18 new tests (+8 `CopyGenerationServiceTest`, +3 `EnriquecimentoServiceTest`, +5 `BannerImageServiceIT`, +2 `PriceHistoryRepositoryIT`; `BotAmazonSpringApplicationIT` gained 2 new assertions on its 1 existing test, not a new test method)
- **Skipped tests**: none
- **Failures**: none

---

## Fix Plans (if issues found)

### Fix 1: ENRICH-08 discount%/de-por overlay content not verified by test

- **Root cause**: `BannerImageServiceIT.downloadComSucessoEImagemValidaGeraBannerJpeg800x800` (`BannerImageServiceIT.java:99-116`) only asserts the banner's presence, dimensions (800×800), and file extension (`.jpg`) — it never samples pixels or otherwise confirms the discount percentage and "de/por" price text (`BannerImageService.java:121-130`) were actually drawn onto the canvas. The sibling selo test (`:153-175`) proves pixel-sampling is a viable technique in this test class (it samples RGB at a fixed coordinate to confirm the red selo badge renders) — the same technique was simply not applied to the primary overlay.
- **Fix task**: Add a pixel-sample (or region-based) assertion to the happy-path banner test confirming the dark overlay bar (semi-transparent black rectangle, `y = 680..800`) is present at its expected coordinates, distinguishing it from the raw product-photo background — analogous to the existing selo pixel check. A full OCR-based text assertion is not required; a structural/color-based check proving the overlay bar (and thus the drawn text region) exists is sufficient to close the evidence gap.
- **Priority**: Major (core P1 MVP visual claim currently unverified by any automated test, though code inspection shows correct implementation)

---

## Requirement Traceability Update

Update spec.md requirement statuses:

| Requirement | Previous Status | New Status |
| --- | --- | --- |
| ENRICH-01 | Pending | ✅ Verified |
| ENRICH-02 | Pending | ✅ Verified |
| ENRICH-03 | Pending | ✅ Verified |
| ENRICH-04 | Pending | ✅ Verified |
| ENRICH-05 | Pending | ✅ Verified |
| ENRICH-06 | Pending | ✅ Verified |
| ENRICH-07 | Pending | ✅ Verified (per-product signal scope, per design.md split with `canais-disparo`) |
| ENRICH-08 | Pending | ❌ Needs Fix (test-coverage gap, see Fix 1) |
| ENRICH-09 | Pending | ✅ Verified |
| ENRICH-10 | Pending | ✅ Verified |
| ENRICH-11 | Pending | ✅ Verified |
| ENRICH-12 | Pending | ✅ Verified |
| ENRICH-13 | Pending | ✅ Verified |
| ENRICH-14 | Pending | ✅ Verified |
| ENRICH-15 | Pending | ✅ Verified |
| ENRICH-16 | Pending | ✅ Verified |

---

## Summary

**Overall**: ⚠️ Issues — 1 test-coverage gap (ENRICH-08), no logic defects found

**Spec-anchored check**: 15/16 ACs matched spec outcome with precise evidence; 1 gap (ENRICH-08)
**Sensor**: 3/3 mutations killed
**Gate**: 65 passed, 0 failed, 0 skipped

**What works**: LLM copy generation with correct timeout/fallback/link-reinsertion/truncation behavior (ENRICH-01..07, 15, 16) — all precisely tested. Banner download/error-handling/temp-file lifecycle and the "menor preço" selo (ENRICH-09..14) — precisely tested, selo pixel-verified. Orchestration (`EnriquecimentoService`) correctly calls both sub-services exactly once and propagates the text-only signal (ENRICH-02, 09, 12, 13). Build gate green (65/65), discrimination sensor 3/3 killed, real worktree untouched by sensor mutations.

**Issues found**: ENRICH-08's core visual claim (discount% and de/por price overlaid on the product photo) is implemented correctly on code inspection but has zero automated assertion proving it renders — the happy-path test only checks banner dimensions/format. Fix: add a pixel/region-based assertion (test-only change, no source logic change needed) to `BannerImageServiceIT.downloadComSucessoEImagemValidaGeraBannerJpeg800x800`, following the same technique already used for the selo pixel check.

**Next steps**: Route Fix 1 to an implementer (test-only change); re-run `mvn verify` and re-verify ENRICH-08. This is iteration 1 of the fix→re-verify cycle (max 3 before escalating to user).
