# Scraping & Coleta Specification

## Problem Statement

O bot precisa descobrir produtos das 5 categorias-alvo (monitores, notebooks, periféricos, cadeira gamer, mesas) na Amazon e saber quando um deles está mais barato do que já esteve, sem qualquer URL ou preço hardcoded no código. Sem essa camada, não há matéria-prima para o enriquecimento e o disparo.

## Goals

- [ ] Coletar produtos por categoria configurada, sem alterar código para adicionar/remover categoria (O5 do PRD)
- [ ] Manter histórico de preço suficiente para detectar queda relevante com fonte de verdade própria (D8)
- [ ] Nunca travar o ciclo inteiro por falha de uma única categoria

## Out of Scope

| Feature | Reason |
| --- | --- |
| Amazon PA-API | Decisão de PRD (D1/§5) — exige histórico de vendas de afiliado, fica para v2 |
| Coleta via `/deals` ou bestsellers | D7 trava coleta por busca de palavra-chave (`/s?k=...`) |
| Paginação de resultados de busca | v1 processa apenas a 1ª página por categoria (ver Assumptions) |
| Anti-detecção avançada (proxies, resolução de CAPTCHA) | Fora do volume do v1 (PRD §5); falha de coleta apenas loga |
| Enriquecimento de texto/imagem | Feature separada `enriquecimento-conteudo` |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Processamento de categorias | Sequencial, uma categoria por vez, com intervalo configurável entre elas | Reduz risco de bloqueio por rate limit da Amazon e simplifica o uso de uma única sessão de WebDriver por vez | y |
| Paginação da busca por keyword | Processa apenas a 1ª página de resultados por categoria no v1 | Volume suficiente para 5 categorias; paginação fica para v2 se o volume exigir | y |
| Limite de sanidade de preço | Descarta preço extraído ≤ R$ 0,00 ou > R$ 50.000,00 | Protege contra erro de extração (parcelamento, frete, seletor errado) virar promoção falsa | y |
| Retenção do histórico de preço | Indefinida no v1 (sem expiração/purge automático) | Necessário para o selo "menor preço em N dias" (feature enriquecimento) e para auditoria | y |
| Retry de categoria com falha | Sem retry dentro do mesmo ciclo; nova tentativa só no próximo ciclo agendado | Simplicidade; falha isolada de uma categoria não deve atrasar as demais | y |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Coleta de produtos por categoria configurável ⭐ MVP

**User Story**: Como operador, quero que o sistema colete produtos da Amazon usando keywords de busca configuradas por categoria no banco, para manter o catálogo atualizado sem alterar código quando eu adicionar uma categoria.

**Why P1**: Sem coleta não há produtos para detectar promoção nem para disparar.

**Acceptance Criteria**:

1. WHEN o scheduler de coleta invoca uma execução THEN o sistema SHALL buscar, para cada categoria ativa cadastrada, os produtos retornados na 1ª página de resultados da busca por palavra-chave da Amazon (`/s?k={keyword}`) associada a essa categoria.
2. THE sistema SHALL extrair de cada produto encontrado: ASIN, título, preço atual, preço riscado (quando presente), URL da imagem principal e URL do produto.
3. IF uma categoria não retornar nenhum produto na busca THEN o sistema SHALL registrar log de nível WARN identificando categoria e keyword, e SHALL continuar processando as demais categorias.
4. IF o preço extraído de um produto for menor ou igual a R$ 0,00 OU maior que R$ 50.000,00 THEN o sistema SHALL descartar esse produto do ciclo atual e SHALL registrar log de WARN com o ASIN e o valor bruto extraído.
5. WHEN um produto com o mesmo ASIN já existe no catálogo THEN o sistema SHALL atualizar seus dados (título, preço, imagem) em vez de criar um registro duplicado.
6. THE sistema SHALL aguardar um intervalo configurável entre a busca de categorias distintas, para reduzir o risco de bloqueio por limite de requisições da Amazon.

**Independent Test**: Cadastrar 1 categoria com sua keyword, rodar uma coleta manual e confirmar que produtos aparecem no catálogo com ASIN, preço e imagem preenchidos.

---

### P1: Histórico de preço por produto ⭐ MVP

**User Story**: Como operador, quero que cada coleta registre o preço observado no histórico do produto, para que quedas de preço possam ser detectadas ao longo do tempo.

**Why P1**: É a fonte de verdade da detecção de promoção (D8) — sem histórico, não há queda para detectar.

**Acceptance Criteria**:

1. WHEN uma coleta processa um produto (novo ou já existente) THEN o sistema SHALL registrar uma entrada de histórico de preço com o ASIN, o preço extraído e o timestamp da coleta.
2. THE sistema SHALL manter o histórico de preço de um produto indefinidamente, sem expiração automática no v1.
3. IF duas ou mais coletas ocorrerem para a mesma categoria em dias diferentes THEN o sistema SHALL registrar uma entrada de histórico por coleta, nunca deduplicando por dia.

**Independent Test**: Rodar duas coletas em execuções distintas e confirmar duas entradas de histórico para o mesmo ASIN, com timestamps diferentes.

---

### P1: Detecção de queda de preço relevante ⭐ MVP

**User Story**: Como operador, quero que o sistema identifique quando um produto tem queda de preço relevante em relação ao seu histórico, para sinalizá-lo como candidato a disparo.

**Why P1**: É o gatilho de todo o ciclo de enriquecimento e disparo — sem detecção, nada é enviado.

**Acceptance Criteria**:

1. WHEN um produto tem 2 ou mais entradas de histórico de preço THEN o sistema SHALL considerar queda relevante quando o preço atual for menor ou igual ao menor preço já registrado no histórico multiplicado por (1 − percentual mínimo configurado).
2. THE sistema SHALL usar 10% como percentual mínimo padrão de queda, configurável sem alteração de código.
3. WHEN um produto é sinalizado como queda relevante THEN o sistema SHALL disponibilizá-lo como candidato para o ciclo de disparo (consumido pela feature `canais-disparo`).
4. IF um produto não teve seu preço reduzido desde a última vez em que foi sinalizado como candidato THEN o sistema SHALL não sinalizá-lo novamente, evitando reprocessar o mesmo patamar de preço.

**Independent Test**: Inserir manualmente 2 preços históricos para o mesmo ASIN com queda ≥ 10% e confirmar que o produto é sinalizado como candidato.

---

### P2: Fallback de detecção em cold start

**User Story**: Como operador, quero que produtos recém-descobertos (sem histórico suficiente) também possam ser sinalizados usando o preço "de/por" da própria página, para não perder promoções nos primeiros dias de operação de uma categoria.

**Why P2**: Cobre o período de aquecimento sem exigir esperar N coletas antes do primeiro disparo possível; não bloqueia o MVP porque a fonte de verdade principal (histórico) já funciona sem ele.

**Acceptance Criteria**:

1. IF um produto tem menos de 2 entradas de histórico de preço E a página exibe um preço riscado (de/por) THEN o sistema SHALL considerar queda relevante quando o preço atual for menor ou igual ao preço riscado multiplicado por (1 − percentual mínimo configurado).
2. IF um produto tem menos de 2 entradas de histórico E a página não exibe preço riscado THEN o sistema SHALL não sinalizar esse produto como candidato, por falta de base de comparação.

**Independent Test**: Inserir um produto novo (1 única coleta) com preço riscado ≥ 10% acima do preço atual e confirmar que é sinalizado como candidato mesmo sem histórico suficiente.

---

## Edge Cases

- IF o WebDriver falhar ao carregar a página de busca (timeout, erro de conexão) THEN o sistema SHALL registrar log de ERROR com a categoria afetada e SHALL continuar as demais categorias sem interromper o ciclo.
- IF a Amazon retornar página de bloqueio/CAPTCHA THEN o sistema SHALL tratar como falha de coleta daquela categoria, com o mesmo comportamento do item acima.
- WHEN o número de produtos extraídos em todo o ciclo de coleta for zero THEN o sistema SHALL registrar log de nível WARN, tratado como alerta operacional e não como erro fatal.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| SCRAPE-01 | P1: Coleta por categoria | Execute | ✅ Verified |
| SCRAPE-02 | P1: Coleta por categoria | Execute | ✅ Verified |
| SCRAPE-03 | P1: Coleta por categoria | Execute | ❌ Needs Fix (ver `validation.md` — WARN "categoria sem resultado" é inatingível no caminho real do Selenium) |
| SCRAPE-04 | P1: Coleta por categoria | Execute | ⚠️ Verified (gap de precisão de teste — log-content não asserido) |
| SCRAPE-05 | P1: Coleta por categoria | Execute | ✅ Verified |
| SCRAPE-06 | P1: Coleta por categoria | Execute | ✅ Verified |
| SCRAPE-07 | P1: Histórico de preço | Execute | ⚠️ Verified (asserção só conta invocações, não conteúdo salvo) |
| SCRAPE-08 | P1: Histórico de preço | Execute | ⚠️ Verified (requisito negativo, não testável diretamente) |
| SCRAPE-09 | P1: Histórico de preço | Execute | ✅ Verified |
| SCRAPE-10 | P1: Detecção de queda | Execute | ✅ Verified |
| SCRAPE-11 | P1: Detecção de queda | Execute | ⚠️ Verified (valor-padrão 10% seedado mas nunca lido de volta em teste) |
| SCRAPE-12 | P1: Detecção de queda | Execute | ✅ Verified |
| SCRAPE-13 | P1: Detecção de queda | Execute | ✅ Verified |
| SCRAPE-14 | P2: Fallback cold start | Execute | ✅ Verified |
| SCRAPE-15 | P2: Fallback cold start | Execute | ✅ Verified |
| SCRAPE-16 | Edge case: falha WebDriver/CAPTCHA | Execute | ⚠️ Verified (gap de precisão de teste — log-content não asserido) |
| SCRAPE-17 | Edge case: zero produtos no ciclo | Execute | ✅ Verified |

**ID format:** `SCRAPE-NN`

**Status values:** Pending → In Design → In Tasks → Implementing → Verified

**Coverage:** 17 total, 17 mapped to tasks (T1-T12), 1 needs fix (SCRAPE-03), 5 verified with flagged gaps, 11 cleanly verified — ver `.specs/features/scraping-coleta/validation.md` para evidência completa

---

## Success Criteria

- [ ] Uma coleta agendada popula produtos e histórico de preço a partir das 5 categorias do v1
- [ ] Uma queda ≥ percentual configurado gera exatamente um candidato disponível para disparo
- [ ] Falha em uma categoria (WebDriver, CAPTCHA, zero resultados) nunca interrompe as demais
