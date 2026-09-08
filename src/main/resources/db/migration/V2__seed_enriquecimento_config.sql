INSERT INTO app_config (chave, valor, descricao) VALUES
    ('enriquecimento.llm-timeout-segundos', '15', 'Timeout em segundos para aguardar a resposta do LLM antes de cair para copy por template'),
    ('enriquecimento.limite-caracteres-copy', '1024', 'Limite maximo de caracteres da copy gerada, preservando o link de afiliado ao truncar'),
    ('enriquecimento.selo-menor-preco-dias', '90', 'Janela em dias usada para decidir se o preco atual e o menor preco e exibir o selo no banner');
