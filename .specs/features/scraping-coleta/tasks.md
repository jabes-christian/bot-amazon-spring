# Scraping & Coleta Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user - do not proceed without it.**

---

**Design**: `.specs/features/scraping-coleta/design.md`
**Spec**: `.specs/features/scraping-coleta/spec.md`
**Status**: Done — 14/14 tasks completas, Verifier PASS na iteração 2 (`validation.md`), `validate_state.py` confirma 0 erros

---

## Test Coverage Matrix

> Gerada por amostragem do repositório + decisão do usuário (2026-09-07). Guidelines encontradas: nenhuma (`AGENTS.md`, `CONTRIBUTING.md`, config de cobertura) — repositório é o esqueleto do Spring Initializr, único teste existente é o placeholder `BotAmazonSpringApplicationTests` (`contextLoads()`, sem asserts de negócio). Defaults fortes aplicados, calibrados pela decisão confirmada do usuário: **unit tests (JUnit Jupiter + Mockito + AssertJ, via `spring-boot-starter-test`) para regra de negócio; integration tests via Testcontainers Postgres para repository/Flyway; sem e2e** (app headless, sem camada de controller HTTP nesta feature).

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Infraestrutura de build/teste (migration Flyway + wiring do Testcontainers) | integration | Um smoke test provando que a migration V1 aplica limpo e o contexto sobe contra um Postgres real em container | `src/test/java/**/BotAmazonSpringApplicationIT.java` | `mvn verify` |
| Domínio / regra de negócio (`ColetaService`, `PromotionDetectionService`, `ConfigService`) | unit | Todas as branches; 1:1 com os ACs de `spec.md` (SCRAPE-01..17); todo edge case listado tem teste | `src/test/java/**/service/*Test.java` | `mvn test` |
| Scraper (`BaseScraper`, `AmazonProductScraper`) | unit | `WebDriver`/`WebElement` mockados; caminho feliz + filtragem de card malformado/sem ASIN-preço | `src/test/java/**/scraper/**/*Test.java` | `mvn test` |
| DTO com validação (`ScrapedProductDTO`) | unit | Branches do construtor compacto (obrigatoriedade de campo) | `src/test/java/**/dto/*Test.java` | `mvn test` |
| Repository / acesso a dados (repositories Spring Data JPA) | integration | Métodos de query derivada + violação de constraint única, contra Postgres real via Testcontainers | `src/test/java/**/repository/*IT.java` | `mvn verify` |
| Entity / config / record sem lógica (entidades JPA, `SeleniumConfig`, `AmazonSelectorsProperties`, `CandidatoPromocaoDTO`) | none | apenas gate de build | - | `mvn verify` |

**Convenção de nomes** (Maven padrão, confirmado via Context7): testes `*Test.java` rodam só no Surefire (`mvn test`, fase `test`) — Testcontainers **não** é acionado. Testes `*IT.java` rodam só no Failsafe (fases `integration-test`/`verify`, acionadas por `mvn verify`) — exigem Docker daemon ativo. Nenhum arquivo `*IT.java` casa com os padrões default do Surefire (`**/*Test.java`, `**/*Tests.java`, `**/*TestCase.java`), então a separação funciona sem excludes manuais.

## Gate Check Commands

> Gerado a partir de `pom.xml` (Maven, sem ferramenta de lint/formatter configurada no projeto) e da decisão de teste confirmada pelo usuário.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Após tasks só com unit tests (não precisa de Docker) | `mvn test` |
| Full | Após tasks com integration tests (Testcontainers — exige Docker daemon ativo) | `mvn verify` |
| Build | Após conclusão de fase, ou tasks só de config/entidade/migration | `mvn clean verify` |

---

## Execution Plan

Phases are ordered and run sequentially - each phase completes before the next begins, and tasks within a phase execute in order.

**Fase 1 — Fundação: Persistência (Flyway/Testcontainers + Entidades)**

```
T1 → T2 → T3
T1 → T4 → T5 → T6
```

**Fase 2 — Scraper Amazon (Selenium)**

```
T7  → T10
T8  → T10
T9  → T10
```

**Fase 3 — Orquestração de Coleta**

```
T11 (task único da fase — depende de T3, T4, T5, T6 da Fase 1 e T10 da Fase 2)
```

**Fase 4 — Detecção de Promoção**

```
T12 (task único da fase — depende de T3, T5, T6 da Fase 1)
```

> Nota: os títulos acima usam "Fase" (não "Phase") de propósito — os cabeçalhos `### Phase N` reais, que o `validate_tasks.py` usa para atribuir cada task à sua fase, ficam em **Task Breakdown**, imediatamente antes das tasks daquela fase. Repetir o marcador aqui, em português, evita que o parser (que rastreia linearmente o último cabeçalho `Phase N` visto) atribua todas as tasks à última fase do documento.

---

## Task Breakdown

### Phase 1: Fundação — Persistência (Flyway/Testcontainers + Entidades)

### T1: Flyway + Testcontainers + migration inicial de schema

> **Correção (2026-09-07, durante a execução desta task)**: o gate rodou verde na primeira tentativa (contexto sobe, teste passa), mas a asserção de contagem de tabelas falhou (`expected: 4, but was: 0`) — o Flyway nunca era acionado. Causa: em Spring Boot 4, `FlywayAutoConfiguration` vive num módulo próprio (`spring-boot-flyway`) que `org.flywaydb:flyway-core` sozinho não traz; o artefato correto é o starter `org.springframework.boot:spring-boot-starter-flyway` (confirmado via Context7 na fonte do próprio `spring-boot`). Ver AD-007 em `STATE.md` para o racional completo. `Where`/`Done when` abaixo já refletem a correção — `flyway-core` isolado não é mais usado.

**What**: Adicionar as dependências Flyway (`spring-boot-starter-flyway`, `flyway-database-postgresql`) e de teste (`spring-boot-starter-test`, `spring-boot-testcontainers`, `testcontainers-postgresql`, `testcontainers-junit-jupiter`) ao `pom.xml`, configurar o `maven-failsafe-plugin` (execuções `integration-test`+`verify`), configurar `spring.jpa.hibernate.ddl-auto=validate`, criar a migration `V1__create_scraping_coleta_tables.sql` (tabelas `categoria_coleta`, `product`, `price_history`, `app_config` + seed das 5 categorias + seed das 4 chaves de `app_config` desta feature — ver `design.md`/Data Models), e transformar o teste placeholder do Initializr em um smoke test real de contexto+migration.
**Where**: `pom.xml`, `src/main/resources/application.properties`, `src/main/resources/db/migration/V1__create_scraping_coleta_tables.sql`, `src/test/java/com/jchristian/bot_amazon_spring/BotAmazonSpringApplicationIT.java` (renomeado de `BotAmazonSpringApplicationTests.java`)
**Depends on**: None
**Reuses**: Nenhum. Nenhuma dependência ganha versão explícita — todas herdadas do BOM do `spring-boot-starter-parent` 4.1.1 (confirmado via Context7 para Flyway/AD-007 e para Testcontainers), seguindo a convenção já usada no `pom.xml`
**Requirement**: N/A — infraestrutura de persistência (AD-007), pré-requisito de todas as demais tasks desta feature

**Tools**:

- MCP: `context7` (já usado nesta sessão para confirmar coordenadas Maven do Testcontainers e convenção Surefire/Failsafe)
- Skill: NONE

**Done when**:

- [x] `pom.xml` tem as 2 dependências Flyway (escopo padrão) e as 4 dependências de teste (escopo `test`), todas sem `<version>` explícita
- [x] `maven-failsafe-plugin` configurado no `<build><plugins>` com execuções ligadas às fases `integration-test` e `verify`
- [x] `application.properties` tem `spring.jpa.hibernate.ddl-auto=validate`
- [x] Migration cria as 4 tabelas com as colunas descritas em `design.md`/Data Models, incluindo o índice em `price_history(product_id, capturado_em)`
- [x] Migration semeia as 5 categorias (MONITOR, NOTEBOOK, PERIFERICO, CADEIRA_GAMER, MESA) e as 4 chaves de `app_config` (`coleta.percentual-minimo-queda=10`, `coleta.preco-minimo-valido=0.01`, `coleta.preco-maximo-valido=50000.00`, `coleta.intervalo-entre-categorias-segundos=5`)
- [x] `BotAmazonSpringApplicationIT` usa `@Testcontainers(disabledWithoutDocker = true)` + `@Container @ServiceConnection static PostgreSQLContainer` e confirma que o contexto sobe com a migration aplicada: 4 tabelas existem, `categoria_coleta` tem 5 linhas, `app_config` tem 4 linhas
- [x] Gate check passa: `mvn clean verify`
- [x] Test count: 1 teste de integração passa (o smoke test), 0 falhas

**Tests**: integration
**Gate**: build

**Status**: ✅ Complete

**Commit**: `build(scraping-coleta): adiciona Flyway, Testcontainers e migration inicial de schema`

---

### T2: Entidade e repository de `AppConfig`

> **Correção (2026-09-07, durante a execução desta task)**: `@DataJpaTest` e `@AutoConfigureTestDatabase` não estão nos pacotes clássicos (`org.springframework.boot.test.autoconfigure.orm.jpa`/`...jdbc`) nesta versão — Spring Boot 4 moveu ambos para módulos próprios. Pacotes corretos, confirmados via Context7 (arquivo-fonte oficial do `spring-boot`): `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` e `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`. **Vale para T4, T5 e T6 também** (mesmas anotações).

**What**: Criar a entidade JPA `AppConfig` (chave/valor/descrição/atualizadoEm, `chave` única) e `AppConfigRepository` com `findByChave(String)`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/entity/AppConfig.java`, `src/main/java/com/jchristian/bot_amazon_spring/repository/AppConfigRepository.java`
**Depends on**: T1
**Reuses**: Padrão Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`) + `@PreUpdate` do projeto de referência; `JpaRepository<Entity, Long>` com método derivado
**Requirement**: N/A — infraestrutura compartilhada (AD-009), consumida por `ConfigService` (T3)

**Tools**:

- MCP: `context7` (se necessário confirmar anotação JPA específica do Spring Boot 4.1.1)
- Skill: NONE

**Done when**:

- [x] `AppConfig` mapeia exatamente as colunas da migration (T1), com `chave` marcada única
- [x] `AppConfigRepository.findByChave(String chave)` retorna `Optional<AppConfig>`
- [x] Teste de integração cobre: salvar e buscar por chave existente; buscar chave inexistente retorna `Optional.empty()`; inserir chave duplicada viola a constraint única
- [x] Gate check passa: `mvn verify`
- [x] Test count: 3 testes passam, 0 falhas

**Tests**: integration
**Gate**: full

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona entidade e repository de AppConfig`

---

### T3: `ConfigService`

**What**: Criar `ConfigService` com `getBigDecimal`, `getInt`, `getLong` — cada um busca a chave via `AppConfigRepository`, loga WARN e retorna o valor padrão quando a chave está ausente (nunca lança exceção por chave faltando).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/ConfigService.java`
**Depends on**: T2
**Reuses**: Nenhum. Sem cache (AD-002/D4) — cada chamada é uma consulta indexada por chave única
**Requirement**: N/A — infraestrutura compartilhada (AD-009); consumido por SCRAPE-12 (percentual mínimo configurável) e pelas demais features via a mesma tabela

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `getBigDecimal`/`getInt`/`getLong` retornam o valor convertido quando a chave existe em `app_config`
- [x] Cada método retorna o valor padrão e loga WARN quando a chave está ausente (mockando `AppConfigRepository.findByChave` retornando `Optional.empty()`)
- [x] Nenhum método lança exceção para chave ausente
- [x] Gate check passa: `mvn test`
- [x] Test count: 6 testes passam (2 por método: chave presente, chave ausente), 0 falhas

**Tests**: unit
**Gate**: quick

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona ConfigService para leitura de limiares em app_config`

---

### T4: Entidade e repository de `CategoriaColeta`

**What**: Criar a entidade `CategoriaColeta` (id, codigo único, keywordBusca, ativo) e `CategoriaColetaRepository` com `findByAtivoTrue()`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/entity/CategoriaColeta.java`, `src/main/java/com/jchristian/bot_amazon_spring/repository/CategoriaColetaRepository.java`
**Depends on**: T1
**Reuses**: Mesmo padrão Lombok + `JpaRepository` das demais entidades
**Requirement**: SCRAPE-01

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `CategoriaColeta` mapeia exatamente as colunas da migration (T1)
- [x] `findByAtivoTrue()` retorna só as categorias com `ativo = true`
- [x] Teste de integração confirma: as 5 categorias semeadas em T1 aparecem via `findByAtivoTrue()`; uma categoria marcada `ativo = false` não aparece no resultado
- [x] Gate check passa: `mvn verify`
- [x] Test count: 2 testes passam, 0 falhas

**Tests**: integration
**Gate**: full

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona entidade e repository de CategoriaColeta`

---

### T5: Entidade e repository de `Product`

**What**: Criar a entidade `Product` (asin único, categoria `@ManyToOne`, titulo, precoAtual, precoRiscado nullable, urlImagem, urlProduto, lastCandidatoPreco nullable, criadoEm/atualizadoEm) e `ProductRepository` com `findByAsin(String)`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/entity/Product.java`, `src/main/java/com/jchristian/bot_amazon_spring/repository/ProductRepository.java`
**Depends on**: T4
**Reuses**: Mesmo padrão Lombok + `JpaRepository`; `@PrePersist`/`@PreUpdate` para timestamps
**Requirement**: SCRAPE-02, SCRAPE-05

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `Product` mapeia exatamente as colunas da migration (T1), com `categoria` como `@ManyToOne` obrigatório
- [x] `findByAsin(String asin)` retorna `Optional<Product>`
- [x] Teste de integração confirma: salvar e buscar por ASIN existente; ASIN inexistente retorna `Optional.empty()`; inserir ASIN duplicado viola a constraint única
- [x] Gate check passa: `mvn verify`
- [x] Test count: 3 testes passam, 0 falhas

**Tests**: integration
**Gate**: full

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona entidade e repository de Product`

---

### T6: Entidade e repository de `PriceHistory`

**What**: Criar a entidade `PriceHistory` (product `@ManyToOne`, preco, capturadoEm) e `PriceHistoryRepository` com `countByProduct(Product)` e `findMenorPrecoByProduct(Product)` (`@Query` com `MIN(preco)`).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/entity/PriceHistory.java`, `src/main/java/com/jchristian/bot_amazon_spring/repository/PriceHistoryRepository.java`
**Depends on**: T5
**Reuses**: Mesmo padrão Lombok + `JpaRepository`
**Requirement**: SCRAPE-07, SCRAPE-08, SCRAPE-09

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `PriceHistory` mapeia exatamente as colunas da migration (T1)
- [x] `countByProduct(product)` retorna a contagem correta de entradas
- [x] `findMenorPrecoByProduct(product)` retorna o menor `preco` já registrado para o produto
- [x] Teste de integração confirma: 2 entradas de histórico para o mesmo produto não são deduplicadas (SCRAPE-09); `findMenorPrecoByProduct` retorna o mínimo correto entre elas
- [x] Gate check passa: `mvn verify`
- [x] Test count: 2 testes passam, 0 falhas (ver nota abaixo sobre a contagem)

**Tests**: integration
**Gate**: full

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona entidade e repository de PriceHistory`

---

### Phase 2: Scraper Amazon (Selenium)

### T7: `SeleniumConfig`

> **Correção (2026-09-07, durante a execução de T10)**: faltava o bean `WebDriverWait` — `BaseScraper` (T8) exige um no construtor, e nem o design nem esta task o listavam entre os beans de `SeleniumConfig`. Só apareceu como `NoSuchBeanDefinitionException` ao rodar `mvn clean verify` com `AmazonProductScraper` (T10) já wireado no contexto completo — nenhum teste unitário de T7 ou T8 isoladamente exercitava essa ligação. Corrigido: `SeleniumConfig` ganhou `@Bean WebDriverWait webDriverWait(WebDriver, timeoutSegundos)`, com `selenium.wait-timeout-segundos` (default 10) como propriedade técnica (`@Value`, não `app_config` — mesmo racional dos seletores CSS, AD-009).

**What**: Criar o bean `WebDriver` — `RemoteWebDriver` quando `${selenium.remote.url:}` não vazio, senão `ChromeDriver` local via `WebDriverManager.chromedriver().setup()` — e o bean `ChromeOptions` (headless, user-agent realista).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/config/SeleniumConfig.java`
**Depends on**: None
**Reuses**: Padrão do `SeleniumConfig` do projeto de referência (inspecionado via GitHub) — headless, user-agent realista, mesmo mecanismo de fallback local/remoto
**Requirement**: N/A — infraestrutura (comportamento local vs. remoto rastreado a OPS-01/OPS-03 em `operacao-docker`; implementação física antecipada para cá — ver `design.md`, nota "Ajuste")

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Bean `WebDriver` usa `RemoteWebDriver` quando a propriedade está preenchida, `ChromeDriver` local quando vazia
- [x] `ChromeOptions` inclui modo headless e um user-agent realista
- [x] Gate check passa: `mvn clean verify` (sem teste dedicado — bean de configuração depende de driver de navegador real; comportamento coberto indiretamente quando `AmazonProductScraper`, T10, for exercitado)

**Tests**: none
**Gate**: build

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona SeleniumConfig (bean WebDriver local/remoto)`

---

### T8: `BaseScraper` (abstract)

**What**: Criar a classe abstrata com os helpers Selenium comuns: `navegarPara(String url)`, `aguardarElementos(By locator)`, `extrairTexto(By locator)`, `extrairAtributo(By locator, String atributo)`, `elementoExiste(By locator)`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/scraper/base/BaseScraper.java`
**Depends on**: None
**Reuses**: `BaseScraper` do projeto de referência (inspecionado via GitHub) — copiado quase igual, é infraestrutura Selenium genérica
**Requirement**: N/A — infraestrutura Selenium genérica

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `navegarPara` chama `driver.get(url)` com a URL recebida
- [x] `aguardarElementos`/`extrairTexto`/`extrairAtributo` usam `WebDriverWait` real sobre um `WebDriver`/`WebElement` mockado (Mockito) e retornam os valores esperados
- [x] `elementoExiste` retorna `true` quando o elemento é encontrado e `false` quando `WebDriver` lança `NoSuchElementException`/timeout
- [x] Gate check passa: `mvn test`
- [x] Test count: 6 testes passam (1 por método, mais 1 extra para o caso `elementoExiste=false`), 0 falhas

**Tests**: unit
**Gate**: quick

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona BaseScraper com helpers Selenium reutilizáveis`

---

### T9: `ScrapedProductDTO`

**What**: Criar o `record` com construtor compacto validando os campos obrigatórios (asin, título, preço atual não nulos/vazios) e uma factory `of(...)`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/dto/ScrapedProductDTO.java`
**Depends on**: None
**Reuses**: Padrão de DTO-como-record do projeto de referência (construtor compacto + factory `of(...)`)
**Requirement**: SCRAPE-02

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Construção com todos os campos obrigatórios preenchidos funciona (asin, título, preço atual, imagem, URL do produto; preço riscado é opcional/nullable)
- [x] Construção com asin, título ou preço atual nulo/vazio lança exceção no construtor compacto
- [x] Gate check passa: `mvn test`
- [x] Test count: 4 testes passam (1 caminho feliz + 3 validações), 0 falhas

**Tests**: unit
**Gate**: quick

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona ScrapedProductDTO`

---

### T10: `AmazonSelectorsProperties` + `AmazonProductScraper`

**What**: Criar `AmazonSelectorsProperties` (`@ConfigurationProperties`, seletores CSS candidatos da seção "Validação de Seletores Amazon" do `design.md` — técnico, fora de `app_config` por decisão já registrada) e `AmazonProductScraper.buscarPorKeyword(String keyword)`, que navega para `https://www.amazon.com.br/s?k={keyword}`, extrai cada card e filtra os sem ASIN/preço.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/config/AmazonSelectorsProperties.java`, `src/main/java/com/jchristian/bot_amazon_spring/scraper/AmazonProductScraper.java`
**Depends on**: T7, T8, T9
**Reuses**: `BaseScraper` (T8); segue a decisão de Design de um único scraper parametrizado por keyword (não um por categoria)
**Requirement**: SCRAPE-01, SCRAPE-02

> **Pré-requisito separado (não bloqueia esta task no calendário, mas bloqueia a validação de que os seletores realmente funcionam contra a Amazon real)**: antes de considerar os seletores definitivos, execute o procedimento manual descrito em `design.md` §"Validação de Seletores Amazon" (DevTools + `amazon.com.br` ao vivo) e ajuste `AmazonSelectorsProperties` com os valores confirmados/corrigidos.

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `AmazonSelectorsProperties` expõe um campo por seletor candidato da tabela de Design (container do card, ASIN, título, preço atual, preço riscado, imagem, link)
- [x] `buscarPorKeyword` navega para a URL de busca correta (`/s?k={keyword}`, keyword codificada corretamente na URL)
- [x] `buscarPorKeyword` extrai ASIN, título, preço atual, preço riscado (quando presente), URL da imagem e URL do produto de cada card (mockando `WebDriver`/`WebElement` para simular N cards)
- [x] Cards sem ASIN ou sem preço atual são descartados do resultado, não geram `ScrapedProductDTO`
- [x] Gate check passa: `mvn test` (e `mvn clean verify` para confirmar que o contexto completo sobe — foi aqui que o gap do `WebDriverWait` apareceu; ver correção no topo desta task)
- [x] Test count: 4 testes passam (URL correta, extração completa, card sem ASIN filtrado, card sem preço filtrado), 0 falhas

**Tests**: unit
**Gate**: quick

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona AmazonProductScraper e seletores configuráveis`

---

### Phase 3: Orquestração de Coleta

### T11: `ColetaService`

**What**: Criar `executarCicloColeta()` — para cada `CategoriaColeta` ativa (sequencial): chama o scraper, valida sanidade de preço (descarta ≤R$0 ou >R$50.000, loga WARN com ASIN+valor bruto), faz upsert do `Product` por ASIN, grava `PriceHistory`; isola falha por categoria (try/catch, log ERROR, segue as demais); aguarda intervalo configurável entre categorias; loga WARN se o ciclo inteiro extrair zero produtos.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/ColetaService.java`
**Depends on**: T3, T4, T5, T6, T10
**Reuses**: Padrão de isolamento por item do `MonitorScheduler` de referência, movido para dentro do service (uma execução cobre N categorias aqui)
**Requirement**: SCRAPE-01, SCRAPE-03, SCRAPE-04, SCRAPE-05, SCRAPE-06, SCRAPE-07, SCRAPE-08, SCRAPE-09, SCRAPE-16, SCRAPE-17

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Para cada categoria ativa (via `CategoriaColetaRepository.findByAtivoTrue()`), chama `AmazonProductScraper.buscarPorKeyword` com a keyword da categoria (SCRAPE-01)
- [x] Categoria sem resultado loga WARN com categoria+keyword e segue para a próxima (SCRAPE-01/edge case)
- [x] Preço ≤R$0 ou >R$50.000 descarta o produto do ciclo e loga WARN com ASIN+valor bruto (SCRAPE-04), lendo os limiares via `ConfigService`
- [x] Produto com ASIN já existente é atualizado (título, preço, imagem), nunca duplicado (SCRAPE-05)
- [x] Toda passagem por um produto grava uma entrada de `PriceHistory` com ASIN+preço+timestamp, sem deduplicar por dia (SCRAPE-07, SCRAPE-09)
- [x] Aguarda o intervalo configurável (via `ConfigService`) entre categorias distintas (SCRAPE-06)
- [x] Exceção lançada ao processar uma categoria é capturada, logada em ERROR com a categoria afetada, e não interrompe as demais categorias (SCRAPE-16)
- [x] Se o ciclo inteiro extrair zero produtos, loga WARN de nível operacional (verificado via Logback `ListAppender`, não só ausência de exceção), não erro fatal (SCRAPE-17)
- [x] Gate check passa: `mvn test`
- [x] Test count: 8 testes passam (1 por comportamento acima), 0 falhas

**Tests**: unit
**Gate**: quick

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona ColetaService (orquestração do ciclo de coleta)`

---

### Phase 4: Detecção de Promoção

### T12: `CandidatoPromocaoDTO` + `PromotionDetectionService`

**What**: Criar o `record CandidatoPromocaoDTO(Product produto, BigDecimal percentualDesconto)` e `buscarCandidatosElegiveis()` — para cada `Product`: calcula preço-base (mínimo do histórico se ≥2 entradas, senão `precoRiscado` se presente, senão pula o produto); se `precoAtual <= precoBase * (1 - percentualMinimo)` **e** `precoAtual != lastCandidatoPreco`, inclui o candidato com o percentual calculado e atualiza `lastCandidatoPreco`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/dto/CandidatoPromocaoDTO.java`, `src/main/java/com/jchristian/bot_amazon_spring/service/PromotionDetectionService.java`
**Depends on**: T3, T5, T6
**Reuses**: Nenhum
**Requirement**: SCRAPE-10, SCRAPE-11, SCRAPE-12, SCRAPE-13, SCRAPE-14, SCRAPE-15

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Produto com ≥2 entradas de histórico usa o menor preço já registrado como base (SCRAPE-10)
- [x] Percentual mínimo padrão de 10% é lido via `ConfigService`, configurável sem alteração de código (SCRAPE-12)
- [x] Produto sinalizado retorna `CandidatoPromocaoDTO` com o percentual de desconto calculado corretamente (SCRAPE-11 → contrato com `canais-disparo`, ver revisão em `design.md`)
- [x] Produto cujo preço atual não mudou desde o último candidato sinalizado (`lastCandidatoPreco == precoAtual`) não é sinalizado de novo (SCRAPE-13)
- [x] Produto com <2 entradas de histórico E preço riscado presente usa o preço riscado como base (SCRAPE-14)
- [x] Produto com <2 entradas de histórico E sem preço riscado não é sinalizado, por falta de base de comparação (SCRAPE-15)
- [x] `lastCandidatoPreco` é atualizado como efeito colateral quando um produto é sinalizado
- [x] Gate check passa: `mvn test`
- [x] Test count: 6 testes passam (1 por comportamento acima), 0 falhas

**Tests**: unit
**Gate**: quick

**Status**: ✅ Complete

**Commit**: `feat(scraping-coleta): adiciona PromotionDetectionService (detecção de queda de preço)`

---

### Phase 5: Correções pós-Verifier (fix→re-verify, iteração 1)

> Tasks criadas a partir de `.specs/features/scraping-coleta/validation.md` (Verifier, 2026-09-07, veredito FAIL). Não fazem parte do plano original de 12 tasks — nascem do ciclo fix→re-verify do skill (máx. 3 iterações antes de escalar ao usuário).

### T13: `TimeoutException` de zero-cards vira lista vazia, não ERROR genérico

**What**: Capturar `TimeoutException` especificamente dentro de `AmazonProductScraper.buscarPorKeyword` (em torno da chamada a `aguardarElementos`) e tratá-la como "zero resultados" (retorna `List.of()`), preservando a escalada normal para `ColetaService`'s isolamento por categoria (SCRAPE-16) para qualquer outra exceção — nunca um catch genérico de `Exception`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/scraper/AmazonProductScraper.java`
**Depends on**: T10
**Reuses**: Nenhum
**Requirement**: SCRAPE-03 (fix de gap real encontrado pelo Verifier — `ExpectedConditions.presenceOfAllElementsLocatedBy` lança `TimeoutException` em vez de retornar lista vazia quando zero elementos são encontrados)

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] `buscarPorKeyword` captura `org.openqa.selenium.TimeoutException` (só esse tipo, não `Exception`/`WebDriverException` genéricos) ao redor de `aguardarElementos` e retorna `List.of()` nesse caso
- [x] Teste com `driver.findElements(...)` sempre retornando lista vazia confirma que `buscarPorKeyword` retorna `List.of()` sem lançar exceção (exercita o `WebDriverWait` real, não um mock que contorna a semântica do Selenium)
- [x] Teste separado confirma que uma exceção que NÃO é `TimeoutException` (ex.: `WebDriverException` de driver morto) continua propagando normalmente, não é engolida
- [x] Gate check passa: `mvn test`
- [x] Test count: 2 testes novos passam (6 anteriores de `AmazonProductScraperTest` continuam passando), 0 falhas

**Tests**: unit
**Gate**: quick

**Status**: ✅ Complete

**Commit**: `fix(scraping-coleta): trata TimeoutException de zero-cards como lista vazia (SCRAPE-03)`

---

### T14: Reforço de asserções de conteúdo (log e dados salvos)

**What**: Adicionar asserções de conteúdo onde hoje só há comportamento/contagem comprovados: `ListAppender` para o texto do WARN de SCRAPE-04 (ASIN+valor) e do ERROR de SCRAPE-16 (categoria) em `ColetaServiceTest`; `argThat` para o `PriceHistory` salvo (ASIN/preço) em `ColetaServiceTest` (SCRAPE-07/09); e uma asserção do valor semeado `'10'` para `coleta.percentual-minimo-queda` em `BotAmazonSpringApplicationIT` (SCRAPE-11).
**Where**: `src/test/java/com/jchristian/bot_amazon_spring/service/ColetaServiceTest.java`, `src/test/java/com/jchristian/bot_amazon_spring/BotAmazonSpringApplicationIT.java`
**Depends on**: T11
**Reuses**: Padrão de `ListAppender` já usado em `cicloComZeroProdutosExtraidosLogaWarnENaoLancaExcecao` (SCRAPE-17)
**Requirement**: SCRAPE-04, SCRAPE-07, SCRAPE-09, SCRAPE-11, SCRAPE-16

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [x] Teste de preço fora da faixa (SCRAPE-04) confirma via `ListAppender` que o WARN loga o ASIN e o valor bruto do produto descartado
- [x] Teste de falha isolada por categoria (SCRAPE-16) confirma via `ListAppender` que o ERROR loga a categoria afetada
- [x] Teste de histórico (SCRAPE-07/09) usa `argThat`/`ArgumentCaptor` para confirmar que os `PriceHistory` salvos têm o ASIN/preço corretos, não só a contagem de chamadas
- [x] `BotAmazonSpringApplicationIT` confirma que `app_config` tem `chave='coleta.percentual-minimo-queda'` com `valor='10'` (SCRAPE-11)
- [x] Gate check passa: `mvn verify` (mistura unit + integration)
- [x] Test count: testes existentes fortalecidos (nenhum teste novo adicionado, só asserções extras nos já existentes), 0 falhas

**Tests**: unit, integration
**Gate**: full

**Status**: ✅ Complete

**Commit**: `test(scraping-coleta): reforça asserções de conteúdo de log e dados salvos (SCRAPE-04/07/09/11/16)`

---

## Phase Execution Map

Visual representation of task ordering. Phases run in sequence, and tasks within a phase run in order:

```
Phase 1 → Phase 2 → Phase 3 → Phase 4

Phase 1:  T1 → T2 → T3
          T1 → T4 → T5 → T6
Phase 2:  T7  → T10
          T8  → T10
          T9  → T10
Phase 3:  T11
Phase 4:  T12
```

Cross-phase dependencies (não desenhadas acima — fora do escopo do cross-check por fase, ver regra abaixo): T11 depende também de T3, T4, T5, T6 (Fase 1); T12 depende também de T3, T5, T6 (Fase 1).

Execution is strictly sequential - there is no intra-phase parallelism. A single agent (or batch worker) works one task at a time, in order.

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Flyway+Testcontainers+migration | 3 arquivos (pom.xml, application.properties, migration) + 1 teste renomeado | ✅ Granular — merge justificado: nenhum dos 3 arquivos é gateável isoladamente (Flyway falha o boot sem migration, AD-007); ver regra "Resolving compilation dependencies" de `tasks.md` |
| T2: Entidade+repository AppConfig | 2 arquivos (entidade + repository) | ✅ Granular — par coeso, repository não tem comportamento independente da entidade |
| T3: ConfigService | 1 arquivo | ✅ Granular |
| T4: Entidade+repository CategoriaColeta | 2 arquivos | ✅ Granular — mesmo racional de T2 |
| T5: Entidade+repository Product | 2 arquivos | ✅ Granular — mesmo racional de T2 |
| T6: Entidade+repository PriceHistory | 2 arquivos | ✅ Granular — mesmo racional de T2 |
| T7: SeleniumConfig | 1 arquivo | ✅ Granular |
| T8: BaseScraper | 1 arquivo | ✅ Granular |
| T9: ScrapedProductDTO | 1 arquivo | ✅ Granular |
| T10: AmazonSelectorsProperties+AmazonProductScraper | 2 arquivos | ✅ Granular — par coeso, properties só existe para parametrizar o scraper |
| T11: ColetaService | 1 arquivo (1 método, `executarCicloColeta`) | ✅ Granular |
| T12: CandidatoPromocaoDTO+PromotionDetectionService | 2 arquivos | ✅ Granular — mesmo racional de T2 (DTO sem comportamento próprio) |

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | (fonte da Fase 1) | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | T2 | T2 → T3 | ✅ Match |
| T4 | T1 | T1 → T4 | ✅ Match |
| T5 | T4 | T4 → T5 | ✅ Match |
| T6 | T5 | T5 → T6 | ✅ Match |
| T7 | None | (fonte da Fase 2) | ✅ Match |
| T8 | None | (fonte da Fase 2) | ✅ Match |
| T9 | None | (fonte da Fase 2) | ✅ Match |
| T10 | T7, T8, T9 | T7 → T10, T8 → T10, T9 → T10 | ✅ Match |
| T11 | T3, T4, T5, T6, T10 | T10 → T11 dentro da Fase 3; T3/T4/T5/T6 são cross-phase (Fase 1), fora do escopo do diagrama por fase — regra explícita do validador | ✅ Match (cross-phase não exige seta) |
| T12 | T3, T5, T6 | Fase 4 tem T12 como único nó; todas as dependências são cross-phase (Fase 1), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Infra build/teste (migration + Testcontainers) | integration | integration | ✅ OK |
| T2 | Repository (+ Entity) | integration (maior exigência entre as 2 camadas) | integration | ✅ OK |
| T3 | Domínio/regra de negócio (ConfigService) | unit | unit | ✅ OK |
| T4 | Repository (+ Entity) | integration | integration | ✅ OK |
| T5 | Repository (+ Entity) | integration | integration | ✅ OK |
| T6 | Repository (+ Entity) | integration | integration | ✅ OK |
| T7 | Entity/config (SeleniumConfig) | none | none | ✅ OK |
| T8 | Scraper (BaseScraper) | unit | unit | ✅ OK |
| T9 | DTO com validação (ScrapedProductDTO) | unit | unit | ✅ OK |
| T10 | Scraper (+ Entity/config AmazonSelectorsProperties) | unit (maior exigência) | unit | ✅ OK |
| T11 | Domínio/regra de negócio (ColetaService) | unit | unit | ✅ OK |
| T12 | Domínio/regra de negócio (+ Entity/config CandidatoPromocaoDTO) | unit (maior exigência) | unit | ✅ OK |

---

## Success Criteria (herdado de `spec.md`)

- [ ] Uma coleta agendada popula produtos e histórico de preço a partir das 5 categorias do v1
- [ ] Uma queda ≥ percentual configurado gera exatamente um candidato disponível para disparo
- [ ] Falha em uma categoria (WebDriver, CAPTCHA, zero resultados) nunca interrompe as demais
