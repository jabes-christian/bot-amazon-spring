# STATE

## Decisions

### AD-001
- **Decision**: Telegram e Evolution API (WhatsApp) são integrados via `RestClient` puro do Spring Web, sem SDK oficial do Telegram.
- **Reason**: O bot só dispara conteúdo, nunca recebe/processa mensagens; o SDK completo (`telegrambots-spring-boot-starter`) traz polling/webhook e um modelo de comandos que não serão usados.
- **Trade-off**: Perde-se a tipagem forte e os helpers do SDK; endpoints da API do Telegram (`sendPhoto`) e da Evolution API são montados e chamados manualmente.
- **Scope**: Camada de integração de disparo (dispatch/notification), todos os canais.
- **Date**: 2026-09-03
- **Status**: active

### AD-002
- **Decision**: Sem Redis e sem camada de cache nesta versão; deduplicação de produto/promoção é resolvida com consultas diretas no Postgres.
- **Reason**: Volume de dados (5 categorias, N canais) não justifica a complexidade operacional de um cache distribuído; `spring-boot-starter-cache` presente no `pom.xml` fica sem uso no v1 (revisitar na fase de Design se deve ser removido).
- **Trade-off**: Toda checagem de dedup e histórico de preço custa uma query; aceitável no volume esperado, pode exigir índices dedicados.
- **Scope**: Toda a camada de persistência e deduplicação (produtos, preços, histórico de disparo).
- **Date**: 2026-09-03
- **Status**: active

### AD-003
- **Decision**: Sem filas de mensagem, sem microsserviços, sem stack reativa (WebFlux); arquitetura é um monolito Spring Boot em camadas.
- **Reason**: Escopo do v1 é um bot de disparo agendado com dois schedulers, não um sistema distribuído; complexidade de filas/microsserviços não tem contrapartida de necessidade real neste volume.
- **Trade-off**: Escalonamento horizontal e isolamento de falha entre coleta/disparo dependem de disciplina de código (try/catch por canal, por produto), não de infraestrutura.
- **Scope**: Arquitetura geral do projeto — governa todas as features futuras.
- **Date**: 2026-09-03
- **Status**: active

### AD-004
- **Decision**: Estrutura de pacotes segue o padrão do projeto de referência `jabes-christian/freelas-spring-boot-selenium`: `scraper` / `service` / `scheduler` / `repository` / `entity` / `dto`, com uma `BaseScraper` reunindo métodos comuns de Selenium; estratégia dual de `docker-compose` (dev/prod) também reaproveitada.
- **Reason**: Reaproveitar um padrão de arquitetura já validado em produção pelo mesmo autor, em vez de desenhar um novo do zero.
- **Trade-off**: O domínio do projeto de referência é diferente; adapta-se a estrutura de pacotes, não o domínio.
- **Scope**: Organização de código de todo o projeto.
- **Date**: 2026-09-03
- **Status**: active

### AD-005
- **Decision**: Promoção é detectada primariamente pelo histórico de preço próprio, persistido no Postgres (preço atual ≤ mínimo histórico − X%); o preço "de/por" exibido pela própria página da Amazon é usado apenas como fallback de cold start, quando ainda não há histórico suficiente (< 2 coletas) para o produto.
- **Reason**: Histórico próprio é fonte de verdade robusta e imune a mudanças de layout/marketing da Amazon no preço riscado; o fallback cobre o período de aquecimento sem esperar N dias antes do primeiro disparo possível.
- **Trade-off**: Exige duas regras de decisão e uma tabela de histórico de preço por produto, em vez de uma checagem simples do "de/por" da página.
- **Scope**: Domínio de precificação e detecção de promoção — afeta entidades de produto, histórico de preço e o serviço de detecção usado tanto pela coleta quanto pelo disparo.
- **Date**: 2026-09-03
- **Status**: active

### AD-006
- **Decision**: Canais/grupos (Telegram e WhatsApp) são administrados exclusivamente via `INSERT` direto no Postgres — sem controller REST, sem endpoint de CRUD, sem painel administrativo no v1.
- **Reason**: O aplicativo é headless por definição no v1; o operador é único e técnico o bastante para rodar SQL. Adicionar uma camada web só para isso seria escopo não solicitado.
- **Trade-off**: Nenhuma validação de entrada em tempo de cadastro além das constraints do schema; erros de configuração só aparecem em runtime (log). Se o v2 precisar de operação por não-técnicos, uma camada de API/admin precisa ser desenhada à parte.
- **Scope**: Gestão da entidade `Channel`/`Group` e futura entidade de categorias/keywords de coleta.
- **Date**: 2026-09-03
- **Status**: active

### AD-007
- **Decision**: Gestão de schema do banco via migrations versionadas do Flyway (`org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql`, ambos sem versão explícita — herdados do BOM do `spring-boot-starter-parent` 4.1.1), com `spring.jpa.hibernate.ddl-auto=validate`. Nunca `update` ou `create`.
- **Reason**: D10 exige que canais e categorias/keywords sejam cadastrados via `INSERT` de seed versionado — isso só é reproduzível e auditável com migrations versionadas, não com `ddl-auto`. Confirmado via Context7 (docs oficiais do Flyway) que, a partir das versões atuais, o suporte a PostgreSQL foi extraído de `flyway-core` para o módulo `flyway-database-postgresql` (carregado via ServiceLoader) — por isso os dois artefatos são necessários, não um só.
- **Trade-off**: Toda alteração de schema exige escrever um arquivo de migration (`Vn__descricao.sql`) em vez de deixar o Hibernate inferir; ligeiramente mais cerimônia, mas elimina divergência silenciosa entre ambientes. A dependência e a primeira migration só serão adicionadas ao `pom.xml` na Tasks phase da feature que introduzir a primeira entidade JPA (`scraping-coleta`, dona de `Product`/`PriceHistory`) — adicionar o dependency agora, sem nenhuma migration, quebraria o boot da aplicação (Flyway falha se `classpath:db/migration` não existir com conteúdo).
- **Scope**: Toda a camada de persistência — resolve a pendência aberta no PRD §7 ("Gestão do schema: Flyway vs. `ddl-auto`"). Governa `scraping-coleta`, `enriquecimento-conteudo`, `canais-disparo` e a feature `operacao-docker` (que documenta o requisito, ver `OPS-16..19`).
- **Date**: 2026-09-03
- **Status**: active

### AD-008
- **Decision**: Remover `spring-boot-starter-cache` e `spring-boot-starter-cache-test` do `pom.xml`. Dependência já removida (commit pendente do usuário).
- **Reason**: AD-002/D4 já travam "sem Redis, sem cache" — a dependência estava presente no scaffold inicial do Spring Initializr sem nenhum uso previsto em nenhuma das 4 specs. Resolve a pendência aberta no PRD §7 ("`spring-boot-starter-cache` está no pom.xml mas D4 diz sem cache").
- **Trade-off**: Nenhum — nenhuma feature depende dela. Se uma necessidade real de cache surgir em v2, a dependência volta via uma nova decisão (não reabre esta).
- **Scope**: `pom.xml` do projeto inteiro.
- **Date**: 2026-09-03
- **Status**: active

### AD-009
- **Decision**: Todo limiar numérico de **negócio** (percentual mínimo de queda, faixa de sanidade de preço, intervalo entre categorias, e nas próximas features: janela de dedup, teto de produtos por canal, intervalo entre envios, timeout do LLM, limite de caracteres da copy, janela do selo "menor preço") é lido em runtime de uma tabela genérica `app_config` (chave/valor) via um `ConfigService` único e compartilhado — nunca de `@ConfigurationProperties`/`application.properties`. Seletores CSS e outros detalhes técnicos de implementação continuam em `@ConfigurationProperties` (não entram em `app_config`).
- **Reason**: Revisão pedida pelo usuário durante o Design de `scraping-coleta` — os mesmos limiares que motivaram `CategoriaColeta` ser tabela em vez de enum (O5, "0 deploy") se aplicam aos limiares de negócio: o operador quer poder testar 10% vs. 15% de desconto mínimo, ou reduzir a janela de dedup numa data de alta promoção, sem esperar restart/redeploy. `@ConfigurationProperties` exigiria reiniciar a aplicação a cada ajuste, o que contraria essa necessidade já expressa.
- **Trade-off**: Cada feature que introduzir um novo limiar de negócio precisa semear sua chave em `app_config` via a própria migration Flyway, em vez de só adicionar uma propriedade ao arquivo de config. Sem cache (AD-002/D4) — toda leitura é uma consulta indexada por chave única; volume irrelevante neste projeto (poucas leituras por ciclo agendado, não por requisição web).
- **Scope**: Toda decisão de "onde mora um número configurável" em qualquer feature futura. `app_config`/`ConfigService` nascem em `scraping-coleta` (primeira feature com Flyway) e são reusados sem alteração por `canais-disparo` e `enriquecimento-conteudo`, cada uma semeando suas próprias chaves.
- **Date**: 2026-09-03
- **Status**: active

## Handoff

- **Feature**: as 3 features com design formal aprovadas — indo para Fase 4 (Tasks), começando por `scraping-coleta`
- **Phase / Task**: `design.md` de `scraping-coleta`, `enriquecimento-conteudo` e `canais-disparo` aprovados (com 2 revisões cross-feature feitas durante a revisão: retorno de `PromotionDetectionService` → `List<CandidatoPromocaoDTO>`; `SeleniumConfig` reatribuído de `operacao-docker` para `scraping-coleta`, por dependência de compilação — `AmazonProductScraper` precisa do bean `WebDriver` e `operacao-docker` vem por último na ordem proposta). Proposta de ordem de execução (scraping-coleta → enriquecimento-conteudo → canais-disparo → operacao-docker) e estratégia de sub-agentes apresentada ao usuário, aguardando confirmação antes de escrever o 1º `tasks.md`
- **Completed**: PRD (`.specs/PRD.md`); AD-001..AD-009; `spec.md` das 4 features (SCRAPE-01..17, ENRICH-01..16, DISPATCH-01..26, OPS-01..19); `pom.xml` sem `spring-boot-starter-cache`/`-test` (AD-008); os 3 `design.md` (scraping-coleta, enriquecimento-conteudo, canais-disparo) — todos aprovados
- **In-progress**: nenhum arquivo em edição
- **Next step**: Confirmar com o usuário a ordem de execução e a estratégia de sub-agentes; então escrever `.specs/features/scraping-coleta/tasks.md` (1ª feature), rodar `validate_tasks.py`, apresentar para aprovação — mesmo ritmo usado em Spec/Design (uma feature de cada vez)
- **Blockers**: none
- **Uncommitted files**: `.specs/PRD.md`, `.specs/STATE.md`, `.specs/features/*/spec.md`, `.specs/features/*/design.md` (scraping-coleta, enriquecimento-conteudo, canais-disparo), `pom.xml` (usuário faz commit manualmente)
- **Branch**: main (sugestão: criar `docs/prd-v1` antes de commitar PRD+specs+pom.xml; um branch por feature a partir da fase de Tasks/Execute)
