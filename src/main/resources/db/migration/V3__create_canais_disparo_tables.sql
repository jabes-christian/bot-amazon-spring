CREATE TABLE channel (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    identificador VARCHAR(255) NOT NULL,
    categorias_aceitas VARCHAR(500),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE dispatch_history (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product (id),
    channel_id BIGINT NOT NULL REFERENCES channel (id),
    enviado_em TIMESTAMP NOT NULL
);

CREATE INDEX idx_dispatch_history_product_channel_enviado ON dispatch_history (product_id, channel_id, enviado_em);

INSERT INTO app_config (chave, valor, descricao) VALUES
    ('disparo.teto-produtos-por-canal', '5', 'Numero maximo de produtos selecionados por canal em cada ciclo de disparo'),
    ('disparo.janela-dedup-dias', '7', 'Janela em dias dentro da qual o mesmo produto nao e reenviado ao mesmo canal'),
    ('disparo.intervalo-entre-envios-segundos', '2', 'Intervalo de espera apos cada tentativa de envio, sucesso ou falha');
