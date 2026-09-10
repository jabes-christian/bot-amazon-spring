# Enriquecimento de Conteúdo Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user - do not proceed without it.**

---

**Design**: `.specs/features/enriquecimento-conteudo/design.md`
**Status**: Fase 5 (T8, fix pós-Verifier) em andamento — Verifier iteração 2 (FAIL, `validation.md`) provou por mutação que o fix de T7 para ENRICH-08 era insuficiente (amostra de pixel único caía na barra de fundo, não no texto); T8 corrige com contagem de pixels quase-brancos na região do overlay, validada contra a mesma mutação

---

## Test Coverage Matrix

> Gerada por amostragem do repositório (convenções já estabelecidas em `scraping-coleta`) + decisão do usuário. Guidelines encontradas: nenhuma (mesma conclusão de `scraping-coleta` — repositório sem `AGENTS.md`/config de cobertura formal). Estende a matriz de `scraping-coleta` com uma camada nova: testes de cliente HTTP via `@RestClientTest`/`MockRestServiceServer` (não precisam de Docker, mas sobem um contexto Spring parcial — mais lentos que um teste unitário puro; seguem a mesma convenção de nome `*IT.java` já usada para testes baseados em contexto Spring, não pelo critério "precisa de Docker"). **Importante**: `@RestClientTest` não escaneia beans `@Service`/`@Repository` normais — só monta `RestClient.Builder`+`MockRestServiceServer`. Logo, em `BannerImageServiceIT` (T5), `PriceHistoryRepository` e `ConfigService` entram como `@MockitoBean` (stub), não como beans reais/Testcontainers — a query real de `findMenorPrecoDesde` já é coberta à parte em T2 (`PriceHistoryRepositoryIT`, Testcontainers de verdade). O que T5 testa é só a lógica de `BannerImageService` (decidir desenhar o selo dado o que o repository mockado retorna) + o download via `RestClient` real contra o servidor mockado — nunca a query em si.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Domínio / regra de negócio (`CopyGenerationService`, `EnriquecimentoService`) | unit | Todas as branches; 1:1 com os ACs de `spec.md` (ENRICH-01..07, 12, 13, 15, 16); todo edge case listado tem teste | `src/test/java/**/service/*Test.java` | `mvn test` |
| Cliente HTTP + composição de imagem (`BannerImageService`) | integration | Download sucesso/falha (timeout, 404, conteúdo não-imagem) via `@RestClientTest`+`MockRestServiceServer`; dimensões/formato do banner no caminho feliz | `src/test/java/**/service/BannerImageServiceIT.java` | `mvn verify` |
| Repository (extensão `PriceHistoryRepository.findMenorPrecoDesde`) | integration | Query correta contra Postgres real via Testcontainers | `src/test/java/**/repository/PriceHistoryRepositoryIT.java` | `mvn verify` |
| Entity / config / record sem lógica (`LlmConfig`, `ConteudoEnriquecidoDTO`, `CopyResultadoDTO`, migration) | none | apenas gate de build | - | `mvn verify` |

## Gate Check Commands

> Mesmo padrão de `scraping-coleta` — Maven não está no PATH desta máquina; usar o wrapper cacheado: `C:\Users\jmartinsc\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Após tasks só com unit tests (não precisa de Docker) | `mvn test` |
| Full | Após tasks com integration tests (Testcontainers ou `@RestClientTest` — Testcontainers exige Docker ativo) | `mvn verify` |
| Build | Após conclusão de fase, ou tasks só de config/entidade/migration | `mvn clean verify` |

---

## Execution Plan

**Fase 1 — Fundação: Config + extensão de persistência**

```
T1
T2
T3
```

(T1, T2 e T3 não dependem uma da outra — cada uma mexe em arquivos diferentes e independentes; executadas em sequência só pela ordem de leitura, não por dependência real.)

**Fase 2 — Geração de Copy**

```
T4 (depende de T3, cross-phase)
```

**Fase 3 — Banner**

```
T5 (depende de T2, cross-phase)
```

**Fase 4 — Orquestração**

```
T6 (depende de T4 e T5, cross-phase)
```

> Nota: os títulos das fases acima usam texto simples (não `### Phase N`) de propósito — os cabeçalhos `### Phase N` reais ficam em **Task Breakdown**, logo antes das tasks daquela fase (mesma técnica usada em `scraping-coleta`/`tasks.md` para não confundir o parser de `validate_tasks.py`, que atribui fase por posição linear do último cabeçalho `Phase N` visto).

---

## Task Breakdown

### Phase 1: Fundação — Config e Extensão de Persistência

### T1: Migration V2 (seed de config) + extensão do smoke test

**What**: Criar `V2__seed_enriquecimento_config.sql` inserindo as 3 chaves de `app_config` desta feature (`enriquecimento.llm-timeout-segundos=15`, `enriquecimento.limite-caracteres-copy=1024`, `enriquecimento.selo-menor-preco-dias=90`); estender `BotAmazonSpringApplicationIT` (já existe, de `scraping-coleta`) para confirmar a contagem total de `app_config` (4+3=7) e o valor de uma das novas chaves.
**Where**: `src/main/resources/db/migration/V2__seed_enriquecimento_config.sql`, `src/test/java/com/jchristian/bot_amazon_spring/BotAmazonSpringApplicationIT.java` (modifica)
**Depends on**: None
**Reuses**: Tabela `app_config` já criada por `scraping-coleta` (V1) — nenhuma migration de schema, só `INSERT`
**Requirement**: N/A — infraestrutura (AD-009), pré-requisito de `CopyGenerationService`/`BannerImageService`

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Migration insere as 3 chaves com os valores padrão exatos da seção Config do `design.md`
- [ ] `BotAmazonSpringApplicationIT` confirma `SELECT COUNT(*) FROM app_config` = 7
- [ ] `BotAmazonSpringApplicationIT` confirma `valor` de `enriquecimento.llm-timeout-segundos` = `'15'`
- [ ] Gate check passa: `mvn clean verify`
- [ ] Test count: 1 teste de integração (o mesmo `BotAmazonSpringApplicationIT`, com mais 2 assertions), 0 falhas

**Tests**: integration
**Gate**: build

**Commit**: `build(enriquecimento-conteudo): adiciona migration V2 com seed de config`

---

### T2: `PriceHistoryRepository.findMenorPrecoDesde` (extensão)

**What**: Adicionar `findMenorPrecoDesde(Product produto, LocalDateTime desde): Optional<BigDecimal>` (`@Query` com `MIN(preco)` e filtro `capturadoEm >= desde`) ao `PriceHistoryRepository` já existente — suporta o selo "menor preço em N dias" (P3, ENRICH-14).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/repository/PriceHistoryRepository.java` (modifica), `src/test/java/com/jchristian/bot_amazon_spring/repository/PriceHistoryRepositoryIT.java` (modifica)
**Depends on**: None
**Reuses**: Arquivo/entidade já existentes de `scraping-coleta` — só adiciona um método, não altera os já definidos
**Requirement**: ENRICH-14

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `findMenorPrecoDesde` retorna o menor preço entre as entradas com `capturadoEm >= desde`, ignorando entradas mais antigas que a janela
- [ ] Teste de integração confirma: entrada fora da janela (mais antiga) não influencia o mínimo retornado; entrada dentro da janela com preço menor é refletida no resultado
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 1 teste novo passa (mais os já existentes de `PriceHistoryRepositoryIT`, sem regressão), 0 falhas

**Tests**: integration
**Gate**: full

**Commit**: `feat(enriquecimento-conteudo): adiciona findMenorPrecoDesde a PriceHistoryRepository`

---

### T3: `LlmConfig` (bean `ChatModel`)

**What**: Criar o bean `ChatModel` via `OpenAiChatModel.builder().baseUrl(${openrouter.base-url}).apiKey(${openrouter.api-key}).modelName(${openrouter.model}).build()` — **sem** `.timeout(...)` no builder (o timeout é aplicado por chamada em `CopyGenerationService`, T4, para respeitar AD-009).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/config/LlmConfig.java`
**Depends on**: None
**Reuses**: `langchain4j-open-ai` (já no `pom.xml` desde o scaffold inicial)
**Requirement**: N/A — infraestrutura, pré-requisito de `CopyGenerationService`

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Bean `ChatModel` é construído com `baseUrl`/`apiKey`/`modelName` vindos de propriedades de ambiente (`openrouter.base-url`, `openrouter.api-key`, `openrouter.model`)
- [ ] Builder não define `.timeout(...)`
- [ ] Gate check passa: `mvn clean verify` (sem teste dedicado — bean de configuração depende de credenciais externas reais; comportamento coberto indiretamente quando `CopyGenerationService`, T4, for exercitado com o `ChatModel` mockado)

**Tests**: none
**Gate**: build

**Commit**: `feat(enriquecimento-conteudo): adiciona LlmConfig (bean ChatModel)`

---

### Phase 2: Geração de Copy

### T4: `CopyResultadoDTO` + `CopyGenerationService`

**What**: Criar `record CopyResultadoDTO(String texto, boolean viaLlm)` e `CopyGenerationService.gerarCopy(CandidatoPromocaoDTO candidato): CopyResultadoDTO` — tenta o LLM via `CompletableFuture.supplyAsync(() -> chatModel.chat(prompt), Executors.newVirtualThreadPerTaskExecutor())` com timeout aplicado via `.get(timeoutSegundos, TimeUnit.SECONDS)` (lido do `ConfigService` a cada chamada); em qualquer falha/timeout, cancela a future e cai para `montarCopyTemplate` (`viaLlm=false`); sempre insere/reinsere o link de afiliado ao final do texto, removendo qualquer URL que o LLM tenha gerado; trunca preservando o link íntegro se exceder o limite configurado.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/dto/CopyResultadoDTO.java`, `src/main/java/com/jchristian/bot_amazon_spring/service/CopyGenerationService.java`, `src/test/java/com/jchristian/bot_amazon_spring/service/CopyGenerationServiceTest.java`
**Depends on**: T3
**Reuses**: `ConfigService` (AD-009, `scraping-coleta`)
**Requirement**: ENRICH-01, ENRICH-03, ENRICH-04, ENRICH-05, ENRICH-06, ENRICH-07 (sinaliza a origem — a contagem por ciclo é responsabilidade de `canais-disparo`), ENRICH-15, ENRICH-16

**Tools**:

- MCP: `context7` (se necessário reconfirmar detalhe da API síncrona `ChatModel.chat(String)` durante a implementação)
- Skill: NONE

**Done when**:

- [ ] `gerarCopy` chama `chatModel.chat(prompt)` (via `supplyAsync`) quando o LLM responde dentro do timeout, retornando `CopyResultadoDTO` com `viaLlm=true` e o texto contendo nome do produto, preço de/por (`candidato.precoBase()`/`candidato.produto().getPrecoAtual()`), percentual (`candidato.percentualDesconto()`) e uma chamada para ação (ENRICH-01)
- [ ] Timeout excedido (LLM demora mais que `enriquecimento.llm-timeout-segundos`) cai para `montarCopyTemplate`, `viaLlm=false`, loga WARN com o motivo (ENRICH-04)
- [ ] Exceção lançada pelo LLM (erro, resposta vazia) tem o mesmo tratamento do item acima (ENRICH-04)
- [ ] `montarCopyTemplate` monta copy com título + de/por + percentual + link de afiliado, sem chamar o LLM (ENRICH-05)
- [ ] Link de afiliado (`candidato.produto().getUrlProduto() + "?tag=" + tagAfiliado`) está presente no texto final tanto no caminho LLM quanto no caminho template (ENRICH-06)
- [ ] Se o texto do LLM contiver uma URL diferente do link de afiliado, ela é removida e o link correto é inserido no lugar (ENRICH-16)
- [ ] Texto que excederia o limite configurado (`enriquecimento.limite-caracteres-copy`) é truncado preservando o link de afiliado íntegro no final (ENRICH-15)
- [ ] Gate check passa: `mvn test`
- [ ] Test count: 7 testes passam (1 por comportamento acima), 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `feat(enriquecimento-conteudo): adiciona CopyGenerationService (geração via LLM + fallback template)`

---

### Phase 3: Banner

### T5: `pom.xml` (RestClient) + `BannerImageService`

**What**: Adicionar `spring-boot-starter-restclient` (escopo principal) e `spring-boot-starter-restclient-test` (escopo teste) ao `pom.xml`; configurar `spring.http.clients.connect-timeout`/`read-timeout` em `application.properties`; criar `BannerImageService.gerarBanner(CandidatoPromocaoDTO candidato): Optional<Path>` (baixa a imagem via `RestClient`, retorna `Optional.empty()` se falhar ou o conteúdo não for uma imagem válida; senão compõe o banner via Java2D — canvas 800×800px, JPEG qualidade 0.85, sempre `.jpg` — sobrepondo percentual/de/por e opcionalmente o selo "menor preço em N dias" [P3]; grava em arquivo temporário) e `removerBanner(Path bannerPath): void`.
**Where**: `pom.xml`, `src/main/resources/application.properties`, `src/main/java/com/jchristian/bot_amazon_spring/service/BannerImageService.java`, `src/test/java/com/jchristian/bot_amazon_spring/service/BannerImageServiceIT.java`
**Depends on**: T2
**Reuses**: `PriceHistoryRepository.findMenorPrecoDesde` (T2)
**Requirement**: ENRICH-08, ENRICH-10, ENRICH-11, ENRICH-14

**Tools**:

- MCP: `context7` (se necessário confirmar detalhe de `@RestClientTest`/`MockRestServiceServer` ou da API `RestClient.Builder` durante a implementação)
- Skill: NONE

**Done when**:

- [ ] `pom.xml` tem as 2 dependências (`spring-boot-starter-restclient` sem escopo, `spring-boot-starter-restclient-test` escopo `test`), sem `<version>` explícita
- [ ] `application.properties` tem `spring.http.clients.connect-timeout` e `spring.http.clients.read-timeout` configurados
- [ ] Download bem-sucedido + imagem válida → banner gerado, canvas 800×800px, arquivo `.jpg`, contendo percentual/de/por sobrepostos (ENRICH-08)
- [ ] Download falha (timeout, HTTP erro) → `Optional.empty()`, loga WARN com ASIN e motivo (ENRICH-10)
- [ ] Conteúdo baixado não é uma imagem válida (`ImageIO.read` retorna `null`) → mesmo tratamento do item acima (ENRICH-10)
- [ ] `removerBanner` apaga o arquivo do caminho passado
- [ ] Produto com preço atual = menor dos últimos N dias → banner inclui o selo "MENOR PREÇO EM N DIAS" (ENRICH-14) — `PriceHistoryRepository.findMenorPrecoDesde` mockado via `@MockitoBean` retornando um valor stub igual ao preço atual; este teste verifica só a decisão de `BannerImageService` de desenhar o selo dado esse retorno, não a query real (coberta em T2)
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 5 testes passam (1 por comportamento acima, exceto o pom.xml/properties que não têm teste dedicado), 0 falhas

**Gap de cobertura conhecido (registrado em 2026-09-08, durante Execute)**: timeout de socket real não é simulado em `BannerImageServiceIT` — `MockRestServiceServer` é síncrono e não injeta latência, então o teste de "download falha" (`downloadComErroHttpRetornaOptionalEmptyELogaWarnComAsinEMotivo`) cobre erro HTTP (500), não timeout. Isso é aceitável porque `gerarBanner` trata timeout e erro HTTP no mesmo `catch (RestClientException e)` — `ResourceAccessException` (timeout) e `HttpServerErrorException`/`HttpClientErrorException` (erro HTTP) são ambas subtipos de `RestClientException` — e o valor de timeout em si (`spring.http.clients.read-timeout`) é configuração padrão do `RestClient` do Spring, não lógica própria desta feature. Se quiser um teste dedicado de timeout real (ex.: via WireMock com delay), não implementado agora — só sinalizado.

**Tests**: integration
**Gate**: full

**Commit**: `feat(enriquecimento-conteudo): adiciona BannerImageService (download + composição Java2D)`

---

### Phase 4: Orquestração

### T6: `ConteudoEnriquecidoDTO` + `EnriquecimentoService`

**What**: Criar `record ConteudoEnriquecidoDTO(String copy, Path bannerPath, boolean copyViaLlm)` e `EnriquecimentoService.enriquecer(CandidatoPromocaoDTO candidato): ConteudoEnriquecidoDTO` — chama `copyGenerationService.gerarCopy(candidato)` e `bannerImageService.gerarBanner(candidato)` exatamente uma vez cada, monta o DTO final.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/dto/ConteudoEnriquecidoDTO.java`, `src/main/java/com/jchristian/bot_amazon_spring/service/EnriquecimentoService.java`, `src/test/java/com/jchristian/bot_amazon_spring/service/EnriquecimentoServiceTest.java`
**Depends on**: T4, T5
**Reuses**: Nenhum
**Requirement**: ENRICH-02, ENRICH-07 (propagação do sinal), ENRICH-09, ENRICH-12, ENRICH-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `enriquecer` chama `copyGenerationService.gerarCopy(candidato)` e `bannerImageService.gerarBanner(candidato)` exatamente uma vez cada (ENRICH-02/ENRICH-09)
- [ ] `ConteudoEnriquecidoDTO` resultante tem `copy`/`copyViaLlm` vindos de `CopyResultadoDTO` e `bannerPath` vindo do `Optional<Path>` de `gerarBanner` (`null` se vazio)
- [ ] Quando `gerarBanner` retorna `Optional.empty()`, `ConteudoEnriquecidoDTO.bannerPath()` é `null` — sinaliza modo texto puro para `canais-disparo` (ENRICH-12), preservando a copy completa (ENRICH-13)
- [ ] Gate check passa: `mvn test`
- [ ] Test count: 3 testes passam (chamadas únicas, DTO montado corretamente, modo texto puro quando banner ausente), 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `feat(enriquecimento-conteudo): adiciona EnriquecimentoService (orquestração copy+banner)`

---

### Phase 5: Correções pós-Verifier

### T7: Fecha o gap de cobertura ENRICH-08 (+ precisão de log ENRICH-04)

**What**: Verifier iteração 1 (2026-09-08, `validation.md`) retornou FAIL: `BannerImageServiceIT.downloadComSucessoEImagemValidaGeraBannerJpeg800x800` só verificava dimensões/formato do banner, nunca que o overlay de desconto/de-por foi realmente desenhado (ENRICH-08). T7 adiciona uma asserção de pixel/região no teste (mesma técnica já usada no teste do selo): compara a componente verde do fundo puro (fora de qualquer overlay) contra a região da barra escura do overlay, confirmando escurecimento significativo. Também fecha uma nota secundária não-bloqueante do mesmo relatório (ENRICH-04): os testes de exceção/resposta-vazia do LLM só verificavam `viaLlm=false`, sem confirmar o conteúdo do log WARN (o teste de timeout já fazia isso) — estendidos para verificar o log em todos os 3 sub-casos de falha.
**Where**: `src/test/java/com/jchristian/bot_amazon_spring/service/BannerImageServiceIT.java` (modifica), `src/test/java/com/jchristian/bot_amazon_spring/service/CopyGenerationServiceTest.java` (modifica)
**Depends on**: T5, T4 (estende testes já escritos nessas tasks)
**Reuses**: Nenhum código novo — só reforça asserções existentes
**Requirement**: ENRICH-08, ENRICH-04

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `downloadComSucessoEImagemValidaGeraBannerJpeg800x800` confirma que a região do overlay de desconto/de-por (barra escura, y=680..800) está significativamente mais escura que o fundo puro da foto do produto (fora de qualquer overlay) — prova estrutural de que algo foi desenhado ali, sem depender de OCR
- [ ] `excecaoLancadaPeloLlmCaiParaTemplateComViaLlmFalse` confirma o conteúdo do log WARN (ASIN + motivo)
- [ ] `respostaVaziaDoLlmCaiParaTemplateComViaLlmFalse` confirma o conteúdo do log WARN (ASIN + motivo)
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 0 testes novos (2 testes existentes de `BannerImageServiceIT` e `CopyGenerationServiceTest` ganham asserções adicionais), 0 falhas

**Tests**: integration + unit (extensão de testes já existentes)
**Gate**: full

**Commit**: `test(enriquecimento-conteudo): fecha gap de cobertura ENRICH-08 e precisão de log ENRICH-04`

---

### T8: Corrige de fato o gap ENRICH-08 (fix de T7 provado insuficiente)

**What**: Verifier iteração 2 (2026-09-09, `validation.md`) retornou FAIL: provou por mutação (removendo os 2 `drawString` de desconto/de-por, mantendo o resto do código intacto) que a asserção de T7 (comparar um único pixel em `(750, 685)` contra o fundo puro) continuava passando — o ponto amostrado caía dentro da barra escura semi-transparente (`fillRect` desenhado incondicionalmente), não em cima do texto, então a asserção só provava que a barra existe, nunca que o texto foi de fato renderizado. T8 substitui a comparação pontual por uma varredura de toda a região do overlay (`x:0..800, y:680..800`) contando pixels "quase brancos" (`R>200 && G>200 && B>200`): a barra de fundo (preto 160/255 de alpha sobre a foto do produto) nunca produz branco — só o texto branco desenhado via `drawString` o faz — tornando a asserção robusta à posição exata dos glifos. Validado localmente contra a mutação real (comentar os 2 `drawString` em cópia local do serviço, confirmar que a contagem cai a 0 e o teste falha, restaurar o arquivo) antes de considerar o fix pronto, conforme prescrito pela lição L-002.
**Where**: `src/test/java/com/jchristian/bot_amazon_spring/service/BannerImageServiceIT.java` (modifica)
**Depends on**: T7 (substitui a asserção que T7 introduziu)
**Reuses**: Nenhum código novo em `main` — só a asserção de teste é substituída
**Requirement**: ENRICH-08

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `downloadComSucessoEImagemValidaGeraBannerJpeg800x800` varre toda a região do overlay (y=680..800, largura total) contando pixels quase-brancos e assere que a contagem excede um limiar (>500) — só atingível se o texto branco foi de fato desenhado
- [x] Validado por mutação real: remover os 2 `drawString(desconto/de-por)` faz a contagem cair a 0 e o teste falhar (confirmado localmente antes do commit, depois revertido)
- [x] Gate check passa: `mvn clean verify`
- [x] Test count: 0 testes novos (1 teste existente de `BannerImageServiceIT` tem a asserção substituída), 5/5 passam, 0 falhas

**Tests**: integration (extensão de teste já existente)
**Gate**: build

**Commit**: `test(enriquecimento-conteudo): substitui amostra de pixel unico por contagem de regiao para ENRICH-08`

---

## Phase Execution Map

Visual representation of task ordering. Phases run in sequence, and tasks within a phase run in order:

```
Fase 1 → Fase 2 → Fase 3 → Fase 4

Fase 1:  T1  T2  T3 (sem dependências entre si)
Fase 2:  T4
Fase 3:  T5
Fase 4:  T6
```

Cross-phase dependencies (fora do escopo do cross-check por fase): T4 depende de T3 (Fase 1); T5 depende de T2 (Fase 1); T6 depende de T4 (Fase 2) e T5 (Fase 3).

Execution is strictly sequential - there is no intra-phase parallelism.

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Migration V2 + extensão do smoke test | 2 arquivos (migration + teste existente estendido) | ✅ Granular — mesmo racional de T1 de `scraping-coleta`: migration sem consumidor de teste próprio não é gateável isolada, estender o smoke test já existente é a forma correta |
| T2: `findMenorPrecoDesde` | 2 arquivos (repository modificado + teste existente estendido) | ✅ Granular — extensão de arquivo já existente + seu teste, par coeso |
| T3: `LlmConfig` | 1 arquivo | ✅ Granular |
| T4: `CopyResultadoDTO`+`CopyGenerationService` | 2 arquivos | ✅ Granular — par coeso, DTO só existe para carregar o retorno do service |
| T5: pom.xml+properties+`BannerImageService` | 3 arquivos (pom.xml, application.properties, service) | ✅ Granular — merge justificado: nenhum dos 3 é gateável isoladamente (dependência sem uso não quebra nada sozinha, mas não há motivo pra separar do único consumidor desta task) |
| T6: `ConteudoEnriquecidoDTO`+`EnriquecimentoService` | 2 arquivos | ✅ Granular — par coeso, mesmo racional de T4 |

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | (fonte da Fase 1) | ✅ Match |
| T2 | None | (fonte da Fase 1) | ✅ Match |
| T3 | None | (fonte da Fase 1) | ✅ Match |
| T4 | T3 | Cross-phase (Fase 1 → Fase 2), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |
| T5 | T2 | Cross-phase (Fase 1 → Fase 3), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |
| T6 | T4, T5 | Cross-phase (Fase 2/3 → Fase 4), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Infra config (migration + teste estendido) | integration | integration | ✅ OK |
| T2 | Repository (extensão) | integration | integration | ✅ OK |
| T3 | Entity/config (`LlmConfig`) | none | none | ✅ OK |
| T4 | Domínio/regra de negócio (`CopyGenerationService`) | unit | unit | ✅ OK |
| T5 | Cliente HTTP + composição de imagem (`BannerImageService`) | integration | integration | ✅ OK |
| T6 | Domínio/regra de negócio (`EnriquecimentoService`) | unit | unit | ✅ OK |

---

## Success Criteria (herdado de `spec.md`)

- [ ] 100% dos produtos disparados têm link de afiliado presente, com ou sem LLM disponível
- [ ] Falha de imagem nunca impede o disparo do produto (cai para modo texto puro)
- [ ] Copy e banner são gerados uma única vez por produto por evento, reutilizados entre canais
