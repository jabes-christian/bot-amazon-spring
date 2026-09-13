# Operação & Docker Specification

## Problem Statement

O bot precisa rodar de forma reproduzível em desenvolvimento (IDE + hot-reload) e em produção (tudo containerizado), sem credencial hardcoded e com visibilidade suficiente em log para diagnosticar falhas de coleta, LLM ou disparo sem instrumentação externa. Esta feature define a infraestrutura operacional, reaproveitando o padrão dual dev/prod do projeto de referência (D6).

## Goals

- [ ] `docker compose up` (dev) sobe as dependências e permite rodar o app localmente pela IDE
- [ ] `docker compose -f docker-compose.prod.yml up` sobe a aplicação inteira containerizada
- [ ] Nenhuma credencial fica hardcoded no código ou versionada no compose

## Out of Scope

| Feature | Reason |
| --- | --- |
| Orquestração Kubernetes | Fora do volume do v1; docker-compose é suficiente para um único operador |
| CI/CD automatizado | Não solicitado no PRD; build e deploy continuam manuais no v1 |
| Métricas/observabilidade externa (Prometheus, Grafana) | PRD não pede; logs estruturados bastam no v1 |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Base de imagem da aplicação | `eclipse-temurin:21-jre-alpine`, igual ao Dockerfile do projeto de referência | Imagem enxuta já validada em produção pelo mesmo autor (D6) | y |
| Rede Docker | Uma rede `bridge` dedicada, compartilhada entre os serviços do compose (dev e prod) | Isola os serviços deste projeto de outras stacks Docker na mesma máquina | y |
| Demais dimensões (auth/rate limit de API própria, concorrência de domínio, state-transition de negócio, input de usuário final) | N/A para esta feature | Esta spec trata de infraestrutura operacional; essas dimensões pertencem às features de domínio (`scraping-coleta`, `canais-disparo`) | y |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Ambiente de desenvolvimento via Docker Compose ⭐ MVP

**User Story**: Como operador, quero subir apenas as dependências de infraestrutura (Postgres) em container no ambiente de desenvolvimento, para rodar a aplicação localmente pela IDE com hot-reload, seguindo o padrão do projeto de referência.

**Why P1**: É o modo de trabalho do dia a dia durante o desenvolvimento do v1.

**Acceptance Criteria**:

1. THE sistema SHALL fornecer um `docker-compose.yml` de desenvolvimento que sobe apenas o serviço Postgres, com variáveis de ambiente lidas de um arquivo `.env` não versionado.
2. THE sistema SHALL expor um healthcheck no serviço Postgres do compose de dev, permitindo aguardar o banco estar pronto antes de qualquer conexão da aplicação.
3. WHILE a propriedade `selenium.remote.url` estiver vazia (perfil de desenvolvimento) THE sistema SHALL usar um ChromeDriver local, provisionado automaticamente via WebDriverManager.

**Independent Test**: Rodar `docker compose up` no diretório do projeto e confirmar que apenas o container do Postgres sobe e fica `healthy`.

---

### P1: Ambiente de produção via Docker Compose ⭐ MVP

**User Story**: Como operador, quero subir a aplicação inteira containerizada (app + Postgres + Chrome headless) em produção, para não depender de infraestrutura manual no servidor.

**Why P1**: É como o bot roda de fato em operação contínua.

**Acceptance Criteria**:

1. THE sistema SHALL fornecer um `docker-compose.prod.yml` que sobe três serviços: `postgres`, `selenium` (imagem `selenium/standalone-chrome`) e `app` (build a partir do `Dockerfile` do projeto).
2. THE sistema SHALL configurar o serviço `app` para depender da condição `service_healthy` dos serviços `postgres` e `selenium` antes de iniciar.
3. WHILE a propriedade `selenium.remote.url` estiver preenchida, apontando para o serviço `selenium` (perfil de produção) THE sistema SHALL usar `RemoteWebDriver` em vez de ChromeDriver local.
4. THE sistema SHALL definir `restart: unless-stopped` para os três serviços do compose de produção.
5. THE sistema SHALL persistir os dados do Postgres em um volume nomeado, garantindo que os dados sobrevivam a um `docker compose down` sem a flag `-v`.

**Independent Test**: Rodar `docker compose -f docker-compose.prod.yml up` e confirmar que o serviço `app` só inicia depois de `postgres` e `selenium` reportarem `healthy`.

---

### P1: Segredos via variáveis de ambiente ⭐ MVP

**User Story**: Como operador, quero que nenhuma credencial (tokens, chaves de API, senha de banco) fique hardcoded no código ou no `docker-compose`, para poder trocar credenciais sem tocar em código versionado.

**Why P1**: Requisito básico de segurança e de operação — tokens de Telegram, Evolution API e OpenRouter não podem ir para o repositório.

**Acceptance Criteria**:

1. THE sistema SHALL ler todas as credenciais e endpoints externos (Postgres, Telegram Bot Token, Evolution API URL/Key, OpenRouter API Key, tag de afiliado) de variáveis de ambiente.
2. THE sistema SHALL fornecer um arquivo `.env.example` versionado com placeholders, mantendo o `.env` real fora do controle de versão.
3. IF uma variável de ambiente obrigatória estiver ausente na inicialização THEN o sistema SHALL falhar de forma explícita no boot (fail-fast), registrando qual variável está faltando, em vez de iniciar em estado parcialmente configurado.

**Independent Test**: Remover uma variável obrigatória do `.env` e confirmar que a aplicação falha ao subir, com mensagem indicando a variável ausente.

---

### P1: Log estruturado por etapa do ciclo ⭐ MVP

**User Story**: Como operador, quero logs claros por etapa (coleta, detecção, enriquecimento, disparo), para diagnosticar problemas de produção sem instrumentação externa.

**Why P1**: É a única ferramenta de observabilidade do v1 (PRD exclui Prometheus/Grafana).

**Acceptance Criteria**:

1. THE sistema SHALL prefixar cada linha de log relevante com a etapa do ciclo a que pertence (`COLETA`, `DETECCAO`, `ENRIQUECIMENTO`, `DISPARO`).
2. THE sistema SHALL registrar, ao final de cada ciclo (coleta ou disparo), um resumo com contagens de itens processados, sucessos e falhas.

**Independent Test**: Rodar um ciclo completo de coleta e disparo e confirmar, pelos logs, um resumo final com as contagens de cada etapa.

---

### P1: Gestão de schema via migrations Flyway ⭐ MVP

**User Story**: Como operador, quero que o schema do banco seja versionado por migrations reproduzíveis, para nunca depender de inferência automática de schema (`ddl-auto`) e ter os `INSERT` de seed de canais/categorias (D10) rastreados junto com o schema que eles pressupõem.

**Why P1**: Resolve a pendência aberta no PRD §7 (Flyway vs. `ddl-auto`) — decisão registrada em `.specs/STATE.md` (AD-007) e formalizada aqui como requisito da feature que a executa.

**Acceptance Criteria**:

1. THE sistema SHALL gerenciar o schema do banco exclusivamente via migrations versionadas do Flyway em `src/main/resources/db/migration`, nunca via `spring.jpa.hibernate.ddl-auto=update` ou `create`.
2. THE sistema SHALL configurar `spring.jpa.hibernate.ddl-auto=validate`, garantindo que o schema real seja sempre exatamente o definido pelas migrations Flyway.
3. THE sistema SHALL versionar no controle de versão toda migration que insira dados de seed (ex.: canais e categorias exigidos por D10), junto com a migration de schema que ela pressupõe.
4. IF a aplicação subir sem que uma migration pendente tenha sido aplicada com sucesso THEN o sistema SHALL falhar o boot (fail-fast), nunca subir com schema divergente do esperado pelo código.

**Independent Test**: Remover uma coluna esperada via migration ausente e confirmar que a aplicação falha o boot em vez de subir com schema incompleto.

---

## Edge Cases

- IF o serviço Postgres não atingir o healthcheck dentro do tempo configurado THEN o serviço `app` SHALL não iniciar, respeitando o comportamento padrão do `depends_on: condition: service_healthy` do Docker Compose.
- IF o serviço `selenium` não atingir o healthcheck dentro do tempo configurado (perfil de produção) THEN o serviço `app` SHALL não iniciar, pelo mesmo mecanismo.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| OPS-01 | P1: Ambiente de desenvolvimento | Design | Pending |
| OPS-02 | P1: Ambiente de desenvolvimento | Design | Pending |
| OPS-03 | P1: Ambiente de desenvolvimento | Design | Pending |
| OPS-04 | P1: Ambiente de produção | Design | Pending |
| OPS-05 | P1: Ambiente de produção | Design | Pending |
| OPS-06 | P1: Ambiente de produção | Design | Pending |
| OPS-07 | P1: Ambiente de produção | Design | Pending |
| OPS-08 | P1: Ambiente de produção | Design | Pending |
| OPS-09 | P1: Segredos via variáveis de ambiente | Execute | Implementing |
| OPS-10 | P1: Segredos via variáveis de ambiente | Execute | Implementing |
| OPS-11 | P1: Segredos via variáveis de ambiente | Execute | Implementing |
| OPS-12 | P1: Log estruturado por etapa | Execute | Implementing |
| OPS-13 | P1: Log estruturado por etapa | Design | Pending |
| OPS-14 | Edge case: healthcheck Postgres | Design | Pending |
| OPS-15 | Edge case: healthcheck Selenium | Design | Pending |
| OPS-16 | P1: Gestão de schema via Flyway | Design | Pending |
| OPS-17 | P1: Gestão de schema via Flyway | Design | Pending |
| OPS-18 | P1: Gestão de schema via Flyway | Design | Pending |
| OPS-19 | P1: Gestão de schema via Flyway | Design | Pending |

**ID format:** `OPS-NN`

**Status values:** Pending → In Design → In Tasks → Implementing → Verified

**Coverage:** 19 total, 0 mapped to tasks, 19 unmapped ⚠️ (Design/Tasks ainda não iniciados)

---

## Success Criteria

- [ ] `docker compose up` sobe app + Postgres + Chrome e o ciclo roda sozinho (definição de pronto do PRD)
- [ ] Nenhuma credencial aparece hardcoded em código ou em arquivo versionado
- [ ] Logs de um ciclo completo permitem identificar, sem debugger, em qual etapa uma falha ocorreu
