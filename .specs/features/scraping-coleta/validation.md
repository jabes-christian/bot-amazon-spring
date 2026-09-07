# Scraping & Coleta Validation

**Date**: 2026-09-07
**Spec**: `.specs/features/scraping-coleta/spec.md`
**Diff range**: `7b10c43..HEAD` (`b1bfbb1`..`d37b5b0`, 12 commits)
**Verifier**: independent sub-agent (author ≠ verifier)

---

## Task Completion

| Task | Status  | Notes |
| ---- | ------- | ----- |
| T1   | ✅ Done | Flyway+Testcontainers+migration; `BotAmazonSpringApplicationIT` confirmed live (4 tables, 5 categorias, 4 app_config rows) |
| T2   | ✅ Done | `AppConfig` entity+repository, 3 integration tests |
| T3   | ✅ Done | `ConfigService`, 6 unit tests |
| T4   | ✅ Done | `CategoriaColeta` entity+repository, 2 integration tests |
| T5   | ✅ Done | `Product` entity+repository, 3 integration tests |
| T6   | ✅ Done | `PriceHistory` entity+repository, 2 integration tests |
| T7   | ✅ Done | `SeleniumConfig`, no dedicated test (documented, indirectly exercised by full context boot) |
| T8   | ✅ Done | `BaseScraper`, 6 unit tests |
| T9   | ✅ Done | `ScrapedProductDTO`, 4 unit tests |
| T10  | ✅ Done | `AmazonSelectorsProperties`+`AmazonProductScraper`, 4 unit tests |
| T11  | ✅ Done | `ColetaService`, 8 unit tests |
| T12  | ✅ Done | `CandidatoPromocaoDTO`+`PromotionDetectionService`, 6 unit tests |

All 12 tasks marked `✅ Complete` in `tasks.md`. No partial/blocked tasks.

---

## Spec-Anchored Acceptance Criteria

> SCRAPE-NN IDs re-derived independently from `spec.md`'s own AC bullet order per story (spec.md's Requirement Traceability table only binds IDs at story level, not per-bullet — the exact per-bullet numbering is therefore a judgment call; `tasks.md`'s inline tags swap SCRAPE-11/SCRAPE-12 relative to strict bullet order, a cosmetic labeling difference that does not change what was tested).

### P1: Coleta de produtos por categoria configurável

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-01: scheduler invoca → busca 1ª página por keyword de cada categoria ativa | `buscarPorKeyword` chamado com a keyword de cada categoria retornada por `findByAtivoTrue()` | `src/test/java/.../service/ColetaServiceTest.java:83` — `verify(amazonProductScraper).buscarPorKeyword("monitor gamer");` | ✅ PASS |
| SCRAPE-02: extrair ASIN, título, preço atual, preço riscado, imagem, URL | Todos os 6 campos populados corretamente a partir do card | `src/test/java/.../scraper/AmazonProductScraperTest.java:86-91` — asserts em todos os 6 campos do `ScrapedProductDTO` | ✅ PASS |
| SCRAPE-03: categoria sem produto → WARN identificando categoria+keyword, continua | (a) processamento continua para as demais categorias; (b) log WARN identifica categoria e keyword | (a) `ColetaServiceTest.java:99` — `verify(productRepository).save(...)` para a 2ª categoria ✅; (b) **nenhum teste usa `ListAppender` para esta mensagem** — zero citação | ❌ GAP — ver achado #1 abaixo: o caminho WARN é código morto em produção |
| SCRAPE-04: preço ≤R$0 ou >R$50.000 → descarta + WARN com ASIN+valor bruto | (a) produto descartado, não persistido; (b) log WARN com ASIN+preço bruto | (a) `ColetaServiceTest.java:113-115` — `verify(productRepository, times(1)).save(any())` + `never()).findByAsin("B0INVALIDO")` ✅; (b) sem asserção de conteúdo de log — zero citação | ⚠️ Parcial — comportamento de descarte comprovado; conteúdo do log não verificado |
| SCRAPE-05: ASIN existente → atualiza, não duplica | `save` chamado com o mesmo `id`, título/preço atualizados | `ColetaServiceTest.java:135-139` — `argThat(p -> p.getId().equals(42L) && p.getTitulo().equals("Titulo Novo") && p.getPrecoAtual()...isEqualTo("90.00"))` | ✅ PASS |
| SCRAPE-06: aguarda intervalo configurável entre categorias | Intervalo lido via `ConfigService` com a chave/-default corretos | `ColetaServiceTest.java:165` — `verify(configService).getLong(CHAVE_INTERVALO_CATEGORIAS, 5L)` | ✅ PASS |

### P1: Histórico de preço por produto

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-07: cada produto processado grava histórico (ASIN+preço+timestamp) | Uma entrada de `PriceHistory` por produto processado, com o preço extraído | `ColetaServiceTest.java:153` — `verify(priceHistoryRepository, times(2)).save(any())` | ⚠️ Spec-precision gap — só conta invocações, não verifica o conteúdo salvo (preço/ASIN) via `argThat` |
| SCRAPE-08: histórico mantido indefinidamente, sem expiração automática | Ausência de qualquer job/lógica de purge | Requisito negativo — nenhum código de expiração existe (confirmado por leitura de todo o diff); não há teste positivo possível para "ausência de feature" | ⚠️ Spec-precision gap — satisfeito por omissão, não testável diretamente |
| SCRAPE-09: 2+ coletas em dias diferentes → 1 entrada por coleta, nunca dedup por dia | `countByProduct` == 2 após 2 inserts distintos para o mesmo produto | `src/test/java/.../repository/PriceHistoryRepositoryIT.java:54-60` — `assertThat(total).isEqualTo(2)` | ✅ PASS |

### P1: Detecção de queda de preço relevante

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-10: ≥2 entradas de histórico → base = menor preço do histórico | preço atual 80, base 100 (mín. histórico), 10% mínimo → candidato com 20.00% | `src/test/java/.../service/PromotionDetectionServiceTest.java:64-65` — `assertThat(candidatos).hasSize(1)` + `percentualDesconto()).isEqualByComparingTo("20.00")` | ✅ PASS |
| SCRAPE-11: 10% padrão, configurável sem alteração de código | Alterar o percentual mínimo (20%) muda o resultado (85 vs 100 = 15% < 20% → não sinaliza) | `PromotionDetectionServiceTest.java:78` — `assertThat(candidatos).isEmpty()` (configurabilidade) ✅; **valor-padrão 10% especificamente não é lido de volta e verificado em nenhum teste** (migration semeia `'10'`, mas nenhum teste consulta esse valor) | ⚠️ Parcial — configurabilidade comprovada; o "10% é o default" não tem citação própria |
| SCRAPE-12: sinalizado → disponibilizado como candidato (contrato com canais-disparo) | Retorna `CandidatoPromocaoDTO` com produto+percentual corretos | `PromotionDetectionServiceTest.java:92-96` — `candidatos.get(0).produto()).isSameAs(produto)`, `percentualDesconto()).isEqualByComparingTo("30.00")` | ✅ PASS |
| SCRAPE-13: preço não caiu desde o último candidato → não sinaliza de novo | `lastCandidatoPreco == precoAtual` → lista vazia, `save` nunca chamado | `PromotionDetectionServiceTest.java:109-110` — `assertThat(candidatos).isEmpty()` + `verify(productRepository, never()).save(any())` — **confirmado pelo sensor de discriminação (mutação 1)** | ✅ PASS |

### P2: Fallback de detecção em cold start

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-14: <2 entradas + preço riscado presente → base = preço riscado | count=1, riscado=100, atual=80 → candidato 20.00%, `findMenorPrecoByProduct` nunca chamado | `PromotionDetectionServiceTest.java:123-125` — `assertThat(...).hasSize(1)`, `percentualDesconto()...isEqualByComparingTo("20.00")`, `verify(priceHistoryRepository, never()).findMenorPrecoByProduct(any())` | ✅ PASS |
| SCRAPE-15: <2 entradas + sem preço riscado → não sinaliza | count=0, riscado=null → lista vazia, `save` nunca chamado | `PromotionDetectionServiceTest.java:136-137` — `assertThat(candidatos).isEmpty()` + `verify(productRepository, never()).save(any())` | ✅ PASS |

### Edge Cases

| Criterion | Spec-defined outcome | `file:line` + assertion | Result |
| --- | --- | --- | --- |
| SCRAPE-16: falha WebDriver / CAPTCHA → ERROR log com categoria, continua as demais | (a) exceção isolada por categoria, continua para a próxima; (b) ERROR loga a categoria afetada | (a) `ColetaServiceTest.java:181` — `verify(productRepository).save(...)` para a 2ª categoria após exceção na 1ª ✅; (b) sem asserção de conteúdo do log ERROR (observado apenas como saída de console durante o gate, não afirmado por `ListAppender`) | ⚠️ Parcial — comportamento de isolamento comprovado; conteúdo do log ERROR não verificado |
| SCRAPE-17: zero produtos no ciclo inteiro → WARN, não fatal | WARN com a mensagem certa + não lança exceção | `ColetaServiceTest.java:195,197-198` — `assertDoesNotThrow(...)` + `assertThat(appender.list).anyMatch(evento -> evento.getLevel()==Level.WARN && ...contains("ciclo inteiro extraiu zero produtos"))` | ✅ PASS — teste mais preciso de todo o conjunto |

**Status**: ❌ Gaps presentes (SCRAPE-03 é um gap de comportamento real, não apenas de teste — ver Achado #1). Demais critérios majoritariamente cobertos, com 4 gaps de precisão de asserção (log-content) e 1 gap de precisão de spec (SCRAPE-11 default).

---

### Achado #1 (o mais relevante): SCRAPE-03 é código morto em produção

`AmazonProductScraper.buscarPorKeyword` (`src/main/java/com/jchristian/bot_amazon_spring/scraper/AmazonProductScraper.java:34`) obtém os cards via `aguardarElementos(By.cssSelector(...))`, que por sua vez (`src/main/java/com/jchristian/bot_amazon_spring/scraper/base/BaseScraper.java:28`) é:

```java
protected List<WebElement> aguardarElementos(By locator) {
    return wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(locator));
}
```

Decompilação confirmada de `selenium-support-4.43.0.jar` (`ExpectedConditions$11.apply`): essa condição **retorna `null` quando a lista de elementos está vazia** (nunca retorna uma lista vazia). `WebDriverWait.until(...)` continua tentando até o timeout e então **lança `TimeoutException`** — nunca retorna uma lista vazia ao chamador.

Consequência: em produção, uma categoria que realmente não retorna produtos (SCRAPE-03) **não pode fazer `buscarPorKeyword` retornar `List.of()`** — ela sempre lança `TimeoutException`, que se propaga até `ColetaService.executarCicloColeta` (`ColetaService.java:44-48`) e é tratada pelo `catch (Exception e)` do isolamento **por categoria** (o mesmo caminho de SCRAPE-16), gerando `log.error("COLETA: falha ao processar categoria ...")` em vez do `log.warn("COLETA: categoria sem resultado ...")` esperado em `ColetaService.java:62-65`.

O branch `if (produtosEncontrados.isEmpty())` (linha 62) está, portanto, morto para o caminho real do `AmazonProductScraper` — só é exercitado em `ColetaServiceTest.categoriaSemResultadoNaoInterrompeCicloESeguiParaProxima` (linha 90) porque o teste mocka `AmazonProductScraper.buscarPorKeyword` diretamente para retornar `List.of()`, contornando a semântica real do Selenium. Nenhum teste em `AmazonProductScraperTest` exercita o cenário "zero cards encontrados" através do `WebDriver`/`WebDriverWait` reais (mockados), então a lacuna não aparece em nenhum ponto do conjunto de testes atual.

**Impacto operacional**: o sinal distinto que a spec pede (WARN benigno para "sem resultado" vs. ERROR para falha real) se perde — todo cenário de zero-cards vira ERROR "falha ao processar categoria", inclusive buscas legitimamente sem resultado. Isso não quebra o requisito "não interrompe o ciclo" (SCRAPE-01/16 continuam corretos), mas quebra a granularidade operacional de SCRAPE-03 tal como especificada.

**Sugestão de direção de correção** (não implementada — Verifier não escreve código de correção): capturar `TimeoutException` dentro de `AmazonProductScraper.buscarPorKeyword` (ou em `ColetaService.processarCategoria`, distinguindo-a de outras exceções) e tratá-la como "zero resultados" (retornar lista vazia / logar WARN), deixando outras exceções (falha de navegação, driver morto) escalarem para o ERROR de SCRAPE-16.

---

## Discrimination Sensor

Isolamento: `git worktree add C:/swt-scraping-coleta HEAD` (caminho curto usado porque o caminho padrão do scratchpad excedeu o limite de nome de arquivo do Windows). Baseline `git status --porcelain` da árvore real: vazio (antes e depois do sensor).

| Mutation | File:line | Description | Killed? |
| --- | --- | --- | --- |
| 1 | `service/PromotionDetectionService.java:42` | `if (quedaRelevante && !jaSinalizadoNessePreco)` → `if (quedaRelevante \|\| !jaSinalizadoNessePreco)` | ✅ Killed — `PromotionDetectionServiceTest.percentualMinimoConfiguravelGovernaOLimiarDeDeteccao` e `.produtoJaSinalizadoNoMesmoPrecoNaoESinalizadoNovamente` falharam |
| 2 | `service/ColetaService.java:78` | `compareTo(precoMinimo) < 0 \|\| compareTo(precoMaximo) > 0` → trocado `\|\|` por `&&` (desativa a checagem de sanidade de preço alto) | ✅ Killed — `ColetaServiceTest.precoForaDaFaixaDeSanidadeDescartaProdutoEContinua` falhou (`save` chamado 2x em vez de 1x) |
| 3 | `scraper/AmazonProductScraper.java:53` | `if (precoAtual == null) { return null; }` → `if (precoAtual != null) { return null; }` (inverte o filtro de preço nulo) | ✅ Killed — `AmazonProductScraperTest.buscarPorKeywordExtraiTodosOsCamposDeUmCardValido` falhou (0 produtos em vez de 1); `cardSemPrecoAtualEDescartado` também falhou (NPE) |

**Sensor depth**: lightweight (3 mutações, feature não é P0/pagamento/auth)
**Result**: 3/3 killed - PASS ✅

Worktree removido (`git worktree remove --force`); `git status --porcelain` da árvore real confirmado idêntico ao baseline (vazio) após a limpeza.

---

## Code Quality

| Principle | Status |
| --- | --- |
| Minimum code | ✅ — nenhum código além do especificado nas 12 tasks |
| Surgical changes | ✅ — diff restrito aos arquivos da feature + `tasks.md`/`STATE.md` |
| No scope creep | ✅ — `ConfigService`/`SeleniumConfig` genéricos são decisões de design documentadas (AD-009), não flexibilidade não solicitada |
| Matches patterns | ✅ — Lombok, `JpaRepository`, DTO-record-com-factory consistentes em todo o código |
| Spec-anchored outcome check (asserted values match spec) | ⚠️ — maioria PASS; 4 critérios com asserção de log-content ausente (SCRAPE-03/04/16) e 1 com asserção de conteúdo salvo ausente (SCRAPE-07/09), ver tabela acima |
| Per-layer Coverage Expectation met (domain 1:1 ACs; routes happy+edge+error) | ✅ — sem camada de rota nesta feature (headless); domínio com 1:1 razoável às ACs, com as ressalvas acima |
| Every test maps to a spec requirement - no unclaimed tests | ✅ — todos os testes lidos mapeiam a um AC, edge case ou Done-when de alguma task |
| Documented guidelines followed: [file(s) or "none - strong defaults applied"] | none — `tasks.md`'s própria Test Coverage Matrix confirma ausência de guideline formal no repo; defaults fortes aplicados de forma consistente |

---

## Edge Cases

- [x] WebDriver falha ao carregar página de busca: isolado por categoria, ciclo continua (`ColetaServiceTest.java:168-182`) — comportamento ✅, conteúdo do log ERROR não verificado
- [⚠️] Amazon retorna CAPTCHA/bloqueio: tratado pela mesma via de exceção genérica (comportamento correto por acidente da semântica do Selenium — ver Achado #1), mas indistinguível de "zero resultados legítimo" (SCRAPE-03) nos logs
- [x] Zero produtos extraídos no ciclo inteiro: WARN não fatal, testado com precisão via `ListAppender` (`ColetaServiceTest.java:184-202`)

---

## Gate Check

- **Gate command**: `mvn clean verify` (via wrapper `C:\Users\jmartinsc\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`)
- **Result**: 45 passed (34 unit via Surefire + 11 integration via Failsafe/Testcontainers Postgres), 0 failed, 0 skipped. `BUILD SUCCESS`.
- **Test count before feature**: 1 (placeholder `BotAmazonSpringApplicationTests.contextLoads()`, sem asserts de negócio)
- **Test count after feature**: 45
- **Delta**: +44 new tests
- **Skipped tests**: none
- **Failures**: none

---

## Fix Plans (if issues found)

### Fix 1: SCRAPE-03 ("categoria sem resultado" → WARN) é inatingível em produção

- **Root cause**: `BaseScraper.aguardarElementos` usa `ExpectedConditions.presenceOfAllElementsLocatedBy`, que lança `TimeoutException` em vez de retornar lista vazia quando zero elementos são encontrados (confirmado via decompilação de `selenium-support-4.43.0.jar`). Isso faz `AmazonProductScraper.buscarPorKeyword` nunca retornar `List.of()` na prática — sempre lança exceção, que cai no isolamento genérico por categoria (`ColetaService.java:44-48`) e vira ERROR, não o WARN específico de `ColetaService.java:62-65`.
- **Fix task**: Capturar `TimeoutException` dentro de `AmazonProductScraper.buscarPorKeyword` (ou em ponto equivalente) e tratá-la como "zero resultados" (retornar lista vazia), preservando a escalada a ERROR para outras exceções (falha de navegação/driver). Adicionar um teste em `AmazonProductScraperTest` que mocke `driver.findElements(...)` retornando sempre `List.of()` e confirme que `buscarPorKeyword` retorna lista vazia sem lançar.
- **Priority**: Major (comportamento operacional divergente da spec; não quebra o "não interrompe o ciclo", mas quebra a distinção WARN/ERROR pedida)

### Fix 2: Asserções de conteúdo de log ausentes (SCRAPE-04, SCRAPE-16) e de conteúdo salvo (SCRAPE-07/09)

- **Root cause**: os testes cobrem o comportamento (descarte, isolamento, contagem de saves) mas não usam `ListAppender`/`argThat` para confirmar o texto do log ou os campos persistidos, ao contrário do padrão já usado com sucesso em `cicloComZeroProdutosExtraidosLogaWarnENaoLancaExcecao` (SCRAPE-17).
- **Fix task**: Adicionar asserções de conteúdo (via `ListAppender` para os logs de WARN/ERROR com ASIN/categoria/keyword; via `argThat` para o `PriceHistory` salvo) nos testes já existentes, seguindo o padrão de SCRAPE-17.
- **Priority**: Minor (gap de precisão de teste, não de comportamento)

### Fix 3: SCRAPE-11 — valor-padrão "10%" nunca é lido de volta e verificado

- **Root cause**: `BotAmazonSpringApplicationIT` conta linhas de `app_config` (4) mas não verifica o valor de `coleta.percentual-minimo-queda`.
- **Fix task**: Adicionar uma asserção de valor (ex.: `SELECT valor FROM app_config WHERE chave = 'coleta.percentual-minimo-queda'` deve ser `'10'`) em `BotAmazonSpringApplicationIT` ou em um teste de integração dedicado.
- **Priority**: Minor

---

## Requirement Traceability Update

| Requirement | Previous Status | New Status |
| --- | --- | --- |
| SCRAPE-01 | Pending | ✅ Verified |
| SCRAPE-02 | Pending | ✅ Verified |
| SCRAPE-03 | Pending | ❌ Needs Fix |
| SCRAPE-04 | Pending | ⚠️ Verified (test-precision gap) |
| SCRAPE-05 | Pending | ✅ Verified |
| SCRAPE-06 | Pending | ✅ Verified |
| SCRAPE-07 | Pending | ⚠️ Verified (test-precision gap) |
| SCRAPE-08 | Pending | ⚠️ Verified (not independently testable) |
| SCRAPE-09 | Pending | ✅ Verified |
| SCRAPE-10 | Pending | ✅ Verified |
| SCRAPE-11 | Pending | ⚠️ Verified (default value not asserted) |
| SCRAPE-12 | Pending | ✅ Verified |
| SCRAPE-13 | Pending | ✅ Verified |
| SCRAPE-14 | Pending | ✅ Verified |
| SCRAPE-15 | Pending | ✅ Verified |
| SCRAPE-16 | Pending | ⚠️ Verified (test-precision gap) |
| SCRAPE-17 | Pending | ✅ Verified |

---

## Summary

**Overall**: ⚠️ Issues

**Spec-anchored check**: 10/17 ACs matched spec outcome cleanly; 6 spec-precision gaps flagged (SCRAPE-04, 07, 08, 09-adjacent count-only, 11, 16); 1 real behavioral gap found (SCRAPE-03)
**Sensor**: 3/3 mutations killed
**Gate**: 45 passed, 0 failed, 0 skipped

**What works**: Persistência (Flyway/Testcontainers), `ConfigService`, `PromotionDetectionService` (histórico, cold-start, anti-repique), upsert por ASIN, isolamento por categoria e o log WARN de zero-produtos-no-ciclo estão todos comprovados com precisão de spec e sobrevivem ao sensor de mutação.

**Issues found**:
1. SCRAPE-03: o branch WARN "categoria sem resultado" é inatingível pelo caminho real do Selenium (`TimeoutException` sempre escalando para o path de ERROR de SCRAPE-16) — ver Fix 1.
2. Asserções de log-content ausentes em SCRAPE-04/16, e de conteúdo salvo em SCRAPE-07/09 — ver Fix 2.
3. SCRAPE-11: valor-padrão "10%" seedado mas nunca verificado — ver Fix 3.

**Next steps**: Rotear Fix 1 (Major) como task de correção antes de considerar a feature pronta para produção; Fix 2/3 (Minor) podem ser agrupados numa única task de reforço de asserções. Após a correção, re-executar este Verifier (ciclo fix→re-verify, máx. 3 iterações).
