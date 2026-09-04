# Enriquecimento de Conteúdo Specification

## Problem Statement

Um produto candidato (ASIN, título, preço, imagem) não é um post pronto para disparo. É preciso transformá-lo em copy atrativa em português e em um banner visual com desconto e preço de/por, sempre com o link de afiliado presente — e isso não pode depender 100% de um LLM gratuito com disponibilidade instável.

## Goals

- [ ] Gerar copy em pt-BR via LLM (OpenRouter free, LangChain4j) para todo produto candidato
- [ ] Garantir que 100% dos disparos contenham o link de afiliado com a tag configurada, mesmo se o LLM falhar (O3 do PRD)
- [ ] Compor localmente um banner com foto do produto, % de desconto e preço de/por

## Out of Scope

| Feature | Reason |
| --- | --- |
| LLM com visão / geração de imagem por IA | D9 trava banner composto localmente; modelos free com visão são instáveis |
| Encurtador de link | Premissa do PRD (§7) — URL canônica `/dp/{ASIN}?tag=...`, sem dependência externa |
| Circuit breaker / backoff exponencial no LLM | PRD §5 — timeout + fallback simples bastam neste volume |
| Seleção de canais / regras de dedup | Feature separada `canais-disparo` |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Processamento de enriquecimento | Sequencial, um produto por vez | Simplicidade e menor pressão simultânea sobre o rate limit do LLM free | y |
| Timeout de chamada ao LLM | 15 segundos, configurável | Equilíbrio entre dar chance ao modelo free responder e não atrasar o ciclo de disparo | y |
| Limite de caracteres da copy | Respeita o limite de legenda do Telegram (1024 caracteres), truncando se necessário | Evita erro de API por caption longa | y |
| Selo "menor preço em N dias" — janela | 90 dias, configurável (funcionalidade P3, não bloqueia v1) | Horizonte razoável de "melhor preço" sem exigir anos de histórico acumulado | y |
| Diretório de banners temporários | Arquivo removido após o disparo do produto ser concluído em todos os canais elegíveis (sucesso ou falha final) | Evita acúmulo de arquivos em disco | y |
| Falha no download/processamento da imagem | Produto é marcado para envio em modo texto puro (sem banner) — exceção pontual à regra geral do Telegram sempre usar `sendPhoto` | Decisão do usuário: a promoção não pode se perder só porque a imagem falhou | y |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Geração de copy via LLM ⭐ MVP

**User Story**: Como operador, quero que a copy de cada produto candidato seja gerada por um LLM (OpenRouter, modelo free, via LangChain4j) em português, para publicar um texto atrativo sem escrever manualmente.

**Why P1**: É o texto que efetivamente é publicado nos canais — sem ele, não há post.

**Acceptance Criteria**:

1. WHEN um produto é selecionado como candidato ao disparo THEN o sistema SHALL solicitar ao LLM configurado (OpenRouter, modelo free) uma copy em português do Brasil contendo nome do produto, preço "de" e "por", percentual de desconto e uma chamada para ação.
2. THE sistema SHALL gerar a copy uma única vez por produto por evento de queda de preço detectado, e SHALL reutilizá-la para todos os canais elegíveis daquele disparo.
3. THE sistema SHALL inserir o link de afiliado (URL do produto + tag de afiliado configurada) na copy final, substituindo qualquer link que o LLM eventualmente tenha gerado.
4. IF a chamada ao LLM exceder o timeout configurável OU retornar erro (rate limit, indisponibilidade, resposta vazia) THEN o sistema SHALL usar a copy de fallback por template e SHALL registrar log de WARN com o motivo da falha.

**Independent Test**: Enviar um produto candidato ao serviço de enriquecimento com o LLM disponível e confirmar que a copy retornada contém preço de/por, percentual e o link de afiliado correto.

---

### P1: Fallback de copy por template ⭐ MVP

**User Story**: Como operador, quero que o disparo aconteça mesmo se o LLM falhar, para que a monetização nunca dependa da disponibilidade do modelo free.

**Why P1**: Garante O3 do PRD (100% dos disparos com link de afiliado) independentemente de o LLM estar de pé.

**Acceptance Criteria**:

1. IF o LLM falhar (timeout, erro, resposta vazia) THEN o sistema SHALL montar a copy usando um template fixo com título do produto, preço de/por, percentual de desconto e link de afiliado.
2. THE sistema SHALL garantir que 100% dos disparos contenham o link de afiliado com a tag configurada, independentemente de a copy ter vindo do LLM ou do template de fallback.
3. THE sistema SHALL registrar, ao final de cada ciclo de disparo, quantos produtos usaram copy gerada por LLM e quantos usaram o template de fallback.

**Independent Test**: Forçar falha do LLM (ex.: endpoint inválido) e confirmar que o disparo do produto ainda ocorre com copy de template e link de afiliado presente.

---

### P1: Composição de banner local ⭐ MVP

**User Story**: Como operador, quero que a imagem enviada seja um banner com foto do produto, percentual de desconto e preço de/por, para o post ser visualmente atrativo sem depender do LLM para gerar imagem.

**Why P1**: É o principal gancho visual do post — Telegram e WhatsApp priorizam mídia com legenda.

**Acceptance Criteria**:

1. WHEN um produto é selecionado como candidato ao disparo E a imagem principal do produto é baixada com sucesso THEN o sistema SHALL compor localmente um banner sobrepondo à foto do produto o percentual de desconto e o preço "de" (riscado) e "por" (atual).
2. THE sistema SHALL gerar o banner uma única vez por produto por evento de disparo e reutilizá-lo para todos os canais elegíveis.
3. IF o download da imagem do produto falhar (timeout, URL inválida, erro HTTP) OU o arquivo baixado não for uma imagem válida THEN o sistema SHALL marcar esse produto para envio em modo texto puro nesse ciclo, e SHALL registrar log de WARN com o ASIN e o motivo da falha.
4. THE sistema SHALL armazenar o banner gerado em diretório temporário e SHALL removê-lo após o disparo do produto ser concluído (sucesso ou falha final) em todos os canais elegíveis.

**Independent Test**: Processar um produto com imagem válida e confirmar que o arquivo de banner gerado contém o percentual e os dois preços sobrepostos à foto.

---

### P1: Envio em modo texto puro quando não há banner ⭐ MVP

**User Story**: Como operador, quero que uma promoção não se perca só porque a imagem falhou, mesmo que isso exija enviar texto sem imagem uma vez.

**Why P1**: Sem essa exceção pontual, uma falha de rede na CDN da Amazon derrubaria a promoção inteira mesmo com preço e link corretos disponíveis.

**Acceptance Criteria**:

1. IF um produto foi marcado para envio em modo texto puro THEN, para canais Telegram, o sistema SHALL sinalizar que o disparo deve usar o endpoint `sendMessage` no lugar de `sendPhoto` apenas para esse produto; para canais WhatsApp, o sistema SHALL sinalizar envio da copy como mensagem de texto sem mídia anexa.
2. THE sistema SHALL preservar, no texto enviado em modo texto puro, todos os elementos da copy: preço de/por, percentual de desconto e link de afiliado.

**Independent Test**: Forçar falha de download de imagem de um produto e confirmar que ele chega à feature de disparo marcado para texto puro, com a copy completa.

---

### P3: Selo "menor preço em N dias"

**User Story**: Como operador, quero destacar quando o preço atual é o menor dos últimos N dias, para reforçar a urgência da promoção.

**Why P3**: Reforça conversão, mas o disparo funciona plenamente sem esse selo.

**Acceptance Criteria**:

1. WHERE o preço atual do produto for o menor valor registrado no histórico dos últimos N dias configuráveis (padrão 90) THEN o sistema SHALL incluir no banner o selo "MENOR PREÇO EM N DIAS".

---

## Edge Cases

- IF o texto gerado (LLM ou template) exceder o limite de caracteres de legenda do Telegram (1024) THEN o sistema SHALL truncar o texto preservando o link de afiliado íntegro no final.
- IF o LLM retornar conteúdo que remova ou altere o link de afiliado THEN o sistema SHALL reinserir o link correto antes do disparo (coberto por P1 "Geração de copy via LLM", AC3).

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| ENRICH-01 | P1: Geração de copy via LLM | Design | Pending |
| ENRICH-02 | P1: Geração de copy via LLM | Design | Pending |
| ENRICH-03 | P1: Geração de copy via LLM | Design | Pending |
| ENRICH-04 | P1: Geração de copy via LLM | Design | Pending |
| ENRICH-05 | P1: Fallback de copy por template | Design | Pending |
| ENRICH-06 | P1: Fallback de copy por template | Design | Pending |
| ENRICH-07 | P1: Fallback de copy por template | Design | Pending |
| ENRICH-08 | P1: Composição de banner local | Design | Pending |
| ENRICH-09 | P1: Composição de banner local | Design | Pending |
| ENRICH-10 | P1: Composição de banner local | Design | Pending |
| ENRICH-11 | P1: Composição de banner local | Design | Pending |
| ENRICH-12 | P1: Envio em modo texto puro | Design | Pending |
| ENRICH-13 | P1: Envio em modo texto puro | Design | Pending |
| ENRICH-14 | P3: Selo menor preço em N dias | Design | Pending |
| ENRICH-15 | Edge case: truncamento de legenda | Design | Pending |
| ENRICH-16 | Edge case: LLM altera link | Design | Pending |

**ID format:** `ENRICH-NN`

**Status values:** Pending → In Design → In Tasks → Implementing → Verified

**Coverage:** 16 total, 0 mapped to tasks, 16 unmapped ⚠️ (Design/Tasks ainda não iniciados)

---

## Success Criteria

- [ ] 100% dos produtos disparados têm link de afiliado presente, com ou sem LLM disponível
- [ ] Falha de imagem nunca impede o disparo do produto (cai para modo texto puro)
- [ ] Copy e banner são gerados uma única vez por produto por evento, reutilizados entre canais
