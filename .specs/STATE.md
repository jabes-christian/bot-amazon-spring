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

## Handoff

- **Feature**: bot-amazon-spring v1 — Spec (4 features: scraping-coleta, enriquecimento-conteudo, canais-disparo, operacao-docker)
- **Phase / Task**: Fase 2 (Spec) concluída para as 4 features — `validate_spec.py` passou limpo (0 erros, 0 warnings) nas 4 — aguardando aprovação do usuário
- **Completed**: PRD (`.specs/PRD.md`), AD-001..AD-006, `spec.md` das 4 features com stories P1/P2/P3, ACs em EARS e traceability (SCRAPE-01..17, ENRICH-01..16, DISPATCH-01..26, OPS-01..15)
- **In-progress**: nenhum arquivo em edição
- **Next step**: Após aprovação do usuário, iniciar Fase 3 (Design) por feature — avaliar se cada uma precisa de `design.md` formal (tier Large/Complex) ou pode seguir direto para Tasks/Execute (tier Medium)
- **Blockers**: none
- **Uncommitted files**: `.specs/PRD.md`, `.specs/STATE.md`, `.specs/features/*/spec.md` (usuário faz commit manualmente)
- **Branch**: main (sugestão: criar `docs/prd-v1` antes de commitar PRD; um branch por feature a partir da fase de Tasks/Execute)
