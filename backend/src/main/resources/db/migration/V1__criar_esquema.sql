CREATE TABLE usuarios (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    nome            VARCHAR(120)  NOT NULL,
    email           VARCHAR(160)  NOT NULL,
    senha_hash      VARCHAR(100)  NOT NULL,
    perfil          VARCHAR(20)   NOT NULL,
    departamento    VARCHAR(80)   NULL,
    telefone        VARCHAR(30)   NULL,
    ativo           BOOLEAN       NOT NULL DEFAULT TRUE,
    ultimo_login_em DATETIME(6)   NULL,
    criado_em       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_usuarios PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email),
    CONSTRAINT ck_usuarios_perfil CHECK (perfil IN ('ADMIN', 'TECNICO', 'SOLICITANTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_usuarios_perfil_ativo ON usuarios (perfil, ativo);

CREATE TABLE categorias (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nome          VARCHAR(80)  NOT NULL,
    descricao     VARCHAR(255) NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_categorias PRIMARY KEY (id),
    CONSTRAINT uk_categorias_nome UNIQUE (nome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE subcategorias (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    categoria_id  BIGINT       NOT NULL,
    nome          VARCHAR(80)  NOT NULL,
    descricao     VARCHAR(255) NULL,
    ativo         BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_subcategorias PRIMARY KEY (id),
    CONSTRAINT uk_subcategorias_categoria_nome UNIQUE (categoria_id, nome),
    CONSTRAINT uk_subcategorias_id_categoria UNIQUE (id, categoria_id),
    CONSTRAINT fk_subcategorias_categoria FOREIGN KEY (categoria_id)
        REFERENCES categorias (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE politicas_sla (
    id                           BIGINT      NOT NULL AUTO_INCREMENT,
    prioridade                   VARCHAR(10) NOT NULL,
    prazo_primeira_resposta_min  INT         NOT NULL,
    prazo_resolucao_min          INT         NOT NULL,
    ativo                        BOOLEAN     NOT NULL DEFAULT TRUE,
    criado_em                    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em                DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_politicas_sla PRIMARY KEY (id),
    CONSTRAINT uk_politicas_sla_prioridade UNIQUE (prioridade),
    CONSTRAINT ck_politicas_sla_prioridade CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_politicas_sla_prazos CHECK (
        prazo_primeira_resposta_min > 0
        AND prazo_resolucao_min >= prazo_primeira_resposta_min
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sequencias_protocolo (
    ano           SMALLINT NOT NULL,
    ultimo_numero INT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_sequencias_protocolo PRIMARY KEY (ano)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chamados (
    id                          BIGINT       NOT NULL AUTO_INCREMENT,
    protocolo                   VARCHAR(20)  NOT NULL,
    titulo                      VARCHAR(150) NOT NULL,
    descricao                   TEXT         NOT NULL,
    categoria_id                BIGINT       NOT NULL,
    subcategoria_id             BIGINT       NULL,
    prioridade                  VARCHAR(10)  NOT NULL DEFAULT 'MEDIA',
    status                      VARCHAR(25)  NOT NULL DEFAULT 'ABERTO',
    solicitante_id              BIGINT       NOT NULL,
    tecnico_id                  BIGINT       NULL,
    solucao                     TEXT         NULL,

    criado_em                   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    atribuido_em                DATETIME(6)  NULL,
    primeira_resposta_em        DATETIME(6)  NULL,
    resolvido_em                DATETIME(6)  NULL,
    fechado_em                  DATETIME(6)  NULL,
    reaberto_em                 DATETIME(6)  NULL,
    total_reaberturas           INT          NOT NULL DEFAULT 0,

    sla_primeira_resposta_limite DATETIME(6) NULL,
    sla_resolucao_limite         DATETIME(6) NULL,

    versao                      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_chamados PRIMARY KEY (id),
    CONSTRAINT uk_chamados_protocolo UNIQUE (protocolo),
    CONSTRAINT fk_chamados_categoria    FOREIGN KEY (categoria_id)    REFERENCES categorias (id)    ON DELETE RESTRICT,
    CONSTRAINT fk_chamados_subcategoria FOREIGN KEY (subcategoria_id, categoria_id) REFERENCES subcategorias (id, categoria_id) ON DELETE RESTRICT,
    CONSTRAINT fk_chamados_solicitante  FOREIGN KEY (solicitante_id)  REFERENCES usuarios (id)      ON DELETE RESTRICT,
    CONSTRAINT fk_chamados_tecnico      FOREIGN KEY (tecnico_id)      REFERENCES usuarios (id)      ON DELETE RESTRICT,
    CONSTRAINT ck_chamados_prioridade CHECK (prioridade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_chamados_status CHECK (status IN (
        'ABERTO', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO',
        'AGUARDANDO_TERCEIRO', 'RESOLVIDO', 'FECHADO')),
    CONSTRAINT ck_chamados_resolvido CHECK (status NOT IN ('RESOLVIDO', 'FECHADO') OR resolvido_em IS NOT NULL),
    CONSTRAINT ck_chamados_fechado   CHECK (status <> 'FECHADO' OR fechado_em IS NOT NULL),
    CONSTRAINT ck_chamados_atribuicao CHECK (tecnico_id IS NULL OR atribuido_em IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_chamados_status_prioridade ON chamados (status, prioridade);
CREATE INDEX idx_chamados_solicitante       ON chamados (solicitante_id, criado_em);
CREATE INDEX idx_chamados_tecnico_status    ON chamados (tecnico_id, status);
CREATE INDEX idx_chamados_categoria         ON chamados (categoria_id);
CREATE INDEX idx_chamados_criado_em         ON chamados (criado_em);
CREATE INDEX idx_chamados_resolvido_em      ON chamados (resolvido_em);
CREATE INDEX idx_chamados_sla_resolucao     ON chamados (sla_resolucao_limite);

CREATE TABLE comentarios (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    chamado_id BIGINT      NOT NULL,
    autor_id   BIGINT      NOT NULL,
    tipo       VARCHAR(15) NOT NULL DEFAULT 'PUBLICO',
    conteudo   TEXT        NOT NULL,
    criado_em  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_comentarios PRIMARY KEY (id),
    CONSTRAINT fk_comentarios_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id) ON DELETE RESTRICT,
    CONSTRAINT fk_comentarios_autor   FOREIGN KEY (autor_id)   REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT ck_comentarios_tipo CHECK (tipo IN ('PUBLICO', 'NOTA_INTERNA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_comentarios_chamado_tipo ON comentarios (chamado_id, tipo, criado_em);
CREATE INDEX idx_comentarios_autor        ON comentarios (autor_id);

CREATE TABLE historico_chamados (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    chamado_id     BIGINT       NOT NULL,
    autor_id       BIGINT       NOT NULL,
    tipo_evento    VARCHAR(25)  NOT NULL,
    campo          VARCHAR(50)  NULL,
    valor_anterior VARCHAR(255) NULL,
    valor_novo     VARCHAR(255) NULL,
    descricao      VARCHAR(500) NULL,
    criado_em      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_historico_chamados PRIMARY KEY (id),
    CONSTRAINT fk_historico_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id) ON DELETE RESTRICT,
    CONSTRAINT fk_historico_autor   FOREIGN KEY (autor_id)   REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT ck_historico_tipo_evento CHECK (tipo_evento IN (
        'CRIACAO', 'EDICAO', 'ALTERACAO_STATUS', 'ALTERACAO_PRIORIDADE',
        'ATRIBUICAO', 'COMENTARIO', 'NOTA_INTERNA', 'RESOLUCAO',
        'ENCERRAMENTO', 'REABERTURA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_historico_chamado_data ON historico_chamados (chamado_id, criado_em);
CREATE INDEX idx_historico_autor        ON historico_chamados (autor_id);

CREATE TABLE refresh_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    usuario_id  BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    expira_em   DATETIME(6) NOT NULL,
    revogado_em DATETIME(6) NULL,
    criado_em   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens (usuario_id, expira_em);
