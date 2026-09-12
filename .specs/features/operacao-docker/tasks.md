# Operação & Docker Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user - do not proceed without it.**

---

**Design**: N/A — feature vai direto para Tasks por decisão do usuário (2026-09-12); esta é a única das 4 features do v1 sem `design.md` formal. As decisões de design que normalmente morariam lá estão travadas neste documento (ver notas de cada task e a seção "Decisões de Design" abaixo).
**Status**: Draft — aguardando aprovação do usuário antes de Execute.

---

## Decisões de Design (substituem o `design.md` ausente)

Levantadas por pesquisa direta do código (3 sub-agentes de exploração, 2026-09-12) e resolvidas pelo usuário antes da escrita deste documento:

1. **Fail-fast (OPS-11)**: implementado como `EnvironmentPostProcessor` dedicado (não um `@Component`/`@PostConstruct`, que rodaria tarde demais — o `DataSource` já teria falhado antes). Coleta **todas** as variáveis obrigatórias ausentes de uma vez e lança uma única exceção listando todas, em vez de falhar na primeira. Mantém os defaults `@Value` vazios já existentes (`LlmConfig`, `TelegramChannelSender`, `WhatsAppChannelSender`, `CopyGenerationService`) — zero teste existente quebra.
2. **Prefixo de log (OPS-12)**: mantém a convenção `ETAPA: mensagem` já usada em 15 das 16 mensagens de log existentes (descoberto por grep — não é uma convenção nova, é uma lacuna a preencher). `DETECCAO` hoje não tem nenhum log.
3. **`.env` local (Deferred Idea, registrada em `STATE.md` durante `enriquecimento-conteudo`)**: adota `spring.config.import=optional:file:.env[.properties]`, igual ao projeto de referência. O prefixo `optional:` é no-op quando o arquivo não existe — CI e `docker-compose` continuam recebendo credenciais via variável de ambiente do container, sem depender do Spring ler `.env`.
4. **`WebDriver` eager (Deferred Idea, registrada em `STATE.md` durante `scraping-coleta`)**: resolvida via `@Lazy` — não via `spring.main.lazy-initialization=true` (efeito colateral rejeitado: atrasaria a detecção de erro de wiring de *todos* os beans do projeto, não só do `WebDriver`).
5. **Estado real da persistência (achado da pesquisa, não previsto no `spec.md`)**: não existe `spring.datasource.*` em lugar nenhum do projeto — a URL do banco hoje só é fornecida pelo `@ServiceConnection` do Testcontainers nos testes. **A aplicação não sobe fora de teste.** Isso é bloqueador de qualquer `docker-compose` e é resolvido em T1.
6. **Requisitos já satisfeitos pelo código atual, não pelo `spec.md`**: OPS-03/OPS-06 (`SeleniumConfig.java:30-36` já ramifica `RemoteWebDriver`/`ChromeDriver` local via `selenium.remote.url`) e OPS-16/17/18 (Flyway + `ddl-auto=validate` já é a gestão de schema, via AD-007). As tasks correspondentes (T5, T6, T9) **verificam e travam** esse comportamento em vez de implementá-lo do zero.
7. **Risco confirmado — fontes no Alpine**: `BannerImageService.java:127-137` usa `java.awt.Font`/`Graphics2D`. `eclipse-temurin:21-jre-alpine` não traz fontconfig nem fontes TrueType — sem correção, a composição de banner quebra **só em produção** (nunca em dev, nunca em teste, porque nenhum dos dois usa essa imagem). T4 inclui `apk add --no-cache fontconfig ttf-dejavu`.
8. **Risco confirmado — teste frágil de contagem de WARN**: `DisparoServiceTest.java:367` assere exatamente 2 eventos WARN por ciclo. O resumo de fim de ciclo de OPS-13 (T8) é obrigatoriamente **INFO**, nunca WARN, para não quebrar essa assertion.

---

## Test Coverage Matrix

> Gerada por pesquisa direta do repositório (3 sub-agentes de exploração cobrindo: formato de `tasks.md` das 3 features anteriores; inventário de infra/config/Selenium/Flyway; estado de logging por etapa do ciclo). Guidelines de cobertura formal: nenhuma (mesma conclusão das 3 features anteriores — sem `AGENTS.md`). Diferença desta feature: parte do trabalho é **verificação de comportamento já existente** (OPS-03/06, OPS-16/17/18), não implementação nova — refletido no `Tests`/`Gate` de cada task.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Validação de ambiente no boot (`RequiredEnvironmentValidator`) | unit | Uma var ausente, várias ausentes, todas presentes — mensagem cita cada ausente por nome | `src/test/java/**/config/RequiredEnvironmentValidatorTest.java` | `mvn test` |
| Wiring lazy de bean (`SeleniumConfig`) | unit | `WebDriver` não é instanciado no refresh do contexto sem uso explícito | `src/test/java/**/config/SeleniumConfigTest.java` | `mvn test` |
| Instrumentação de log / resumo de ciclo (`ColetaService`, `DisparoService`, `PromotionDetectionService`, `ColetaScheduler`, `AmazonProductScraper`) | unit | Toda etapa (`COLETA`/`DETECCAO`/`ENRIQUECIMENTO`/`DISPARO`) tem ao menos 1 log; resumo de fim de ciclo em INFO com contagens | testes já existentes destas classes (ListAppender, padrão estabelecido) + novos onde a classe não tinha teste de log | `mvn test` |
| Migrations Flyway / schema | integration | `flyway_schema_history` com as migrations esperadas aplicadas; `ddl-auto=validate` confirmado | `src/test/java/**/BotAmazonSpringApplicationIT.java` | `mvn verify` |
| Config externalizada (`application.properties`, `.env.example`), `Dockerfile`, `docker-compose*.yml` | none | apenas gate de build — sem lógica própria testável em JVM | - | `mvn clean verify` |

## Gate Check Commands

> Mesmo padrão das 3 features anteriores — Maven não está no PATH desta máquina; usar o wrapper cacheado: `C:\Users\jmartinsc\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`. Confirmar `docker info` antes de rodar qualquer gate `full`/`build` — Testcontainers pula silenciosamente sem Docker (`Skipped` ≠ `Passed`, gap já encontrado em T1 de `canais-disparo`).

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Após tasks só com unit tests (não precisa de Docker) | `mvn test` |
| Full | Após tasks com integration tests (Testcontainers exige Docker ativo) | `mvn verify` |
| Build | Após conclusão de fase, ou tasks só de config/infra sem teste dedicado | `mvn clean verify` |

---

## Execution Plan

**Fase 1 — Configuração da aplicação**

```
T1
T2 (depende de T1)
T3
```

**Fase 2 — Containerização**

```
T4 (depende de T1, T2)
T5 (depende de T1)
T6 (depende de T4, T5)
```

**Fase 3 — Observabilidade**

```
T7
T8 (depende de T7)
```

**Fase 4 — Formalização do schema**

```
T9
```

> Nota: os títulos das fases acima usam texto simples (não `### Phase N`) de propósito — os cabeçalhos `### Phase N` reais ficam em **Task Breakdown**, logo antes das tasks daquela fase (mesma técnica usada nas 3 features anteriores, para não confundir o parser de `validate_tasks.py`). T3 e T9 não dependem de nenhuma outra task desta feature — estão nas fases 1 e 4 por coesão narrativa (T3 é config de aplicação; T9 fecha o ciclo de schema), mesmo padrão já usado para T8 de `canais-disparo`.

---

## Task Breakdown

### Phase 1: Configuração da aplicação

### T1: `spring.datasource.*` via env vars + `spring.config.import` do `.env` + `.env.example`

**What**: Adicionar `spring.datasource.url/username/password` a `application.properties`, lendo `${DB_URL}`/`${DB_USER}`/`${DB_PASSWORD}` com defaults de dev local (`jdbc:postgresql://localhost:5432/bot_amazon`, `postgres`, `postgres`); adicionar `spring.config.import=optional:file:.env[.properties]` (Decisão de Design #3). Criar `.env.example` versionado com placeholders de todas as 11 chaves externas usadas pelo projeto: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `TELEGRAM_BOT_TOKEN`, `EVOLUTION_API_URL`, `EVOLUTION_API_KEY`, `EVOLUTION_API_INSTANCE`, `OPENROUTER_API_KEY`, `OPENROUTER_MODEL`, `AFILIADO_TAG`, `SELENIUM_REMOTE_URL`.
**Where**: `src/main/resources/application.properties` (modifica), `.env.example` (novo)
**Depends on**: None
**Reuses**: Nenhum — primeira vez que o projeto declara `spring.datasource.*`; até aqui a URL do banco só existia via `@ServiceConnection` do Testcontainers nos testes (Decisão de Design #5)
**Requirement**: OPS-09 (parcial — Postgres), OPS-10

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `application.properties` lê `spring.datasource.url/username/password` de `${DB_URL}`/`${DB_USER}`/`${DB_PASSWORD}` com defaults de dev local (OPS-09 parcial)
- [ ] `spring.config.import=optional:file:.env[.properties]` presente, prefixo `optional:` confirmado (não quebra o boot quando `.env` não existe)
- [ ] `.env.example` versionado com as 11 chaves acima, cada uma com um placeholder claro (nunca um valor real) (OPS-10)
- [ ] `.gitignore:36-38` já ignora `.env`/`.env.local`/`.env.*.local` e **não** ignora `.env.example` — confirmado, não alterado
- [ ] Gate check passa: `mvn clean verify` (as ITs continuam usando o datasource do Testcontainers via `@ServiceConnection`, que sobrepõe `spring.datasource.*`)

**Tests**: none
**Gate**: build

**Commit**: `feat(operacao-docker): adiciona spring.datasource via env vars, spring.config.import e .env.example`

---

### T2: `RequiredEnvironmentValidator` (fail-fast no boot)

**What**: Implementar um `EnvironmentPostProcessor` que roda antes da criação do contexto Spring, ordenado após `ConfigDataEnvironmentPostProcessor` (para enxergar `application.properties` e o `.env` importado em T1), coletando todas as chaves obrigatórias ausentes ou vazias (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `TELEGRAM_BOT_TOKEN`, `EVOLUTION_API_URL`, `EVOLUTION_API_KEY`, `EVOLUTION_API_INSTANCE`, `OPENROUTER_API_KEY`, `OPENROUTER_MODEL`, `AFILIADO_TAG`) e lançando uma única exceção listando todas as ausentes (Decisão de Design #1). Registrado via `META-INF/spring.factories` (mecanismo padrão de `EnvironmentPostProcessor` no Spring Boot 4). Desativado nos testes via `systemPropertyVariables` no surefire e no failsafe do `pom.xml` (não via `src/test/resources/application.properties`, que sombrearia o `application.properties` principal e derrubaria `ddl-auto=validate` nas ITs).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/config/RequiredEnvironmentValidator.java`, `src/main/resources/META-INF/spring.factories` (novo), `src/test/java/com/jchristian/bot_amazon_spring/config/RequiredEnvironmentValidatorTest.java`, `pom.xml` (modifica)
**Depends on**: T1
**Reuses**: Nenhum — primeira validação de boot do projeto
**Requirement**: OPS-09, OPS-11

**Tools**:

- MCP: `context7` (confirmar a forma exata de registrar um `EnvironmentPostProcessor` no Spring Boot 4.1.1 e a semântica de ordenação relativa a `ConfigDataEnvironmentPostProcessor`, antes de implementar)
- Skill: NONE

**Done when**:

- [ ] Uma variável obrigatória ausente/vazia → boot falha citando exatamente essa variável por nome (OPS-11)
- [ ] Múltiplas variáveis ausentes → a exceção cita **todas**, não só a primeira encontrada
- [ ] Todas as variáveis presentes → boot segue normalmente, validator não interfere
- [ ] `mvn test`/`mvn verify` continuam passando sem nenhuma env var real setada (validator desativado via propriedade de sistema do plugin de teste, não via arquivo de properties que sombrearia o principal)
- [ ] Gate check passa: `mvn test`
- [ ] Test count: 3 testes novos passam (uma ausente, várias ausentes, nenhuma ausente), 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `feat(operacao-docker): adiciona RequiredEnvironmentValidator para fail-fast no boot`

---

### T3: `@Lazy` no `WebDriver`

**What**: Aplicar `@Lazy` em três pontos (Decisão de Design #4): (1) o `@Bean webDriver` em `SeleniumConfig`; (2) o parâmetro `WebDriver` de `webDriverWait` em `SeleniumConfig`; (3) o parâmetro `WebDriver` do construtor de `AmazonProductScraper`. Só no `@Bean` não basta — um consumidor eager (`webDriverWait`, que hoje recebe `WebDriver` como parâmetro de método `@Bean`) instanciaria o driver de qualquer forma na criação do bean `WebDriverWait`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/config/SeleniumConfig.java` (modifica), `src/main/java/com/jchristian/bot_amazon_spring/scraper/AmazonProductScraper.java` (modifica), `src/test/java/com/jchristian/bot_amazon_spring/config/SeleniumConfigTest.java` (novo)
**Depends on**: None
**Reuses**: Nenhum — `WebDriver` é uma interface, então o proxy `@Lazy` é um JDK dynamic proxy padrão do Spring, sem infraestrutura adicional
**Requirement**: N/A — Deferred Idea registrada em `STATE.md` durante `scraping-coleta`, resolvida aqui

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `mvn verify` deixa de exigir Chrome instalado na máquina — nenhum `WebDriver` real é instanciado a menos que um método do scraper seja de fato chamado
- [ ] Teste via `ApplicationContextRunner` sobre `SeleniumConfig` isolada confirma que o bean `webDriver` não é um singleton materializado após o refresh do contexto sem uso
- [ ] Remover qualquer um dos 3 `@Lazy` faz o teste falhar (alvo de mutação limpo — confirmar manualmente durante a implementação, sensor formal fica a cargo do Verifier)
- [ ] Gate check passa: `mvn test`
- [ ] Test count: 1 teste novo passa, 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `fix(operacao-docker): aplica @Lazy ao WebDriver para evitar Chrome eager em boot e testes`

---

### Phase 2: Containerização

### T4: `Dockerfile` + `.dockerignore`

**What**: Criar `Dockerfile` multi-stage: build com `maven:3.9-eclipse-temurin-21` (ou equivalente já fixado no `pom.xml`), runtime com `eclipse-temurin:21-jre-alpine` (conforme `spec.md`, decisão já confirmada), copiando o jar `bot-amazon-spring-0.0.1-SNAPSHOT.jar` (sem `<finalName>` no `pom.xml` — nome literal). Adicionar `apk add --no-cache fontconfig ttf-dejavu` no stage runtime (Decisão de Design #7 — sem isso, `BannerImageService` quebra em produção, nunca em dev/teste). Criar `.dockerignore` excluindo `target/`, `.git/`, `.idea/`, `*.md`.
**Where**: `Dockerfile` (novo), `.dockerignore` (novo)
**Depends on**: T1, T2
**Reuses**: Nenhum — primeiro artefato de container do projeto
**Requirement**: N/A — infraestrutura, pré-requisito de OPS-04

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `docker build -t bot-amazon-spring .` conclui com sucesso
- [ ] Container iniciado sem nenhuma env var obrigatória falha citando as variáveis ausentes (prova que T2 funciona no ambiente real de container, não só no teste unitário)
- [ ] Stage runtime tem `fontconfig`/`ttf-dejavu` instalados
- [ ] Gate check passa: `mvn clean verify` (o Dockerfile em si não tem teste JVM — validado por inspeção/build manual)

**Tests**: none
**Gate**: build

**Commit**: `build(operacao-docker): adiciona Dockerfile multi-stage com fontes para composição de banner`

---

### T5: `docker-compose.yml` (dev)

**What**: Criar `docker-compose.yml` de desenvolvimento com apenas o serviço `postgres` (imagem `postgres:16-alpine`, mesma versão usada pelo Testcontainers), variáveis lidas de `.env`, healthcheck via `pg_isready`, porta publicada para acesso da IDE. `SELENIUM_REMOTE_URL` deliberadamente ausente/vazia neste compose — verifica que `SeleniumConfig` já cai no ramo do ChromeDriver local (OPS-03, comportamento já existente, Decisão de Design #6).
**Where**: `docker-compose.yml` (novo)
**Depends on**: T1
**Reuses**: `SeleniumConfig.java:30-36` (branch `RemoteWebDriver`/`ChromeDriver` já implementado, sem alteração)
**Requirement**: OPS-01, OPS-02, OPS-03 (verificação)

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `docker compose up` sobe apenas o serviço `postgres` (OPS-01)
- [ ] Serviço `postgres` fica `healthy` via healthcheck antes de qualquer conexão da aplicação (OPS-02)
- [ ] Com `SELENIUM_REMOTE_URL` vazia/ausente, a aplicação rodada pela IDE usa ChromeDriver local (OPS-03 — comportamento herdado, não implementado aqui)
- [ ] Gate check passa: `mvn clean verify`

**Tests**: none
**Gate**: build

**Commit**: `build(operacao-docker): adiciona docker-compose.yml de desenvolvimento`

---

### T6: `docker-compose.prod.yml`

**What**: Criar `docker-compose.prod.yml` com três serviços: `postgres` (com healthcheck e volume nomeado), `selenium` (imagem `selenium/standalone-chrome`, com healthcheck), `app` (build a partir do `Dockerfile` de T4). Serviço `app` com `depends_on: {postgres: {condition: service_healthy}, selenium: {condition: service_healthy}}`. `restart: unless-stopped` nos três serviços. `SELENIUM_REMOTE_URL` do serviço `app` apontando para o serviço `selenium` — verifica que `SeleniumConfig` cai no ramo `RemoteWebDriver` (OPS-06, comportamento já existente).
**Where**: `docker-compose.prod.yml` (novo)
**Depends on**: T4, T5
**Reuses**: `Dockerfile` (T4), `SeleniumConfig.java:30-36` (branch `RemoteWebDriver`, sem alteração)
**Requirement**: OPS-04, OPS-05, OPS-06 (verificação), OPS-07, OPS-08, OPS-14, OPS-15

**Tools**:

- MCP: `context7` (confirmar o endpoint/comando de healthcheck correto da imagem `selenium/standalone-chrome` antes de configurar)
- Skill: NONE

**Done when**:

- [ ] `docker compose -f docker-compose.prod.yml up` sobe `postgres`, `selenium` e `app` (OPS-04)
- [ ] Serviço `app` só inicia depois de `postgres` E `selenium` reportarem `healthy` (OPS-05)
- [ ] Com `SELENIUM_REMOTE_URL` apontando para o serviço `selenium`, a aplicação usa `RemoteWebDriver` (OPS-06 — comportamento herdado)
- [ ] Os três serviços têm `restart: unless-stopped` (OPS-07)
- [ ] Dados do Postgres persistem em volume nomeado após `docker compose down` sem `-v` (OPS-08)
- [ ] Se `postgres` não atingir o healthcheck a tempo, `app` não inicia (OPS-14, comportamento padrão do `depends_on: condition: service_healthy`)
- [ ] Se `selenium` não atingir o healthcheck a tempo, `app` não inicia (OPS-15, mesmo mecanismo)
- [ ] Gate check passa: `mvn clean verify`

**Tests**: none
**Gate**: build

**Commit**: `build(operacao-docker): adiciona docker-compose.prod.yml com postgres, selenium e app`

---

### Phase 3: Observabilidade

### T7: Prefixo de etapa nas lacunas (`PromotionDetectionService`, `ColetaScheduler`, `AmazonProductScraper`, `ConfigService`)

**What**: Formalizar a convenção `ETAPA: mensagem` já usada em 15 das 16 mensagens de log existentes (Decisão de Design #2) e preencher as lacunas encontradas por pesquisa: adicionar `@Slf4j` e logs prefixados `DETECCAO:` a `PromotionDetectionService` (hoje zero logs); adicionar `@Slf4j` e logs `COLETA:` de início/fim de ciclo a `ColetaScheduler` (hoje sem nenhum log); adicionar logs `COLETA:` às falhas hoje engolidas em silêncio por `AmazonProductScraper` (`TimeoutException`→`List.of()` e outros catches). `ConfigService:36` permanece sem prefixo — é infraestrutura cross-cutting, não pertence a uma etapa específica do ciclo (nota explícita, não uma lacuna).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/PromotionDetectionService.java` (modifica), `src/main/java/com/jchristian/bot_amazon_spring/scheduler/ColetaScheduler.java` (modifica), `src/main/java/com/jchristian/bot_amazon_spring/scraper/AmazonProductScraper.java` (modifica), mais os testes correspondentes
**Depends on**: None
**Reuses**: Convenção `ETAPA: mensagem` já usada em `ColetaService`, `DisparoService`, `DisparoScheduler`, `BannerImageService`, `CopyGenerationService` — nenhuma reformatação dessas 5 classes, só extensão às 3 que faltam
**Requirement**: OPS-12

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `PromotionDetectionService` loga ao menos a decisão de elegibilidade de cada produto avaliado, prefixado `DETECCAO:`
- [ ] `ColetaScheduler` loga início e fim de cada disparo agendado, prefixado `COLETA:`
- [ ] `AmazonProductScraper` loga toda falha hoje silenciosa (timeout, elemento não encontrado), prefixada `COLETA:`, em vez de retornar lista vazia sem rastro
- [ ] As 5 classes já prefixadas (`ColetaService`, `DisparoService`, `DisparoScheduler`, `BannerImageService`, `CopyGenerationService`) permanecem inalteradas — nenhuma mensagem existente reformatada
- [ ] Os 5 test classes que já asserem conteúdo de log (`ColetaServiceTest`, `DisparoServiceTest`, `DisparoSchedulerTest`, `CopyGenerationServiceTest`, `BannerImageServiceIT`) continuam passando sem modificação
- [ ] Gate check passa: `mvn test`
- [ ] Test count: pelo menos 3 testes novos (1 por classe que ganhou log pela primeira vez: `PromotionDetectionServiceTest` cobrindo o novo log, `ColetaSchedulerTest` novo, extensão de `AmazonProductScraperTest` existente), 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `feat(operacao-docker): adiciona logs prefixados por etapa em DETECCAO, ColetaScheduler e AmazonProductScraper`

---

### T8: Resumo de fim de ciclo (`ColetaService`, `DisparoService`)

**What**: Em `ColetaService.executarCicloColeta()`, promover o contador `totalProdutosExtraidos` (hoje calculado e nunca logado, só alimenta o check de zero) a um resumo INFO ao final do ciclo, com contagem de categorias processadas, produtos extraídos e falhas de categoria. Em `DisparoService.executarCicloDisparo()`, adicionar ao resumo já existente (`copiasViaLlm`/`copiasViaTemplate`, linha atual) as contagens de envios bem-sucedidos e com falha, sem remover as contagens de proveniência de copy já asseridas por teste. Ambos os resumos são **INFO**, nunca WARN (Decisão de Design #8 — `DisparoServiceTest.java:367` assere exatamente 2 eventos WARN por ciclo; um novo WARN quebraria essa contagem).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/ColetaService.java` (modifica), `src/main/java/com/jchristian/bot_amazon_spring/service/DisparoService.java` (modifica), `src/test/java/com/jchristian/bot_amazon_spring/service/ColetaServiceTest.java` (modifica), `src/test/java/com/jchristian/bot_amazon_spring/service/DisparoServiceTest.java` (modifica)
**Depends on**: T7
**Reuses**: Contador `totalProdutosExtraidos` já existente em `ColetaService`; resumo `copiasViaLlm`/`copiasViaTemplate` já existente em `DisparoService:88`
**Requirement**: OPS-13

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Ao final de um ciclo de coleta, log INFO com categorias processadas, produtos extraídos e falhas de categoria (OPS-13)
- [ ] Ao final de um ciclo de disparo, log INFO estendido com envios bem-sucedidos e com falha, mantendo `copiasViaLlm`/`copiasViaTemplate` (OPS-13)
- [ ] `DisparoServiceTest.java:367` (contagem exata de 2 eventos WARN por ciclo) continua passando sem modificação — nenhum novo WARN introduzido
- [ ] `DisparoServiceTest.java:433-435` (asserts de `copiasViaLlm=1`/`copiasViaTemplate=1`) continua passando — tokens preservados, resumo só estendido
- [ ] Gate check passa: `mvn test`
- [ ] Test count: 2 testes novos ou estendidos (1 por classe), 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `feat(operacao-docker): adiciona contagens de sucesso/falha ao resumo de fim de ciclo`

---

### Phase 4: Formalização do schema

### T9: Verificação Flyway (OPS-16..19)

**What**: OPS-16 (migrations exclusivas via Flyway), OPS-17 (`ddl-auto=validate`) e OPS-18 (seed versionado junto do schema) já estão satisfeitos pelo estado atual do repositório, via AD-007 — esta task **verifica e trava** esse comportamento em teste, não o implementa do zero. Estender `BotAmazonSpringApplicationIT` para consultar `flyway_schema_history` e confirmar que as migrations esperadas (`V1`, `V2`, `V3`) estão aplicadas com sucesso (`success=true`). OPS-19 (fail-fast em migration pendente) é comportamento nativo do Flyway combinado com `ddl-auto=validate` — documentado em comentário no `application.properties`, coberto indiretamente pela assertion de histórico (uma migration pendente jamais apareceria como aplicada).
**Where**: `src/test/java/com/jchristian/bot_amazon_spring/BotAmazonSpringApplicationIT.java` (modifica), `src/main/resources/application.properties` (modifica, apenas comentário documentando OPS-19)
**Depends on**: None
**Reuses**: `BotAmazonSpringApplicationIT` já existente (estendido nas 3 features anteriores a cada nova migration)
**Requirement**: OPS-16, OPS-17, OPS-18, OPS-19

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `flyway_schema_history` tem exatamente 3 migrations (`V1`, `V2`, `V3`) com `success=true` (OPS-16, OPS-18)
- [ ] `spring.jpa.hibernate.ddl-auto=validate` confirmado em `application.properties` (OPS-17 — já era o valor, esta task só formaliza a verificação)
- [ ] Comentário em `application.properties` documenta que uma migration pendente falha o boot por natureza do Flyway + `validate` (OPS-19)
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 1 teste estendido (mais 1-2 assertions em `BotAmazonSpringApplicationIT`), 0 falhas

**Tests**: integration
**Gate**: full

**Commit**: `test(operacao-docker): verifica histórico de migrations Flyway e trava ddl-auto=validate`

---

## Phase Execution Map

Visual representation of task ordering. Phases run in sequence, and tasks within a phase run in order:

```
Fase 1 → Fase 2 → Fase 3 → Fase 4

Fase 1:  T1  T2  T3
Fase 2:  T4  T5  T6
Fase 3:  T7  T8
Fase 4:  T9
```

Cross-phase dependencies (fora do escopo do cross-check por fase): T4 depende de T2 (mesma fase, adiante do que a ordem intra-fase padrão sugeriria — ver Diagram-Definition Cross-Check); T6 depende de T4 e T5 (mesma fase).

Execution is strictly sequential - there is no intra-phase parallelism.

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: `spring.datasource.*` + `spring.config.import` + `.env.example` | 2 arquivos | ✅ Granular — as duas mudanças de properties e o `.env.example` só fazem sentido juntas (o `.env.example` documenta exatamente as chaves que `application.properties` passa a ler) |
| T2: `RequiredEnvironmentValidator` | 4 arquivos (classe + registro + teste + pom) | ✅ Granular — o registro via `spring.factories` é inerente à classe, sem uso isolado |
| T3: `@Lazy` no `WebDriver` | 3 arquivos | ✅ Granular — os 3 pontos de `@Lazy` só funcionam juntos (ver nota da task: só o `@Bean` não basta) |
| T4: `Dockerfile` + `.dockerignore` | 2 arquivos | ✅ Granular — par coeso, mesmo padrão de T4 (`ChannelSender`+`EnvioException`) de `canais-disparo` |
| T5: `docker-compose.yml` (dev) | 1 arquivo | ✅ Granular |
| T6: `docker-compose.prod.yml` | 1 arquivo | ✅ Granular |
| T7: Prefixo de etapa nas lacunas | 3 arquivos de produção + testes correspondentes | ✅ Granular — as 3 classes formam um único lote coeso (todas resolvem a mesma lacuna: etapas sem log), mesmo racional de T1 de `enriquecimento-conteudo`/`canais-disparo` para lotes de config sem consumidor isolado |
| T8: Resumo de fim de ciclo | 2 arquivos de produção + 2 de teste | ✅ Granular — par coeso, os dois resumos resolvem o mesmo requisito (OPS-13) |
| T9: Verificação Flyway | 2 arquivos | ✅ Granular — verificação, não implementação; escopo mínimo por natureza |

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | (fonte da Fase 1) | ✅ Match |
| T2 | T1 | Fase 1, sequencial | ✅ Match |
| T3 | None | Fase 1, sem dependência (coesão narrativa — ver nota na Execution Plan) | ✅ Match |
| T4 | T1, T2 | Cross-phase (Fase 1 → Fase 2), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |
| T5 | T1 | Cross-phase (Fase 1 → Fase 2), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |
| T6 | T4, T5 | Fase 2, sequencial (ambas as dependências já resolvidas dentro da própria fase) | ✅ Match |
| T7 | None | (fonte da Fase 3) | ✅ Match |
| T8 | T7 | Fase 3, sequencial | ✅ Match |
| T9 | None | (fonte da Fase 4, coesão narrativa — fecha o ciclo de schema) | ✅ Match |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Config externalizada (properties, `.env.example`) | none | none | ✅ OK |
| T2 | Validação de ambiente no boot | unit | unit | ✅ OK |
| T3 | Wiring lazy de bean (`SeleniumConfig`) | unit | unit | ✅ OK |
| T4 | `Dockerfile`/`.dockerignore` | none | none | ✅ OK |
| T5 | `docker-compose.yml` | none | none | ✅ OK |
| T6 | `docker-compose.prod.yml` | none | none | ✅ OK |
| T7 | Instrumentação de log (`DETECCAO`, `ColetaScheduler`, `AmazonProductScraper`) | unit | unit | ✅ OK |
| T8 | Resumo de fim de ciclo (`ColetaService`, `DisparoService`) | unit | unit | ✅ OK |
| T9 | Migrations Flyway / schema | integration | integration | ✅ OK |

---

## Success Criteria (herdado de `spec.md`)

- [ ] `docker compose up` sobe app + Postgres + Chrome e o ciclo roda sozinho (definição de pronto do PRD)
- [ ] Nenhuma credencial aparece hardcoded em código ou em arquivo versionado
- [ ] Logs de um ciclo completo permitem identificar, sem debugger, em qual etapa uma falha ocorreu
