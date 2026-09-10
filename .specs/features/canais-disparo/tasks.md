# Canais & Disparo Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user - do not proceed without it.**

---

**Design**: `.specs/features/canais-disparo/design.md`
**Status**: Draft — aguardando aprovação do usuário antes de Execute

---

## Test Coverage Matrix

> Gerada por amostragem do repositório (convenções já estabelecidas em `scraping-coleta`/`enriquecimento-conteudo`) + decisão do usuário. Guidelines encontradas: nenhuma (mesma conclusão das 2 features anteriores — repositório sem `AGENTS.md`/config de cobertura formal). Estende as matrizes anteriores com duas camadas novas: **orquestração de ciclo** (`DisparoService`, o componente mais denso desta feature — ver nota de atenção no `design.md`) e **guarda de overlap de scheduler** (`DisparoScheduler`, lógica de `AtomicBoolean` genuinamente testável sem contexto Spring). Clientes HTTP de canal (`TelegramChannelSender`/`WhatsAppChannelSender`) reusam o padrão já validado de `BannerImageServiceIT` (`@RestClientTest`+`MockRestServiceServer`, T5 de `enriquecimento-conteudo`).

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Orquestração de ciclo (`DisparoService`) | unit | Todas as branches; 1:1 com os ACs de `spec.md` (DISPATCH-03,04,09..12,15,18..26); todo edge case listado tem teste — atenção redobrada aqui, componente de maior risco desta feature (ver `design.md`) | `src/test/java/**/service/DisparoServiceTest.java` | `mvn test` |
| Guarda de overlap de scheduler (`DisparoScheduler`) | unit | Execução concorrente é pulada com log WARN; a flag é liberada mesmo após exceção (DISPATCH-08) | `src/test/java/**/scheduler/DisparoSchedulerTest.java` | `mvn test` |
| Cliente HTTP por canal (`TelegramChannelSender`, `WhatsAppChannelSender`) | integration | Payload correto (multipart Telegram / JSON+Base64 WhatsApp) nos 2 modos (com banner / texto puro); falha HTTP mapeada para `EnvioException`; caso WhatsApp inclui explicitamente o cenário HTTP 201 + corpo `PENDING` como sucesso (decisão de design não validada contra instância real — ver Risks & Concerns) | `src/test/java/**/service/sender/*IT.java` | `mvn verify` |
| Repository (`ChannelRepository`, `DispatchHistoryRepository`) | integration | Query correta contra Postgres real via Testcontainers | `src/test/java/**/repository/{Channel,DispatchHistory}RepositoryIT.java` | `mvn verify` |
| Entity / interface / exceção / wiring de scheduler trivial (`Channel`, `TipoCanal`, `DispatchHistory`, `ChannelSender`, `EnvioException`, `ColetaScheduler`, `@EnableScheduling`) | none | apenas gate de build | - | `mvn verify` |

## Gate Check Commands

> Mesmo padrão das 2 features anteriores — Maven não está no PATH desta máquina; usar o wrapper cacheado: `C:\Users\jmartinsc\.m2\wrapper\dists\apache-maven-3.9.16-bin\5grr65jo27hi51sujmtcldfovl\apache-maven-3.9.16\bin\mvn.cmd`.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | Após tasks só com unit tests (não precisa de Docker) | `mvn test` |
| Full | Após tasks com integration tests (Testcontainers ou `@RestClientTest` — Testcontainers exige Docker ativo) | `mvn verify` |
| Build | Após conclusão de fase, ou tasks só de config/entidade/migration | `mvn clean verify` |

---

## Execution Plan

**Fase 1 — Fundação: Persistência (Channel + DispatchHistory)**

```
T1
T2
T3
```

(T2 depende de T1 — precisa da tabela `channel` existir para o teste de integração rodar. T3 depende de T1 e T2 — precisa de `dispatch_history` e da entidade `Channel` já existirem, por causa da FK.)

**Fase 2 — Envio (Senders)**

```
T4
T5 (depende de T2, T4)
T6 (depende de T2, T4)
```

**Fase 3 — Orquestração**

```
T7 (depende de T2, T3, T4, T5, T6, cross-phase)
```

**Fase 4 — Agendamento**

```
T8
T9 (depende de T7, cross-phase)
```

> Nota: os títulos das fases acima usam texto simples (não `### Phase N`) de propósito — os cabeçalhos `### Phase N` reais ficam em **Task Breakdown**, logo antes das tasks daquela fase (mesma técnica usada em `scraping-coleta`/`enriquecimento-conteudo` para não confundir o parser de `validate_tasks.py`). T8 (`ColetaScheduler`) não depende de nenhuma task nova desta feature (só de `ColetaService`, já existente em `scraping-coleta`) — está na Fase 4 por coesão narrativa (agendamento agrupado), não por dependência real, mesmo padrão já usado para T1/T2/T3 de `enriquecimento-conteudo`.

---

## Task Breakdown

### Phase 1: Fundação — Persistência

### T1: Migration V3 (tabelas `channel`/`dispatch_history` + seed de config) + extensão do smoke test

**What**: Criar `V3__create_canais_disparo_tables.sql` com as tabelas `channel` (`id`, `tipo` varchar, `identificador` varchar, `categorias_aceitas` varchar nullable, `ativo` boolean) e `dispatch_history` (`id`, `product_id` FK, `channel_id` FK, `enviado_em` timestamp) + `INSERT` das 3 chaves de `app_config` desta feature (`disparo.teto-produtos-por-canal=5`, `disparo.janela-dedup-dias=7`, `disparo.intervalo-entre-envios-segundos=2`); estender `BotAmazonSpringApplicationIT` para confirmar a contagem total de tabelas (4+2=6, incluindo `channel`/`dispatch_history` no filtro `IN (...)`) e de `app_config` (7+3=10).
**Where**: `src/main/resources/db/migration/V3__create_canais_disparo_tables.sql`, `src/test/java/com/jchristian/bot_amazon_spring/BotAmazonSpringApplicationIT.java` (modifica)
**Depends on**: None
**Reuses**: Padrão de migration já estabelecido em V1 (DDL)/V2 (seed) — esta é a primeira migration da feature a combinar os dois em um arquivo, pela mesma razão de T1 em `enriquecimento-conteudo` (config sem tabela nova não seria gateável isolada)
**Requirement**: N/A — infraestrutura (AD-009), pré-requisito de `Channel`/`DispatchHistory`/`ChannelRepository`/`DispatchHistoryRepository`

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Migration cria `channel` e `dispatch_history` com as colunas acima, FKs de `dispatch_history` para `product`/`channel`
- [ ] Migration insere as 3 chaves de `app_config` com os valores padrão exatos do `design.md`
- [ ] `BotAmazonSpringApplicationIT` confirma `SELECT COUNT(*) FROM information_schema.tables WHERE ... AND table_name IN (...)` = 6 (filtro estendido com `channel`, `dispatch_history`)
- [ ] `BotAmazonSpringApplicationIT` confirma `SELECT COUNT(*) FROM app_config` = 10
- [ ] Gate check passa: `mvn clean verify`
- [ ] Test count: 1 teste de integração (o mesmo `BotAmazonSpringApplicationIT`, com mais 2 assertions), 0 falhas

**Tests**: integration
**Gate**: build

**Commit**: `build(canais-disparo): adiciona migration V3 com tabelas channel/dispatch_history e seed de config`

---

### T2: `Channel` (entity) + `TipoCanal` (enum) + `ChannelRepository`

**What**: Criar `Channel` (Lombok+JPA, mesmo padrão de `CategoriaColeta`/`Product`: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`) com `id`, `tipo` (`TipoCanal { TELEGRAM, WHATSAPP }`, `@Enumerated(EnumType.STRING)`), `identificador`, `categoriasAceitas` (nullable), `ativo`; `ChannelRepository.findByAtivoTrue(): List<Channel>`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/entity/Channel.java`, `src/main/java/com/jchristian/bot_amazon_spring/entity/TipoCanal.java`, `src/main/java/com/jchristian/bot_amazon_spring/repository/ChannelRepository.java`, `src/test/java/com/jchristian/bot_amazon_spring/repository/ChannelRepositoryIT.java`
**Depends on**: T1
**Reuses**: Mesmo padrão Lombok+JPA de `CategoriaColeta`/`Product`; mesmo padrão de teste de repository (`@DataJpaTest`+Testcontainers) de `PriceHistoryRepositoryIT`
**Requirement**: DISPATCH-01, DISPATCH-02

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `findByAtivoTrue()` retorna apenas canais com `ativo=true`, nenhum inativo (DISPATCH-02)
- [ ] Nenhuma leitura em memória entre ciclos — cada chamada consulta o Postgres diretamente (DISPATCH-01, garantido pela própria natureza do Spring Data JPA, sem cache — AD-002)
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 1 teste novo passa (canal ativo retornado, inativo excluído), 0 falhas

**Tests**: integration
**Gate**: full

**Commit**: `feat(canais-disparo): adiciona entidade Channel e ChannelRepository`

---

### T3: `DispatchHistory` (entity) + `DispatchHistoryRepository`

**What**: Criar `DispatchHistory` (mesmo padrão de `PriceHistory`: `@ManyToOne` para `Product` e `Channel`, `@PrePersist` para `enviadoEm`); `DispatchHistoryRepository.existsByProductAndChannelAndEnviadoEmAfter(Product, Channel, LocalDateTime): boolean`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/entity/DispatchHistory.java`, `src/main/java/com/jchristian/bot_amazon_spring/repository/DispatchHistoryRepository.java`, `src/test/java/com/jchristian/bot_amazon_spring/repository/DispatchHistoryRepositoryIT.java`
**Depends on**: T1, T2
**Reuses**: Mesmo padrão de `PriceHistory`/`PriceHistoryRepository` (incluindo `@PrePersist` para timestamp)
**Requirement**: DISPATCH-19, DISPATCH-20

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `existsByProductAndChannelAndEnviadoEmAfter` retorna `true` quando há um envio bem-sucedido para o par produto×canal dentro da janela (`enviadoEm >= desde`), `false` quando a única entrada existente está fora da janela (DISPATCH-20)
- [ ] Registro persistido tem `enviadoEm` preenchido automaticamente via `@PrePersist`, sem precisar ser setado manualmente (DISPATCH-19)
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 2 testes novos passam (dentro da janela / fora da janela), 0 falhas

**Tests**: integration
**Gate**: full

**Commit**: `feat(canais-disparo): adiciona entidade DispatchHistory e DispatchHistoryRepository`

---

### Phase 2: Envio (Senders)

### T4: `ChannelSender` (interface) + `EnvioException`

**What**: Criar a interface `ChannelSender { void enviar(Channel canal, ConteudoEnriquecidoDTO conteudo) throws EnvioException; }` e a exceção unchecked `EnvioException`, conforme o contrato do `design.md`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/sender/ChannelSender.java`, `src/main/java/com/jchristian/bot_amazon_spring/service/sender/EnvioException.java`
**Depends on**: None
**Reuses**: `Channel` (T2), `ConteudoEnriquecidoDTO` (`enriquecimento-conteudo`, já existente)
**Requirement**: N/A — infraestrutura, pré-requisito de `TelegramChannelSender`/`WhatsAppChannelSender`/`DisparoService`

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Interface `ChannelSender` declara `enviar(Channel, ConteudoEnriquecidoDTO)` lançando `EnvioException`
- [ ] `EnvioException` é unchecked (estende `RuntimeException`)
- [ ] Gate check passa: `mvn clean verify` (sem teste dedicado — interface e exceção sem lógica própria, comportamento coberto indiretamente pelos testes de T5/T6/T7)

**Tests**: none
**Gate**: build

**Commit**: `feat(canais-disparo): adiciona interface ChannelSender e EnvioException`

---

### T5: `TelegramChannelSender`

**What**: Implementar `ChannelSender` para Telegram via `RestClient`: com banner, `POST https://api.telegram.org/bot{token}/sendPhoto` multipart (`chat_id`, `photo`=`FileSystemResource` do banner local, `caption`=copy); sem banner (modo texto puro), `POST .../sendMessage` JSON (`chat_id`, `text`=copy); qualquer status HTTP não-2xx vira `EnvioException`. Token via `@Value("${telegram.bot.token}")`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/sender/TelegramChannelSender.java`, `src/test/java/com/jchristian/bot_amazon_spring/service/sender/TelegramChannelSenderIT.java`
**Depends on**: T2, T4
**Reuses**: Padrão `@RestClientTest`+`MockRestServiceServer` já validado em `BannerImageServiceIT` (`enriquecimento-conteudo`, T5) — mesmo racional: `RestClient.Builder` autoconfigurado, `@MockitoBean` para dependências que `@RestClientTest` não escaneia (nenhuma nesta classe, que não tem dependências além do `RestClient`)
**Requirement**: DISPATCH-13, DISPATCH-14

**Tools**:

- MCP: `context7` (se necessário reconfirmar o formato exato do multipart de `sendPhoto` ou do corpo de erro do Telegram durante a implementação — ver Risks & Concerns do `design.md`)
- Skill: NONE

**Done when**:

- [ ] Produto com banner disponível → `sendPhoto` chamado com multipart contendo `chat_id`, `photo` (arquivo local) e `caption`=copy (DISPATCH-13)
- [ ] Produto em modo texto puro (`bannerPath()==null`) → `sendMessage` chamado com `chat_id` e `text`=copy, nunca `sendPhoto` (DISPATCH-14)
- [ ] Resposta HTTP não-2xx do Telegram → `EnvioException` lançada
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 3 testes passam (com banner, texto puro, falha HTTP), 0 falhas

**Tests**: integration
**Gate**: full

**Commit**: `feat(canais-disparo): adiciona TelegramChannelSender`

---

### T6: `WhatsAppChannelSender`

**What**: Implementar `ChannelSender` para WhatsApp via Evolution API/`RestClient`: com banner, `POST {url}/message/sendMedia/{instance}` (header `ApiKey`, JSON com `number`, `mediatype:"image"`, `mimetype:"image/jpeg"`, `fileName:"banner.jpg"`, `media`=Base64 do arquivo, `caption`=copy); sem banner, `POST .../message/sendText/{instance}` (JSON `number`, `text`=copy); qualquer status HTTP não-2xx vira `EnvioException`; HTTP 201 com corpo `{"status":"PENDING", ...}` é tratado como sucesso (decisão documentada no `design.md`, não validada contra instância real).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/sender/WhatsAppChannelSender.java`, `src/test/java/com/jchristian/bot_amazon_spring/service/sender/WhatsAppChannelSenderIT.java`
**Depends on**: T2, T4
**Reuses**: Mesmo padrão `@RestClientTest`+`MockRestServiceServer` de T5
**Requirement**: DISPATCH-16, DISPATCH-17

**Tools**:

- MCP: `context7` (se necessário reconfirmar o formato exato do payload de `sendMedia`/`sendText` da Evolution API durante a implementação)
- Skill: NONE

**Done when**:

- [ ] Produto com banner disponível → `sendMedia` chamado com JSON contendo `number`, `media` em Base64 do arquivo local, `caption`=copy (DISPATCH-16)
- [ ] Produto em modo texto puro → `sendText` chamado com `number` e `text`=copy, nunca `sendMedia` (DISPATCH-17)
- [ ] **Mock simula exatamente HTTP 201 com corpo `{"status":"PENDING"}`** (não um HTTP 200 genérico) e o sender trata essa resposta como sucesso — cenário específico pedido pelo usuário em 2026-09-10, por não ter sido validado contra uma instância real da Evolution API (ver `design.md` Risks & Concerns)
- [ ] Resposta HTTP não-2xx da Evolution API → `EnvioException` lançada
- [ ] Gate check passa: `mvn verify`
- [ ] Test count: 4 testes passam (com banner, texto puro, sucesso 201+PENDING, falha HTTP), 0 falhas

**Tests**: integration
**Gate**: full

**Commit**: `feat(canais-disparo): adiciona WhatsAppChannelSender`

---

### Phase 3: Orquestração

### T7: `DisparoService`

**What**: Implementar `executarCicloDisparo()` conforme os 6 passos do `design.md`: (1) busca candidatos 1x via `PromotionDetectionService`, monta lookup `Product→CandidatoPromocaoDTO`; (2) para cada canal ativo, filtra por categoria aceita (ignora canal com categorias vazias/nulas, WARN), remove já dedup'd via `DispatchHistoryRepository`, ordena por percentual desc, seleciona até o teto configurado, acumula `Map<Product,Set<Channel>>`; (3) nenhum canal ativo → INFO, encerra; (4) para cada produto do mapa: enriquece 1x, conta `copyViaLlm`, envia para cada canal (1 retry imediato em falha, grava `DispatchHistory` só em sucesso, ERROR sem interromper os demais, dorme o intervalo configurado após cada tentativa); (5) remove o banner após todos os canais do produto; (6) loga a contagem agregada `copiasViaLlm`/`copiasViaTemplate` (ENRICH-07). Canal com `tipo` diferente de `TELEGRAM`/`WHATSAPP` é ignorado com WARN (defensivo — hoje inalcançável via `TipoCanal` enum, mas gates o cenário caso o enum seja usado incorretamente por leitura direto do banco).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/service/DisparoService.java`, `src/test/java/com/jchristian/bot_amazon_spring/service/DisparoServiceTest.java`
**Depends on**: T2, T3, T4, T5, T6 (cross-phase)
**Reuses**: `PromotionDetectionService`/`EnriquecimentoService`/`BannerImageService`/`ConfigService` (features já existentes, contrato herdado sem alteração)
**Requirement**: DISPATCH-03, DISPATCH-04, DISPATCH-09, DISPATCH-10, DISPATCH-11, DISPATCH-12, DISPATCH-15, DISPATCH-18, DISPATCH-19, DISPATCH-20, DISPATCH-21, DISPATCH-22, DISPATCH-23, DISPATCH-24, DISPATCH-25, DISPATCH-26, ENRICH-07 (agregação por ciclo)

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Canal com categoria aceita restrita só recebe produtos dessa categoria (DISPATCH-03)
- [ ] Canal com tipo diferente de `TELEGRAM`/`WHATSAPP` é ignorado, log WARN (DISPATCH-04)
- [ ] Candidatos filtrados por canal são ordenados por percentual de desconto decrescente (DISPATCH-09/10)
- [ ] No máximo o teto configurado de produtos é selecionado por canal, a partir do topo da ordenação (DISPATCH-11)
- [ ] Canal sem nenhum candidato elegível é pulado sem erro nesse ciclo (DISPATCH-12)
- [ ] Intervalo configurado é aguardado após cada tentativa de envio, sucesso ou falha (DISPATCH-15/18)
- [ ] Envio bem-sucedido grava `DispatchHistory` (DISPATCH-19); produto já dedup'd dentro da janela é excluído da seleção daquele canal (DISPATCH-20/21)
- [ ] Falha de envio aciona exatamente 1 retry imediato antes de desistir (DISPATCH-22)
- [ ] Falha do retry loga ERROR com canal/produto/motivo e NÃO grava `DispatchHistory` — produto continua elegível no próximo ciclo (DISPATCH-23)
- [ ] Falha em um canal/produto não interrompe o processamento dos demais do ciclo (DISPATCH-24)
- [ ] Nenhum canal ativo → log INFO "nenhum canal ativo", ciclo encerra sem erro (DISPATCH-25)
- [ ] Canal com categorias aceitas vazias/nulas é ignorado, log WARN identificando o canal (DISPATCH-26)
- [ ] `enriquecimentoService.enriquecer(candidato)` é chamado exatamente 1x por produto, mesmo quando o produto está no conjunto de mais de um canal
- [ ] `bannerImageService.removerBanner` só é chamado depois que todos os canais elegíveis daquele produto já tentaram enviar
- [ ] Ao final do ciclo, loga a contagem agregada de `copiasViaLlm`/`copiasViaTemplate` (ENRICH-07)
- [ ] Gate check passa: `mvn test`
- [ ] Test count: 14 testes passam (1 por comportamento acima, mais o teste de enriquecer 1x mesmo com múltiplos canais), 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `feat(canais-disparo): adiciona DisparoService (orquestração do ciclo de disparo)`

---

### Phase 4: Agendamento

### T8: `@EnableScheduling` + `ColetaScheduler`

**What**: Adicionar `@EnableScheduling` à classe principal; criar `ColetaScheduler` com `@Scheduled(cron="${coleta.cron}", zone="America/Sao_Paulo")` chamando `coletaService.executarCicloColeta()` (já existente em `scraping-coleta`). Adicionar a propriedade `coleta.cron` a `application.properties` (valor padrão operacional, ajustável sem deploy via variável de ambiente — não é limiar de negócio, ver Tech Decisions do `design.md`).
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/BotAmazonSpringApplication.java` (modifica), `src/main/java/com/jchristian/bot_amazon_spring/scheduler/ColetaScheduler.java`, `src/main/resources/application.properties` (modifica)
**Depends on**: None
**Reuses**: `ColetaService.executarCicloColeta()` (`scraping-coleta`, já implementado e testado — nenhuma alteração)
**Requirement**: DISPATCH-05, DISPATCH-07 (fuso horário, metade — a outra metade é testada em T9)

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] `ColetaScheduler` dispara `coletaService.executarCicloColeta()` no cron configurado em `coleta.cron`, fuso `America/Sao_Paulo`
- [ ] `application.properties` tem `coleta.cron` com um valor padrão documentado (não `app_config` — decisão operacional, não limiar de negócio)
- [ ] Gate check passa: `mvn clean verify` (sem teste dedicado — wiring trivial de `@Scheduled` sobre um método já testado em `scraping-coleta`; nenhuma lógica nova além da chamada direta)

**Tests**: none
**Gate**: build

**Commit**: `feat(canais-disparo): adiciona ColetaScheduler e habilita @EnableScheduling`

---

### T9: `DisparoScheduler`

**What**: Criar `DisparoScheduler` com `@Scheduled(cron="${disparo.cron}", zone="America/Sao_Paulo")` chamando `disparoService.executarCicloDisparo()`, guardado por um `AtomicBoolean` de instância (`compareAndSet` no início; se já `true`, loga WARN e pula sem chamar `DisparoService`; libera a flag em `finally`, mesmo se `executarCicloDisparo()` lançar). Adicionar `disparo.cron` a `application.properties`.
**Where**: `src/main/java/com/jchristian/bot_amazon_spring/scheduler/DisparoScheduler.java`, `src/test/java/com/jchristian/bot_amazon_spring/scheduler/DisparoSchedulerTest.java`, `src/main/resources/application.properties` (modifica)
**Depends on**: T7
**Reuses**: Nenhum — decisão de lock local (`AtomicBoolean`) registrada no `design.md`, Tech Decisions
**Requirement**: DISPATCH-06, DISPATCH-07 (fuso horário, metade restante), DISPATCH-08

**Tools**:

- MCP: NONE
- Skill: NONE

**Done when**:

- [ ] Execução concorrente (simulada via um `DisparoService` mockado cuja resposta re-invoca `executarCicloAgendado()` reentrantemente) é pulada, log WARN, `disparoService.executarCicloDisparo()` chamado apenas 1x para as 2 invocações (DISPATCH-08)
- [ ] Após a execução externa retornar (com ou sem exceção), a flag é liberada — uma invocação subsequente chama `disparoService.executarCicloDisparo()` normalmente (`finally` testado explicitamente, inclusive no caminho de exceção)
- [ ] Gate check passa: `mvn test`
- [ ] Test count: 3 testes passam (overlap pulado + WARN, flag liberada após sucesso, flag liberada após exceção), 0 falhas

**Tests**: unit
**Gate**: quick

**Commit**: `feat(canais-disparo): adiciona DisparoScheduler com guarda de overlap via AtomicBoolean`

---

## Phase Execution Map

Visual representation of task ordering. Phases run in sequence, and tasks within a phase run in order:

```
Fase 1 → Fase 2 → Fase 3 → Fase 4

Fase 1:  T1  T2  T3
Fase 2:  T4  T5  T6
Fase 3:  T7
Fase 4:  T8  T9
```

Cross-phase dependencies (fora do escopo do cross-check por fase): T5 depende de T2 (Fase 1); T6 depende de T2 (Fase 1); T7 depende de T2/T3 (Fase 1) e T4/T5/T6 (Fase 2); T9 depende de T7 (Fase 3).

Execution is strictly sequential - there is no intra-phase parallelism.

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Migration V3 + extensão do smoke test | 2 arquivos (migration + teste existente estendido) | ✅ Granular — mesmo racional de T1 de `scraping-coleta`/`enriquecimento-conteudo`: migration sem consumidor de teste próprio não é gateável isolada |
| T2: `Channel`+`TipoCanal`+`ChannelRepository` | 4 arquivos (2 entidades/enum + repository + teste) | ✅ Granular — par coeso, entidade e enum só existem para o repository que os consulta |
| T3: `DispatchHistory`+`DispatchHistoryRepository` | 3 arquivos | ✅ Granular — par coeso, mesmo racional de `PriceHistory`/`PriceHistoryRepository` |
| T4: `ChannelSender`+`EnvioException` | 2 arquivos | ✅ Granular — interface e sua exceção associada, sem lógica própria de nenhuma |
| T5: `TelegramChannelSender` | 2 arquivos (service + teste) | ✅ Granular |
| T6: `WhatsAppChannelSender` | 2 arquivos (service + teste) | ✅ Granular |
| T7: `DisparoService` | 2 arquivos (service + teste) | ✅ Granular — arquivo único, apesar de denso (ver nota de atenção no `design.md`); dividir a classe em si não é justificado, os 6 passos formam uma única orquestração coesa |
| T8: `@EnableScheduling`+`ColetaScheduler`+properties | 3 arquivos | ✅ Granular — merge justificado: nenhum dos 3 é gateável isoladamente (habilitar scheduling sem nenhum `@Scheduled` não tem o que testar, e vice-versa) |
| T9: `DisparoScheduler`+properties | 2 arquivos | ✅ Granular |

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | (fonte da Fase 1) | ✅ Match |
| T2 | T1 | Fase 1, sequencial | ✅ Match |
| T3 | T1, T2 | Fase 1, sequencial | ✅ Match |
| T4 | None | (fonte da Fase 2) | ✅ Match |
| T5 | T2, T4 | Cross-phase (Fase 1 → Fase 2), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |
| T6 | T2, T4 | Cross-phase (Fase 1 → Fase 2), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |
| T7 | T2, T3, T4, T5, T6 | Cross-phase (Fase 1/2 → Fase 3), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |
| T8 | None | (fonte da Fase 4, por coesão narrativa — ver nota na Execution Plan) | ✅ Match |
| T9 | T7 | Cross-phase (Fase 3 → Fase 4), fora do escopo do diagrama por fase | ✅ Match (cross-phase não exige seta) |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1 | Infra config (migration + teste estendido) | integration | integration | ✅ OK |
| T2 | Repository (`ChannelRepository`) | integration | integration | ✅ OK |
| T3 | Repository (`DispatchHistoryRepository`) | integration | integration | ✅ OK |
| T4 | Interface/exceção sem lógica | none | none | ✅ OK |
| T5 | Cliente HTTP por canal (`TelegramChannelSender`) | integration | integration | ✅ OK |
| T6 | Cliente HTTP por canal (`WhatsAppChannelSender`) | integration | integration | ✅ OK |
| T7 | Orquestração de ciclo (`DisparoService`) | unit | unit | ✅ OK |
| T8 | Wiring de scheduler trivial (`ColetaScheduler`) | none | none | ✅ OK |
| T9 | Guarda de overlap de scheduler (`DisparoScheduler`) | unit | unit | ✅ OK |

---

## Success Criteria (herdado de `spec.md`)

- [ ] Telegram e WhatsApp recebem, cada um respeitando as categorias aceitas do seu canal
- [ ] O mesmo produto não é reenviado ao mesmo canal dentro da janela configurada
- [ ] Adicionar um canal novo exige apenas um `INSERT`
- [ ] Um canal com falha (credencial inválida, API fora do ar) nunca impede o disparo nos demais
