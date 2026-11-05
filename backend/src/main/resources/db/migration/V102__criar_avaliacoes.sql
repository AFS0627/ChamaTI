CREATE TABLE avaliacoes (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    chamado_id BIGINT        NOT NULL,
    tecnico_id BIGINT        NOT NULL,
    nota       INT           NOT NULL,
    comentario VARCHAR(1000) NULL,
    criado_em  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_avaliacoes PRIMARY KEY (id),
    CONSTRAINT uk_avaliacoes_chamado UNIQUE (chamado_id),
    CONSTRAINT fk_avaliacoes_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id) ON DELETE RESTRICT,
    CONSTRAINT fk_avaliacoes_tecnico FOREIGN KEY (tecnico_id) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT ck_avaliacoes_nota CHECK (nota BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_avaliacoes_tecnico ON avaliacoes (tecnico_id);
