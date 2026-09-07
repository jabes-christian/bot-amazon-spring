CREATE TABLE categoria_coleta (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    keyword_busca VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE product (
    id BIGSERIAL PRIMARY KEY,
    asin VARCHAR(20) NOT NULL UNIQUE,
    categoria_id BIGINT NOT NULL REFERENCES categoria_coleta (id),
    titulo VARCHAR(500) NOT NULL,
    preco_atual NUMERIC(10, 2) NOT NULL,
    preco_riscado NUMERIC(10, 2),
    url_imagem VARCHAR(1000),
    url_produto VARCHAR(1000),
    last_candidato_preco NUMERIC(10, 2),
    criado_em TIMESTAMP NOT NULL,
    atualizado_em TIMESTAMP NOT NULL
);

CREATE TABLE price_history (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product (id),
    preco NUMERIC(10, 2) NOT NULL,
    capturado_em TIMESTAMP NOT NULL
);

CREATE INDEX idx_price_history_product_capturado ON price_history (product_id, capturado_em);

CREATE TABLE app_config (
    id BIGSERIAL PRIMARY KEY,
    chave VARCHAR(100) NOT NULL UNIQUE,
    valor VARCHAR(255) NOT NULL,
    descricao VARCHAR(500),
    atualizado_em TIMESTAMP
);

INSERT INTO categoria_coleta (codigo, keyword_busca, ativo) VALUES
    ('MONITOR', 'monitor gamer', TRUE),
    ('NOTEBOOK', 'notebook', TRUE),
    ('PERIFERICO', 'periferico gamer', TRUE),
    ('CADEIRA_GAMER', 'cadeira gamer', TRUE),
    ('MESA', 'mesa para escritorio', TRUE);

INSERT INTO app_config (chave, valor, descricao) VALUES
    ('coleta.percentual-minimo-queda', '10', 'Percentual minimo de queda de preco para considerar promocao relevante'),
    ('coleta.preco-minimo-valido', '0.01', 'Preco minimo aceito como valido (sanidade)'),
    ('coleta.preco-maximo-valido', '50000.00', 'Preco maximo aceito como valido (sanidade)'),
    ('coleta.intervalo-entre-categorias-segundos', '5', 'Intervalo de espera entre a coleta de categorias distintas');
