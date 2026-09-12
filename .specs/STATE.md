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
- **Decision**: Gestão de schema do banco via migrations versionadas do Flyway (`org.springframework.boot:spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql`, ambos sem versão explícita — herdados do BOM do `spring-boot-starter-parent` 4.1.1), com `spring.jpa.hibernate.ddl-auto=validate`. Nunca `update` ou `create`.
- **Reason**: D10 exige que canais e categorias/keywords sejam cadastrados via `INSERT` de seed versionado — isso só é reproduzível e auditável com migrations versionadas, não com `ddl-auto`. Confirmado via Context7 (docs oficiais do Flyway) que, a partir das versões atuais, o suporte a PostgreSQL foi extraído de `flyway-core` para o módulo `flyway-database-postgresql` (carregado via ServiceLoader) — por isso os dois artefatos são necessários, não um só.
- **Trade-off**: Toda alteração de schema exige escrever um arquivo de migration (`Vn__descricao.sql`) em vez de deixar o Hibernate inferir; ligeiramente mais cerimônia, mas elimina divergência silenciosa entre ambientes. A dependência e a primeira migration só serão adicionadas ao `pom.xml` na Tasks phase da feature que introduzir a primeira entidade JPA (`scraping-coleta`, dona de `Product`/`PriceHistory`) — adicionar o dependency agora, sem nenhuma migration, quebraria o boot da aplicação (Flyway falha se `classpath:db/migration` não existir com conteúdo).
- **Scope**: Toda a camada de persistência — resolve a pendência aberta no PRD §7 ("Gestão do schema: Flyway vs. `ddl-auto`"). Governa `scraping-coleta`, `enriquecimento-conteudo`, `canais-disparo` e a feature `operacao-docker` (que documenta o requisito, ver `OPS-16..19`).
- **Date**: 2026-09-03
- **Correção (2026-09-07, durante Execute de T1/scraping-coleta)**: a decisão original (Design) previa só `org.flywaydb:flyway-core` + `flyway-database-postgresql`. Ao rodar o gate de T1, o contexto subia normalmente mas o Flyway nunca era acionado (nenhum log, migration não aplicada, 0 tabelas criadas) — Spring Boot 4 moveu o `FlywayAutoConfiguration` para um módulo próprio (`spring-boot-flyway`), que `flyway-core` sozinho não traz. Confirmado via Context7 (fonte do próprio `spring-boot`, `starter/spring-boot-starter-flyway/build.gradle`) que o artefato correto é o starter `org.springframework.boot:spring-boot-starter-flyway` (traz `spring-boot-flyway` + `flyway-core` + `spring-boot-starter-jdbc` transitivamente); `flyway-database-postgresql` continua explícito à parte (é `optional` dentro do módulo, não se propaga). `pom.xml` e `tasks.md`/T1 corrigidos para refletir isso. Este é o tipo de gap que só aparece rodando o gate de verdade — nenhuma pesquisa via Context7 antes do Execute havia sinalizado essa modularização.
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

## Deferred Ideas

Itens que não bloqueiam a feature corrente, capturados para não se perderem no histórico do chat (não são Decisions — nada aqui foi decidido, só sinalizado para revisão futura).

- **Gate pré-produção: seletores CSS de `AmazonSelectorsProperties` ainda não foram validados contra a Amazon real** (registrado durante Execute de `scraping-coleta`, após T10): os valores em `AmazonSelectorsProperties` (container do card, ASIN, título, preço atual, preço riscado, imagem, link) são os candidatos não verificados listados em `design.md` §"Validação de Seletores Amazon" — nenhum navegador esteve disponível nesta sessão para confirmá-los ao vivo. Isso **não bloqueia** T11/T12 nem o resto da Fase 3/4 desta feature, porque `ColetaService` e `PromotionDetectionService` são desenvolvidos e testados em cima do contrato (`AmazonProductScraper.buscarPorKeyword` mockado), não do scraper real. **Bloqueia, sim, a Definição de Pronto do PRD** ("uma coleta agendada popula produtos... a partir das 5 categorias") — antes de rodar o ciclo de coleta contra `amazon.com.br` de verdade (seja manualmente ou via `docker compose up` de `operacao-docker`), o procedimento de validação manual via DevTools descrito em `design.md` precisa ser executado, e `AmazonSelectorsProperties`/`application.properties` ajustados com os valores confirmados. Não resolvido agora — só registrado como pré-requisito explícito antes do primeiro uso real.

- **Avaliar `spring.config.import=optional:file:.env[.properties]` para credenciais reais em dev local** (registrado durante Execute de `enriquecimento-conteudo`, após T3): `LlmConfig` (T3) introduziu defaults vazios/públicos para `openrouter.base-url`/`openrouter.api-key`/`openrouter.model` só para não quebrar testes de contexto completo sem essas env vars — mas isso significa que, em dev local (fora de Docker/CI), o `ChatModel` real nunca tem credenciais de verdade a menos que o operador exporte as env vars manualmente na sessão do shell antes de rodar a aplicação. O projeto de referência (`jabes-christian/freelas-spring-boot-selenium`) usa `spring.config.import=optional:file:.env[.properties]` para carregar um `.env` local automaticamente nesse cenário. **Candidato natural para `operacao-docker`** (feature dona de `.env.example`/OPS-09/OPS-10) avaliar quando essa feature for desenhada — decidir se adota esse import, e como isso interage com a estratégia de env vars via `docker-compose` (que não precisa do `.env` sendo lido pelo Spring, já injeta via ambiente do container). Não resolvido agora — só registrado, não bloqueia `enriquecimento-conteudo`.

- **WebDriver instanciado em todo `@SpringBootTest` de contexto completo** (descoberto durante Execute de `scraping-coleta`, T7): sem `spring.main.lazy-initialization`, o bean `WebDriver` de `SeleniumConfig` sobe eagerly em qualquer teste de contexto completo — inclusive testes que nada têm a ver com Selenium (ex.: `BotAmazonSpringApplicationIT`, que só testa a migration Flyway). Hoje não bloqueia nada porque a máquina de dev tem Chrome instalado e o WebDriverManager resolve o driver rápido, mas isso torna `mvn verify` local dependente de um Chrome instalável, não só de Docker — uma dependência ambiental implícita do gate que não está documentada em lugar nenhum. **Candidato natural para `operacao-docker`** (feature dona do ambiente de execução/CI) avaliar quando essa feature for desenhada: opções possíveis incluem `spring.main.lazy-initialization=true` (efeito colateral: atrasa a detecção de erro de wiring de outros beans para o primeiro uso, não na subida), um profile de teste que sobrepõe o bean `WebDriver` por um mock/stub, ou simplesmente documentar Chrome como pré-requisito de ambiente de dev. Não resolvido agora — só registrado.

- **Preço "de" do banner renderizado como texto plano, sem tachado literal** (registrado pelo Verifier na iteração 3 de `enriquecimento-conteudo`, 2026-09-10): ENRICH-08 no `spec.md` usa o parêntese "(riscado)" para o preço "de", que na convenção de e-commerce brasileiro costuma indicar um tachado visual literal sobre o valor. `BannerImageService.compor()` desenha `"De: R$ X / Por: R$ Y"` como uma única linha de texto plano — sem linha de tachado nem atributo de fonte riscada. Não afeta o conteúdo substantivo da promoção (percentual, ambos os preços e o link de afiliado estão corretos e presentes) — por isso o Verifier classificou como cosmético/não-bloqueante e a feature fechou com PASS mesmo assim. Se o usuário quiser o tachado literal no banner, é um follow-up pequeno e isolado em `BannerImageService.compor()` (ex.: uma segunda `drawString`/linha do `Graphics2D` sobre o preço "de", ou usar `TextAttribute.STRIKETHROUGH` na fonte) — não reabre ENRICH-08 nem o Verifier desta feature. Não resolvido agora — só registrado (usuário optou por deixar para depois).

## Handoff

- **Feature**: `scraping-coleta` — **Done** (15/15 tasks, Verifier PASS iteração 2). `enriquecimento-conteudo` — **Done** (8/8 tasks, Verifier PASS iteração 3, 16/16 ACs, gate 65/65, sensor 2/2 mortos — `validation.md`). `canais-disparo` — **Done** (9/9 tasks, Verifier PASS iteração 1 — 26/26 DISPATCH ACs + ENRICH-07 verificados, gate 93/93, sensor 7/7 mortos incluindo 5 mutações dedicadas em `DisparoService` — `validation.md`). Ordem confirmada: scraping-coleta → enriquecimento-conteudo → canais-disparo → operacao-docker. Próxima feature: `operacao-docker`.
- **Phase / Task**: `operacao-docker` — Tasks (feature vai direto de Specify para Tasks, sem `design.md` formal — decisão do usuário em 2026-09-12, aplicável só a esta feature). Última ação concluída: `tasks.md` (9 tasks, 4 fases) escrito e validado estruturalmente (`validate_tasks.py`: 0 erros, 9 warnings — todos granularidade de lote coeso, já justificados por escrito na seção "Task Granularity Check" do próprio arquivo). `tasks.md` foi apresentado ao usuário no chat; **sessão pausada antes de receber aprovação explícita** ("aprovado"/"pode seguir para T1") — não presumir aprovação na retomada, confirmar antes de iniciar Execute.
- **Próxima ação concreta**: na retomada, confirmar com o usuário se `tasks.md` de `operacao-docker` está aprovado; só então iniciar Execute a partir de T1. Decisões de design travadas no próprio `tasks.md` (substituindo o `design.md` ausente, todas já respondidas pelo usuário nesta sessão via `AskUserQuestion`, nenhuma pendente de revisão): (1) fail-fast via `EnvironmentPostProcessor` dedicado (não `@Component`/`@PostConstruct`) listando todas as vars ausentes de uma vez; (2) prefixo de log mantém a convenção `ETAPA: mensagem` já existente em 15/16 mensagens, preenchendo lacunas (DETECCAO sem log nenhum, ColetaScheduler sem log, AmazonProductScraper engolindo falhas em silêncio); (3) Deferred Idea `.env` local resolvida via `spring.config.import=optional:file:.env[.properties]`; (4) Deferred Idea `WebDriver` eager resolvida via `@Lazy` em 3 pontos — o `@Bean webDriver`, o parâmetro `WebDriver` de `SeleniumConfig.webDriverWait`, e o parâmetro `WebDriver` do construtor de `AmazonProductScraper` (só o bean não bastaria). Achado adicional registrado no `tasks.md` (Decisão de Design #5): não existe `spring.datasource.*` em lugar nenhum do projeto hoje — a app só sobe em teste via Testcontainers; T1 resolve isso.
- **Completed**: PRD (`.specs/PRD.md`); AD-001..AD-009; `spec.md` das 4 features (SCRAPE-01..17, ENRICH-01..16, DISPATCH-01..26 — todos 100% Verified; OPS-01..19 ainda Pending); `pom.xml` sem `spring-boot-starter-cache`/`-test` (AD-008); os 3 `design.md` (scraping-coleta, enriquecimento-conteudo, canais-disparo); **`scraping-coleta` inteira: `tasks.md` (15 tasks) + implementação completa (47+ testes) + `validation.md` (PASS)**; **`enriquecimento-conteudo` inteira: `tasks.md` (8 tasks) + implementação completa (65 testes) + `validation.md` (PASS, iteração 3)**; **`canais-disparo` inteira: `design.md` + `tasks.md` (9/9 tasks, status Done) + implementação completa (migration V3, `Channel`/`TipoCanal`/`ChannelRepository`, `DispatchHistory`/`DispatchHistoryRepository`, `ChannelSender`/`EnvioException`, `TelegramChannelSender`, `WhatsAppChannelSender`, `DisparoService`, `ColetaScheduler`+`@EnableScheduling`, `DisparoScheduler`) + `validation.md` (PASS, iteração 1 — 93 testes totais no projeto, 65 unit + 28 integration, 0 falhas)**.
- **In-progress**: nenhum arquivo em edição
- **Blockers**: none (mas ver Deferred Ideas: validação manual dos seletores CSS via DevTools ainda pendente antes de rodar coleta contra a Amazon real; nota cosmética ENRICH-08 sobre "(riscado)" — nenhuma bloqueia a próxima feature)
- **Regra de processo confirmada pelo usuário (Execute)**: nunca rodar comandos git. Ao final de cada task: apresentar gate check + done-when cumprido + mensagem de commit sugerida; aguardar o usuário confirmar que o commit foi feito manualmente antes de seguir para a task seguinte. Nunca avançar sem essa confirmação explícita. Vale também para ciclos fix→re-verify. Mensagens de commit sugeridas neste projeto não incluem rodapé de atribuição (`Co-Authored-By`/`Claude-Session`) — instrução explícita do usuário durante Execute de `enriquecimento-conteudo`.
- **Uncommitted files** (confirmado via `git status --porcelain` nesta sessão): `.specs/features/operacao-docker/tasks.md` (novo, `??`), `.specs/STATE.md` (modificado, `M`) — nenhum dos dois commitado ainda; nenhuma implementação de código começou (T1 não iniciada). Último commit real: `01fb69d` (docs de fechamento de `canais-disparo`), sem nenhum commit nesta sessão de `operacao-docker`.
- **Branch**: `tasks/operacao-docker` (branch atual)

## Lições relevantes (LESSONS.md)

- **L-004** (candidate, scope `timing`, registrada pelo Verifier de `canais-disparo`): verificar que um valor de config de intervalo/espera é lido com o default correto não prova que a espera de fato acontece — para provar isso, seria preciso asserir a própria invocação do sleep/wait, ou documentar a lacuna explicitamente. Relevante para qualquer feature futura com `Thread.sleep`/intervalo configurável.
