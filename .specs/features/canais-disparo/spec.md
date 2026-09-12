# Canais & Disparo Specification

## Problem Statement

Ter produtos enriquecidos não adianta se não houver para onde mandá-los, quando mandar, e como não repetir o mesmo produto no mesmo grupo até cansar todo mundo. Esta feature é o "última milha" do bot: lê a configuração de canais do banco, decide quem recebe o quê, dispara via Telegram/WhatsApp e garante que um canal quebrado não derruba os demais.

## Goals

- [ ] Adicionar ou remover um canal exige apenas um `INSERT`/`UPDATE`, zero deploy (O4 do PRD)
- [ ] Zero disparos duplicados do mesmo produto no mesmo canal dentro da janela configurada (O2 do PRD)
- [ ] Falha em um canal nunca impede o disparo nos demais canais

## Out of Scope

| Feature | Reason |
| --- | --- |
| CRUD REST de canais / painel admin | D10 trava configuração via `INSERT` direto no banco |
| Circuit breaker / backoff exponencial | PRD §5 — retry simples (1 tentativa) e log bastam neste volume |
| Filas de mensagem | D5 — monolito em camadas, sem infraestrutura de fila |
| Geração de copy/banner | Feature separada `enriquecimento-conteudo` |
| Detecção de queda de preço | Feature separada `scraping-coleta` — esta feature apenas consome candidatos já sinalizados |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Ordem de processamento de canais | Sequencial, um canal por vez | Evita concorrência de escrita no histórico de disparo e simplifica o isolamento de falha por canal | y |
| Retenção do histórico de disparo | Indefinida no v1 | Necessário para o cálculo da janela de dedup e para auditoria/futuras métricas | y |
| Categorias aceitas vazias ou nulas em um canal | Tratadas como configuração incompleta; o canal é ignorado no ciclo com log WARN | Evita enviar "todas as categorias" silenciosamente por engano de cadastro | y |
| Intervalo entre envios ao mesmo canal | 2 segundos, configurável | Reduz risco de rate limit / flood ban do Telegram e da Evolution API | y |
| Overlap de execução do scheduler de disparo | A nova execução é pulada se a anterior ainda estiver rodando | Evita disparos duplicados por sobreposição de ciclos | y |
| Critério de priorização quando há mais candidatos que o teto por canal | Maior percentual de desconto primeiro | Decisão do usuário: prioriza a promoção mais atrativa para quem recebe | y |
| Envio falho conta para dedup? | Não — só grava histórico de disparo em sucesso confirmado; falha após retry libera o produto para o próximo ciclo | Decisão do usuário: falha de canal não deve "gastar" a promoção; ela deve ser tentada de novo | y |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Canal configurável via banco ⭐ MVP

**User Story**: Como operador, quero cadastrar canais (Telegram/WhatsApp) diretamente no banco, com tipo, identificador, categorias aceitas e status ativo/inativo, para adicionar ou remover um canal sem alterar código ou fazer deploy.

**Why P1**: É a base de configuração de todo o disparo (O4 do PRD).

**Acceptance Criteria**:

1. THE sistema SHALL ler a lista de canais elegíveis para um ciclo de disparo diretamente da tabela de canais no Postgres, sem cache em memória entre ciclos.
2. THE sistema SHALL considerar, para qualquer ciclo de disparo, apenas canais com status ativo=true.
3. WHERE um canal tiver uma lista de categorias aceitas restrita (não "todas") THE sistema SHALL enviar a esse canal apenas produtos cuja categoria esteja na lista aceita do canal.
4. IF um canal cadastrado tiver tipo diferente de TELEGRAM ou WHATSAPP THEN o sistema SHALL ignorá-lo no ciclo de disparo e SHALL registrar log de WARN.

**Independent Test**: Inserir um canal Telegram ativo com categoria "MONITOR" e um candidato dessa categoria; confirmar que o canal é selecionado para recebê-lo.

---

### P1: Agendamento de coleta e disparo ⭐ MVP

**User Story**: Como operador, quero dois schedulers independentes (coleta e disparo), com horários configuráveis, para controlar a cadência de cada etapa sem acoplar uma à outra.

**Why P1**: Sem agendamento automático, o ciclo inteiro exigiria disparo manual, o que contraria o objetivo O1 do PRD.

**Acceptance Criteria**:

1. THE sistema SHALL executar o ciclo de coleta (feature `scraping-coleta`) em um cron configurável, independente do cron de disparo.
2. THE sistema SHALL executar o ciclo de disparo em um cron configurável, independente do cron de coleta.
3. THE sistema SHALL usar o fuso horário `America/Sao_Paulo` para interpretar ambos os crons.
4. IF uma execução do scheduler de disparo ainda estiver em andamento quando o próximo horário agendado chegar THEN o sistema SHALL pular essa nova execução e SHALL registrar log de WARN.

**Independent Test**: Configurar os crons de coleta e disparo com horários distintos e confirmar, pelos logs, que cada ciclo roda de forma independente no horário configurado.

---

### P1: Seleção e priorização de candidatos por canal ⭐ MVP

**User Story**: Como operador, quero que cada canal receba até um teto configurável de produtos por disparo, priorizando as maiores quedas percentuais, para não gerar flood nem perder as melhores promoções.

**Why P1**: Sem teto e priorização, um ciclo com muitos candidatos violaria o limite seguro de mensagens por canal.

**Acceptance Criteria**:

1. WHEN um ciclo de disparo executa THEN, para cada canal ativo, o sistema SHALL filtrar os produtos candidatos (sinalizados pela feature `scraping-coleta`) pelas categorias aceitas do canal.
2. THE sistema SHALL ordenar os candidatos filtrados por percentual de desconto em ordem decrescente.
3. THE sistema SHALL selecionar, no máximo, o número configurável de produtos por canal (padrão 5) a partir do topo dessa ordenação.
4. IF não houver nenhum candidato elegível para um canal em um ciclo THEN o sistema SHALL pular esse canal nesse ciclo sem erro.

**Independent Test**: Cadastrar 7 candidatos elegíveis para um canal com teto 5 e confirmar que os 5 com maior percentual de desconto são selecionados.

---

### P1: Envio via Telegram ⭐ MVP

**User Story**: Como operador, quero que os produtos selecionados sejam enviados ao canal do Telegram configurado, com imagem e legenda, para o grupo/canal receber o conteúdo pronto.

**Why P1**: É um dos dois canais de distribuição do v1 (D2, D3).

**Acceptance Criteria**:

1. WHEN um produto selecionado tiver banner disponível THEN o sistema SHALL enviar ao chat/canal do Telegram configurado, via `RestClient`, usando o endpoint `sendPhoto`, com o banner como mídia e a copy como legenda (`caption`).
2. WHEN um produto selecionado estiver marcado para envio em modo texto puro (feature `enriquecimento-conteudo`) THEN o sistema SHALL usar o endpoint `sendMessage` com a copy como texto.
3. THE sistema SHALL aguardar um intervalo configurável (padrão 2 segundos) entre envios consecutivos ao mesmo canal.

**Independent Test**: Disparar 1 produto com banner para um canal Telegram de teste e confirmar o recebimento da foto com legenda contendo o link de afiliado.

---

### P2: Envio via WhatsApp (Evolution API)

**User Story**: Como operador, quero que os produtos selecionados sejam enviados aos grupos de WhatsApp configurados via Evolution API, para alcançar também esse canal.

**Why P2**: Amplia o alcance, mas o v1 já é útil apenas com Telegram funcionando (P1).

**Acceptance Criteria**:

1. WHEN um produto selecionado tiver banner disponível THEN o sistema SHALL enviar ao grupo de WhatsApp configurado, via `RestClient`, chamando o endpoint de envio de mídia da Evolution API, com o banner como imagem e a copy como legenda.
2. WHEN um produto selecionado estiver em modo texto puro THEN o sistema SHALL enviar apenas o texto da copy via endpoint de envio de texto da Evolution API.
3. THE sistema SHALL aguardar um intervalo configurável (padrão 2 segundos) entre envios consecutivos ao mesmo grupo de WhatsApp.

**Independent Test**: Disparar 1 produto com banner para um grupo de WhatsApp de teste conectado à Evolution API e confirmar o recebimento da mídia com legenda.

---

### P1: Deduplicação por produto × canal × janela ⭐ MVP

**User Story**: Como operador, quero que o mesmo produto não seja reenviado ao mesmo canal dentro de uma janela de dias configurável, para não repetir conteúdo e cansar o grupo.

**Why P1**: É o requisito O2 do PRD — zero disparos duplicados.

**Acceptance Criteria**:

1. THE sistema SHALL registrar, para cada envio bem-sucedido, uma entrada de histórico de disparo identificada por produto (ASIN) × canal × data/hora do envio.
2. WHEN um produto candidato já tiver uma entrada de histórico de disparo bem-sucedido para um canal dentro da janela de dias configurável (padrão 7) THEN o sistema SHALL excluir esse produto da seleção daquele canal nesse ciclo.
3. IF a queda de preço de um produto for detectada novamente após a janela de dedup ter expirado THEN o sistema SHALL voltar a considerá-lo elegível para os canais que ainda não o receberam nessa nova janela.

**Independent Test**: Disparar um produto para um canal, repetir o ciclo de disparo no dia seguinte dentro da janela de 7 dias e confirmar que o mesmo produto não é reenviado a esse canal.

---

### P1: Isolamento de falha e retry por canal ⭐ MVP

**User Story**: Como operador, quero que uma falha em um canal não impeça os demais, e que uma falha pontual de envio não impeça nova tentativa no próximo ciclo, para o sistema ser resiliente sem complexidade de fila.

**Why P1**: Sem isolamento, um canal com token expirado ou instância da Evolution API fora do ar derrubaria o disparo inteiro.

**Acceptance Criteria**:

1. IF o envio a um canal falhar (erro HTTP, timeout) THEN o sistema SHALL tentar novamente uma única vez, imediatamente, antes de desistir desse produto nesse canal nesse ciclo.
2. IF a tentativa de retry também falhar THEN o sistema SHALL registrar log de ERROR com canal, produto e motivo, e SHALL NÃO gravar entrada de histórico de disparo para esse par produto×canal — o produto permanece elegível no próximo ciclo.
3. IF o envio a um canal falhar (mesmo após o retry) THEN o sistema SHALL continuar o processamento dos demais canais e produtos do ciclo, sem interromper o disparo inteiro.

**Independent Test**: Configurar um canal com credencial inválida e outro válido no mesmo ciclo; confirmar que o canal válido recebe o disparo normalmente e o inválido apenas gera log de ERROR.

---

## Edge Cases

- IF a tabela de canais não tiver nenhum canal ativo THEN o sistema SHALL concluir o ciclo de disparo sem erro, registrando log de INFO "nenhum canal ativo".
- IF um canal tiver o campo de categorias aceitas vazio ou nulo THEN o sistema SHALL ignorá-lo no ciclo de disparo e SHALL registrar log de WARN identificando o canal.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| DISPATCH-01 | P1: Canal configurável via banco | Execute | ✅ Verified |
| DISPATCH-02 | P1: Canal configurável via banco | Execute | ✅ Verified |
| DISPATCH-03 | P1: Canal configurável via banco | Execute | ✅ Verified |
| DISPATCH-04 | P1: Canal configurável via banco | Execute | ✅ Verified |
| DISPATCH-05 | P1: Agendamento de coleta e disparo | Execute | ✅ Verified (evidência estrutural — sem teste de disparo em tempo real do cron, ver `validation.md`) |
| DISPATCH-06 | P1: Agendamento de coleta e disparo | Execute | ✅ Verified (evidência estrutural — mesmo caso de DISPATCH-05) |
| DISPATCH-07 | P1: Agendamento de coleta e disparo | Execute | ✅ Verified (evidência estrutural — mesmo caso de DISPATCH-05) |
| DISPATCH-08 | P1: Agendamento de coleta e disparo | Execute | ✅ Verified |
| DISPATCH-09 | P1: Seleção e priorização de candidatos | Execute | ✅ Verified |
| DISPATCH-10 | P1: Seleção e priorização de candidatos | Execute | ✅ Verified |
| DISPATCH-11 | P1: Seleção e priorização de candidatos | Execute | ✅ Verified |
| DISPATCH-12 | P1: Seleção e priorização de candidatos | Execute | ✅ Verified |
| DISPATCH-13 | P1: Envio via Telegram | Execute | ✅ Verified |
| DISPATCH-14 | P1: Envio via Telegram | Execute | ✅ Verified |
| DISPATCH-15 | P1: Envio via Telegram | Execute | ✅ Verified (config lida com o default correto a cada envio; invocação real do `Thread.sleep` não observada diretamente — ver L-004 em `LESSONS.md` e `validation.md`) |
| DISPATCH-16 | P2: Envio via WhatsApp | Execute | ✅ Verified |
| DISPATCH-17 | P2: Envio via WhatsApp | Execute | ✅ Verified |
| DISPATCH-18 | P2: Envio via WhatsApp | Execute | ✅ Verified (mesmo caso de DISPATCH-15 — lógica de intervalo compartilhada) |
| DISPATCH-19 | P1: Deduplicação por produto×canal×janela | Execute | ✅ Verified |
| DISPATCH-20 | P1: Deduplicação por produto×canal×janela | Execute | ✅ Verified |
| DISPATCH-21 | P1: Deduplicação por produto×canal×janela | Execute | ✅ Verified |
| DISPATCH-22 | P1: Isolamento de falha e retry | Execute | ✅ Verified |
| DISPATCH-23 | P1: Isolamento de falha e retry | Execute | ✅ Verified |
| DISPATCH-24 | P1: Isolamento de falha e retry | Execute | ✅ Verified |
| DISPATCH-25 | Edge case: nenhum canal ativo | Execute | ✅ Verified |
| DISPATCH-26 | Edge case: categorias aceitas vazias | Execute | ✅ Verified |

**ID format:** `DISPATCH-NN`

**Status values:** Pending → In Design → In Tasks → Implementing → Verified

**Coverage:** 26 total, 26 Verified — ver `.specs/features/canais-disparo/validation.md` (Verifier PASS, gate 93/93, sensor 7/7 mortos)

---

## Success Criteria

- [ ] Telegram e WhatsApp recebem, cada um respeitando as categorias aceitas do seu canal
- [ ] O mesmo produto não é reenviado ao mesmo canal dentro da janela configurada
- [ ] Adicionar um canal novo exige apenas um `INSERT`
- [ ] Um canal com falha (credencial inválida, API fora do ar) nunca impede o disparo nos demais
