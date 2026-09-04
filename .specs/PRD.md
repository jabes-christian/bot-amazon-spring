# PRD — bot-amazon-spring v1

> Fase 1 de 5 do fluxo `tlc-spec-driven` (**PRD → Spec → Design → Tasks → Execute**).
> Aprovado em 2026-09-03. Próxima fase: Spec (`.specs/features/[feature]/spec.md`).

---

## Context

O repositório hoje é só o esqueleto do Spring Initializr (`BotAmazonSpringApplication.java` + `application.properties` com uma linha) e um `pom.xml` com a stack já fechada. Não há domínio, entidade, scraper ou configuração — o projeto começa do zero, com stack e várias decisões arquiteturais já travadas pelo briefing.

O problema de negócio: divulgar promoções da Amazon manualmente em grupos de WhatsApp e canais de Telegram não escala. Encontrar a queda de preço, escrever a copy, montar a imagem, colar o link de afiliado e repetir isso para N canais é trabalho repetitivo, atrasado em relação à promoção, e propenso a repetir o mesmo produto. O resultado esperado é um bot headless que faz esse ciclo inteiro sozinho, em horários agendados, sem repetir produto no mesmo canal.

---

## 1. Visão do produto

Um monolito Spring Boot headless que, sem intervenção humana:

```
┌───────────┐   ┌────────────┐   ┌──────────────┐   ┌────────────┐
│  COLETA   │ → │  DETECÇÃO  │ → │ ENRIQUECIMTO │ → │  DISPARO   │
│ Selenium  │   │  de queda  │   │ LLM + banner │   │ TG / Zap   │
│  (cron A) │   │  de preço  │   │ + link afil. │   │  (cron B)  │
└───────────┘   └────────────┘   └──────────────┘   └────────────┘
       └──────────── dedup persistido no Postgres ──────────┘
```

## 2. Usuários

| Papel | Quem | Como interage |
| --- | --- | --- |
| **Operador / afiliado** (usuário primário, único) | Você | Configura canais, categorias e limiares via `INSERT` no Postgres. Não há UI nem API no v1. |
| **Membro do grupo/canal** (destinatário) | Assinantes dos grupos de WhatsApp e canais do Telegram | Recebe o post (banner + copy + link de afiliado). Passivo — o bot nunca lê mensagens. |

## 3. Objetivos e métricas de sucesso

| # | Objetivo | Métrica verificável no v1 |
| --- | --- | --- |
| O1 | Ciclo ponta a ponta autônomo | ≥ 1 ciclo coleta→disparo por dia sem intervenção manual |
| O2 | Nunca repetir produto no mesmo canal | 0 disparos duplicados (mesmo produto × mesmo canal) dentro da janela de repetição configurada |
| O3 | Monetização sempre presente | 100% dos links disparados contêm a tag de afiliado |
| O4 | Canal é configuração, não código | Adicionar um canal = 1 `INSERT`, 0 deploy, 0 alteração de código |
| O5 | Categoria é configuração, não código | Adicionar uma categoria/keyword = 1 `INSERT`, 0 alteração de código |
| O6 | Relevância do disparo | 100% dos produtos disparados têm queda ≥ o percentual mínimo configurado |

## 4. Escopo do v1 — épicos e prioridade

| Épico | Descrição | Prioridade |
| --- | --- | --- |
| **E1 · Coleta** | Scraper Selenium percorrendo URLs de **busca por palavra-chave** por categoria; keywords configuradas no banco. Extrai ASIN, título, preço atual, preço riscado, URL da imagem, URL do produto. | **P1** |
| **E2 · Preço & promoção** | Histórico de preço por produto no Postgres. Promoção = preço atual ≤ mínimo histórico − X% (X configurável). | **P1** |
| **E2b · Fallback cold start** | Sem histórico suficiente (< 2 coletas), usa o preço "de/por" da própria página como base de comparação. | P2 |
| **E3 · Enriquecimento** | Copy gerada por LLM (OpenRouter free via LangChain4j) + **banner composto localmente** (foto do produto, % desconto, de/por) + link de afiliado com a tag. | **P1** |
| **E4 · Canais configuráveis** | Entidade `Channel`: tipo (`TELEGRAM`/`WHATSAPP`), identificador do grupo/canal, categorias aceitas, ativo/inativo. | **P1** |
| **E5 · Disparo Telegram** | `sendPhoto` (imagem + caption) via `RestClient`. | **P1** |
| **E5b · Disparo WhatsApp** | Evolution API (envio de mídia) via `RestClient`. | P2 |
| **E6 · Agendamento** | Dois schedulers independentes: coleta e disparo, com cron externalizado em configuração. | **P1** |
| **E7 · Dedup & histórico** | `DispatchHistory` (produto × canal × data). Só entra na fila de disparo produto novo ou com nova queda relevante. | **P1** |
| **E8 · Operação** | `docker-compose` dev (app + Postgres + Chrome) e prod; segredos por variável de ambiente; log estruturado por etapa do ciclo. | **P1** (dev) / P2 (prod) |
| **E9 · Selo "menor preço em N dias"** | Badge extra no banner baseado no histórico acumulado. | P3 |
| **E10 · Acessórios de escritório** | Expansão das categorias além de tech. | P3 (só `INSERT`, se E1/E4 fizerem o trabalho certo) |

**Categorias do v1:** monitores, notebooks, periféricos, cadeira gamer, mesas.

## 5. Fora de escopo (explícito — barreira contra scope creep)

| Item | Razão |
| --- | --- |
| Amazon PA-API (API oficial) | Exige histórico de vendas de afiliado que ainda não existe. Fica para v2. |
| Redis / camada de cache | Decisão travada. Dedup vive no Postgres. |
| Filas de mensagem, microsserviços, WebFlux | Decisão travada. Monolito em camadas. |
| SDK do Telegram (`telegrambots-spring-boot-starter`) | O bot só dispara, nunca recebe. `RestClient` puro basta. |
| Recebimento de mensagens / comandos de bot | O bot é unidirecional por definição. |
| CRUD REST de canais / painel admin | Decisão desta fase: configuração via `INSERT`. App é headless no v1. |
| Métricas de clique e conversão do link | Depende do painel de afiliados da Amazon, não do bot. |
| Multi-tenant / múltiplos afiliados | Um operador, uma tag. |
| LLM com visão / geração de imagem por IA | Banner é composto localmente; modelos free com visão são instáveis. |
| Circuit breaker / backoff exponencial | Retry simples e log de falha bastam neste volume. |
| Anti-detecção avançada (proxies rotativos, resolução de CAPTCHA) | Fora do volume do v1; se a Amazon bloquear, o ciclo falha e loga. |

## 6. Decisões travadas (não reabrir nas fases seguintes)

| # | Decisão | Origem |
| --- | --- | --- |
| D1 | Java 21 + Spring Boot 4.1.1, JPA, Scheduler, Selenium + WebDriverManager, Postgres, LangChain4j (`langchain4j-open-ai` → OpenRouter), Docker | Briefing / `pom.xml` |
| D2 | Telegram e Evolution API via `RestClient` do Spring Web — sem SDK | Briefing |
| D3 | Telegram usa `sendPhoto` (imagem + caption), não `sendMessage` | Briefing |
| D4 | Sem Redis, sem cache; dedup direto no Postgres | Briefing |
| D5 | Sem filas, sem microsserviços, sem WebFlux; monolito em camadas | Briefing |
| D6 | Padrão de pacotes de `jabes-christian/freelas-spring-boot-selenium`: `scraper` / `service` / `scheduler` / `repository` / `entity` / `dto`, `BaseScraper` com métodos comuns, `docker-compose` dev+prod | Briefing |
| D7 | Coleta por **busca por palavra-chave** (`/s?k=...`) — não `/deals`, não bestsellers | Decidido nesta fase |
| D8 | Promoção detectada pelo **histórico próprio** (fonte de verdade); "de/por" da Amazon é fallback de cold start | Decidido nesta fase |
| D9 | Imagem = **banner composto localmente** a partir da foto da Amazon; LLM cuida só do texto | Decidido nesta fase |
| D10 | Canais administrados por `INSERT` no banco; sem controller, sem endpoint de negócio | Decidido nesta fase |

## 7. Premissas e questões em aberto

| Premissa / decisão | Default adotado | Racional | Confirmar? |
| --- | --- | --- | --- |
| Percentual mínimo de queda | 10% | Filtra ruído de centavos sem exigir promoção agressiva. Configurável. | y |
| Janela de não repetição (mesmo produto × mesmo canal) | 7 dias | Evita fadiga do grupo sem perder uma promoção que voltou. Configurável. | y |
| Nº máximo de produtos por disparo/canal | 5 | Evita flood e ban por rate limit no Telegram/WhatsApp. Configurável. | y |
| Formato do link de afiliado | URL canônica `/dp/{ASIN}` + `?tag={TAG}` | Padrão do programa de afiliados; sem encurtador, evita dependência externa. | y |
| Comportamento quando o LLM falha ou está indisponível | Fallback para copy por template (título + de/por + % + link); o disparo acontece mesmo assim | Modelo free do OpenRouter tem rate limit; monetização não pode depender do LLM estar de pé. | y |
| Fuso horário dos schedulers | `America/Sao_Paulo` | Público-alvo brasileiro. | y |
| Idioma da copy gerada | Português do Brasil | Público-alvo. | y |
| Gestão do schema (Flyway vs. `ddl-auto`) | **Em aberto — resolver na fase de Design** | O `pom.xml` não traz ferramenta de migration, e D10 exige `INSERT` de seed versionado. Impacta o design de persistência. | n |
| `spring-boot-starter-cache` está no `pom.xml` mas D4 diz "sem cache" | **Em aberto — resolver na fase de Design** | Dependência sem uso previsto; decidir se sai ou fica inerte. | n |

**Questões em aberto:** as duas marcadas com `n` acima, ambas deliberadamente adiadas para a fase de Design (são decisões técnicas, não de produto).

## 8. Riscos

| Risco | Impacto | Mitigação no v1 |
| --- | --- | --- |
| Amazon bloqueia o scraper (CAPTCHA / 503) | Ciclo de coleta sem resultado | Headless com user-agent realista, delay entre requisições, cron em horário disperso. Falha loga e não derruba a aplicação. |
| Seletores CSS da Amazon mudam | Coleta silenciosamente vazia | Seletores externalizados em configuração; log de "0 produtos extraídos" tratado como alerta, não como sucesso. |
| Modelo free do OpenRouter indisponível / rate-limited | Copy não gerada | Fallback para copy por template (premissa acima). |
| Instância da Evolution API desconectada | WhatsApp não recebe | Falha isolada por canal: um canal quebrado não impede os demais. |
| Rate limit / flood ban no Telegram | Canal derrubado | Intervalo entre envios + teto de produtos por disparo. |
| Preço extraído errado (parcelamento, frete, variação) | Promoção falsa disparada | Extração do preço à vista, validação de faixa e log do valor bruto extraído. |

## 9. Definição de pronto do v1

- [ ] `docker compose up` sobe app + Postgres + Chrome e o ciclo roda sozinho
- [ ] Uma coleta agendada popula produtos e histórico de preço a partir das 5 categorias
- [ ] Uma queda ≥ percentual configurado vira um disparo com banner + copy + link com tag
- [ ] Telegram e WhatsApp recebem, cada um respeitando as categorias aceitas do seu canal
- [ ] O mesmo produto não é reenviado ao mesmo canal dentro da janela configurada
- [ ] Adicionar um canal novo exige apenas um `INSERT`

---

## Próximo passo

Fase 2 (Spec): `spec.md` com user stories priorizadas (P1/P2/P3), critérios de aceite em EARS e IDs de rastreabilidade — a apresentar para aprovação antes de qualquer Design.
