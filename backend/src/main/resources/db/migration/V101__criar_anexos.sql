CREATE TABLE anexos (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    chamado_id          BIGINT       NOT NULL,
    autor_id            BIGINT       NOT NULL,
    nome_original       VARCHAR(255) NOT NULL,
    tipo_conteudo       VARCHAR(50)  NOT NULL,
    tamanho             BIGINT       NOT NULL,
    chave_armazenamento VARCHAR(64)  NOT NULL,
    criado_em           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_anexos PRIMARY KEY (id),
    CONSTRAINT uk_anexos_chave UNIQUE (chave_armazenamento),
    CONSTRAINT fk_anexos_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id) ON DELETE RESTRICT,
    CONSTRAINT fk_anexos_autor   FOREIGN KEY (autor_id)   REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT ck_anexos_tipo    CHECK (tipo_conteudo IN ('image/jpeg', 'image/png', 'application/pdf')),
    CONSTRAINT ck_anexos_tamanho CHECK (tamanho > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_anexos_chamado ON anexos (chamado_id, criado_em);
CREATE INDEX idx_anexos_autor   ON anexos (autor_id);
