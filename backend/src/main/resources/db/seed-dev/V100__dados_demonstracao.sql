SET @agora = UTC_TIMESTAMP(6);

INSERT INTO usuarios (id, nome, email, senha_hash, perfil, departamento, telefone, ativo) VALUES
    (1, 'Administrador do Sistema', 'admin@chamati.local',
        '$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW', 'ADMIN',       'TI',         '(47) 3000-0001', TRUE),
    (2, 'Carlos Mendes',            'carlos.mendes@chamati.local',
        '$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW', 'TECNICO',     'TI',         '(47) 3000-0002', TRUE),
    (3, 'Fernanda Lima',            'fernanda.lima@chamati.local',
        '$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW', 'TECNICO',     'TI',         '(47) 3000-0003', TRUE),
    (4, 'Ana Souza',                'ana.souza@chamati.local',
        '$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW', 'SOLICITANTE', 'Financeiro', '(47) 3000-0004', TRUE),
    (5, 'Bruno Costa',              'bruno.costa@chamati.local',
        '$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW', 'SOLICITANTE', 'Comercial',  '(47) 3000-0005', TRUE),
    (6, 'Juliana Rocha',            'juliana.rocha@chamati.local',
        '$2a$10$rN2e7Q4A.kaSk3JBtjizpOTLMmRAbKB9V7oz2iUbpI/sxv7olZSEW', 'SOLICITANTE', 'RH',         '(47) 3000-0006', TRUE);

INSERT INTO chamados
    (id, protocolo, titulo, descricao, categoria_id, subcategoria_id, prioridade, status,
     solicitante_id, tecnico_id, solucao,
     criado_em, atribuido_em, primeira_resposta_em, resolvido_em, fechado_em, reaberto_em, total_reaberturas)
VALUES
    (1, 'CT-2026-000001', 'Notebook não liga após queda de energia',
        'Após a queda de energia de ontem, o notebook não liga nem com o carregador conectado.',
        1, 1, 'ALTA', 'FECHADO', 4, 2,
        'Fonte de alimentação substituída. Equipamento testado por 24 horas sem falhas.',
        DATE_SUB(@agora, INTERVAL 14400 MINUTE), DATE_SUB(@agora, INTERVAL 14390 MINUTE),
        DATE_SUB(@agora, INTERVAL 14380 MINUTE), DATE_SUB(@agora, INTERVAL 14000 MINUTE),
        DATE_SUB(@agora, INTERVAL 12960 MINUTE), NULL, 0),

    (2, 'CT-2026-000002', 'Instalação do pacote Office',
        'Preciso do Word e do Excel instalados na máquina nova do comercial.',
        2, 4, 'BAIXA', 'RESOLVIDO', 5, 3,
        'Pacote Office instalado e licença corporativa ativada.',
        DATE_SUB(@agora, INTERVAL 4320 MINUTE), DATE_SUB(@agora, INTERVAL 4300 MINUTE),
        DATE_SUB(@agora, INTERVAL 4200 MINUTE), DATE_SUB(@agora, INTERVAL 2880 MINUTE),
        NULL, NULL, 0),

    (3, 'CT-2026-000003', 'Sem acesso à VPN',
        'A VPN retorna erro de autenticação desde o início da manhã e não consigo acessar os sistemas.',
        3, 8, 'CRITICA', 'EM_ATENDIMENTO', 6, 2, NULL,
        DATE_SUB(@agora, INTERVAL 300 MINUTE), DATE_SUB(@agora, INTERVAL 290 MINUTE),
        DATE_SUB(@agora, INTERVAL 285 MINUTE), NULL, NULL, NULL, 0),

    (4, 'CT-2026-000004', 'ERP trava ao emitir nota fiscal',
        'O ERP congela por alguns minutos ao emitir nota fiscal de serviço e depois fecha sozinho.',
        4, 10, 'ALTA', 'AGUARDANDO_USUARIO', 4, 3, NULL,
        DATE_SUB(@agora, INTERVAL 1440 MINUTE), DATE_SUB(@agora, INTERVAL 1420 MINUTE),
        DATE_SUB(@agora, INTERVAL 1380 MINUTE), NULL, NULL, NULL, 0),

    (5, 'CT-2026-000005', 'Solicitação de acesso à pasta do financeiro',
        'Preciso de acesso de leitura à pasta compartilhada do financeiro para a auditoria.',
        5, 14, 'MEDIA', 'ABERTO', 5, NULL, NULL,
        DATE_SUB(@agora, INTERVAL 90 MINUTE), NULL, NULL, NULL, NULL, NULL, 0),

    (6, 'CT-2026-000006', 'Impressora do 2º andar com papel enroscado',
        'A impressora do corredor do RH puxa várias folhas juntas e trava.',
        6, 15, 'BAIXA', 'EM_ATENDIMENTO', 6, 2, NULL,
        DATE_SUB(@agora, INTERVAL 600 MINUTE), DATE_SUB(@agora, INTERVAL 580 MINUTE),
        DATE_SUB(@agora, INTERVAL 560 MINUTE), NULL, NULL, NULL, 0),

    (7, 'CT-2026-000007', 'Caixa de e-mail cheia',
        'Não consigo receber novos e-mails, aparece aviso de caixa cheia.',
        7, 17, 'MEDIA', 'RESOLVIDO', 4, 3,
        'Cota ampliada de 5 GB para 10 GB e itens antigos arquivados.',
        DATE_SUB(@agora, INTERVAL 2000 MINUTE), DATE_SUB(@agora, INTERVAL 1990 MINUTE),
        DATE_SUB(@agora, INTERVAL 1950 MINUTE), DATE_SUB(@agora, INTERVAL 1800 MINUTE),
        NULL, NULL, 0),

    (8, 'CT-2026-000008', 'Monitor com linhas na tela',
        'O monitor da minha mesa exibe linhas verticais coloridas desde ontem.',
        1, 3, 'MEDIA', 'AGUARDANDO_TERCEIRO', 5, 2, NULL,
        DATE_SUB(@agora, INTERVAL 4000 MINUTE), DATE_SUB(@agora, INTERVAL 3990 MINUTE),
        DATE_SUB(@agora, INTERVAL 3880 MINUTE), NULL, NULL, NULL, 0),

    (9, 'CT-2026-000009', 'Wi-Fi instável na sala de reuniões',
        'A conexão Wi-Fi cai várias vezes durante as videoconferências na sala 3.',
        3, 7, 'ALTA', 'ABERTO', 6, NULL, NULL,
        DATE_SUB(@agora, INTERVAL 30 MINUTE), NULL, NULL, NULL, NULL, NULL, 0),

    (10, 'CT-2026-000010', 'Sistema interno fora do ar para todos',
        'O sistema de ponto eletrônico não carrega para nenhum colaborador.',
        4, 11, 'CRITICA', 'FECHADO', 4, 3,
        'Serviço reiniciado e disco do servidor liberado. Monitoramento de espaço configurado.',
        DATE_SUB(@agora, INTERVAL 9000 MINUTE), DATE_SUB(@agora, INTERVAL 8995 MINUTE),
        DATE_SUB(@agora, INTERVAL 8990 MINUTE), DATE_SUB(@agora, INTERVAL 8800 MINUTE),
        DATE_SUB(@agora, INTERVAL 7000 MINUTE), NULL, 0),

    (11, 'CT-2026-000011', 'Atalho da área de trabalho sumiu',
        'O atalho do sistema de RH desapareceu da área de trabalho.',
        8, NULL, 'BAIXA', 'EM_ATENDIMENTO', 6, 3, NULL,
        DATE_SUB(@agora, INTERVAL 3000 MINUTE), DATE_SUB(@agora, INTERVAL 2990 MINUTE),
        DATE_SUB(@agora, INTERVAL 2900 MINUTE), NULL, NULL, DATE_SUB(@agora, INTERVAL 500 MINUTE), 1),

    (12, 'CT-2026-000012', 'Instalar antivírus corporativo em notebook novo',
        'Notebook novo do comercial precisa do antivírus corporativo antes de ser usado.',
        2, 4, 'MEDIA', 'RESOLVIDO', 4, 2,
        'Antivírus corporativo instalado, atualizado e registrado no console de gestão.',
        DATE_SUB(@agora, INTERVAL 6000 MINUTE), DATE_SUB(@agora, INTERVAL 5990 MINUTE),
        DATE_SUB(@agora, INTERVAL 5900 MINUTE), DATE_SUB(@agora, INTERVAL 5700 MINUTE),
        NULL, NULL, 0);

INSERT INTO sequencias_protocolo (ano, ultimo_numero) VALUES (2026, 12);

UPDATE chamados c
JOIN politicas_sla p ON p.prioridade = c.prioridade
SET c.sla_primeira_resposta_limite = DATE_ADD(c.criado_em, INTERVAL p.prazo_primeira_resposta_min MINUTE),
    c.sla_resolucao_limite         = DATE_ADD(c.criado_em, INTERVAL p.prazo_resolucao_min MINUTE);

INSERT INTO comentarios (chamado_id, autor_id, tipo, conteudo, criado_em) VALUES
    (1, 2, 'PUBLICO',      'Recebi o chamado. Vou testar a fonte e a bateria do equipamento.',                 DATE_SUB(@agora, INTERVAL 14380 MINUTE)),
    (1, 2, 'NOTA_INTERNA', 'Fonte queimada pela oscilação. Pedir nova ao estoque (patrimônio 4471).',          DATE_SUB(@agora, INTERVAL 14200 MINUTE)),
    (1, 4, 'PUBLICO',      'Obrigada, o notebook voltou a funcionar normalmente.',                             DATE_SUB(@agora, INTERVAL 13900 MINUTE)),
    (3, 2, 'PUBLICO',      'Estamos verificando o servidor de autenticação da VPN. Retorno em breve.',         DATE_SUB(@agora, INTERVAL 285 MINUTE)),
    (3, 2, 'NOTA_INTERNA', 'Certificado do gateway expirou. Aguardando janela para renovação.',                DATE_SUB(@agora, INTERVAL 250 MINUTE)),
    (3, 6, 'PUBLICO',      'Continuo sem acesso. Existe alguma previsão?',                                     DATE_SUB(@agora, INTERVAL 200 MINUTE)),
    (4, 3, 'PUBLICO',      'Pode informar o horário exato do último travamento e a versão do ERP?',            DATE_SUB(@agora, INTERVAL 1380 MINUTE)),
    (8, 2, 'NOTA_INTERNA', 'Equipamento em garantia. Fornecedor acionado, aguardando coleta.',                 DATE_SUB(@agora, INTERVAL 3900 MINUTE)),
    (8, 2, 'PUBLICO',      'O monitor está em garantia e foi enviado ao fornecedor para troca.',               DATE_SUB(@agora, INTERVAL 3880 MINUTE)),
    (11, 6, 'PUBLICO',     'O atalho sumiu novamente depois da atualização do Windows.',                       DATE_SUB(@agora, INTERVAL 500 MINUTE));

INSERT INTO comentarios (chamado_id, autor_id, tipo, conteudo, criado_em) VALUES
    (2,  3, 'PUBLICO', 'Recebi o chamado. Vou instalar o pacote ainda hoje.',                    DATE_SUB(@agora, INTERVAL 4200 MINUTE)),
    (6,  2, 'PUBLICO', 'Vou verificar o rolo de tração da impressora.',                          DATE_SUB(@agora, INTERVAL 560 MINUTE)),
    (7,  3, 'PUBLICO', 'Vou verificar a cota da sua caixa de e-mail.',                           DATE_SUB(@agora, INTERVAL 1950 MINUTE)),
    (10, 3, 'PUBLICO', 'Identificamos a falha no servidor e já estamos atuando.',                DATE_SUB(@agora, INTERVAL 8990 MINUTE)),
    (11, 3, 'PUBLICO', 'Vou verificar a política que remove atalhos da área de trabalho.',       DATE_SUB(@agora, INTERVAL 2900 MINUTE)),
    (12, 2, 'PUBLICO', 'Vou preparar o instalador e agendar a instalação com você.',             DATE_SUB(@agora, INTERVAL 5900 MINUTE));

INSERT INTO historico_chamados (chamado_id, autor_id, tipo_evento, descricao, criado_em)
SELECT id, solicitante_id, 'CRIACAO', 'Chamado criado', criado_em FROM chamados;

INSERT INTO historico_chamados (chamado_id, autor_id, tipo_evento, campo, valor_anterior, valor_novo, descricao, criado_em)
SELECT c.id, 1, 'ATRIBUICAO', 'tecnico', NULL, u.nome, 'Chamado atribuído ao técnico', c.atribuido_em
FROM chamados c JOIN usuarios u ON u.id = c.tecnico_id;

INSERT INTO historico_chamados (chamado_id, autor_id, tipo_evento, descricao, criado_em)
SELECT chamado_id, autor_id,
       CASE tipo WHEN 'PUBLICO' THEN 'COMENTARIO' ELSE 'NOTA_INTERNA' END,
       CASE tipo WHEN 'PUBLICO' THEN 'Comentário adicionado' ELSE 'Nota interna adicionada' END,
       criado_em
FROM comentarios;

INSERT INTO historico_chamados (chamado_id, autor_id, tipo_evento, campo, valor_anterior, valor_novo, descricao, criado_em) VALUES
    (1, 2, 'ALTERACAO_STATUS',     'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 14380 MINUTE)),
    (1, 2, 'RESOLUCAO',            'status', 'EM_ATENDIMENTO', 'RESOLVIDO',           'Solução registrada', DATE_SUB(@agora, INTERVAL 14000 MINUTE)),
    (1, 4, 'ENCERRAMENTO',         'status', 'RESOLVIDO',      'FECHADO',             'Chamado encerrado', DATE_SUB(@agora, INTERVAL 12960 MINUTE)),

    (2, 3, 'ALTERACAO_STATUS',     'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 4200 MINUTE)),
    (2, 3, 'RESOLUCAO',            'status', 'EM_ATENDIMENTO', 'RESOLVIDO',           'Solução registrada', DATE_SUB(@agora, INTERVAL 2880 MINUTE)),

    (3, 1, 'ALTERACAO_PRIORIDADE', 'prioridade', 'MEDIA',      'CRITICA',             'Impacto em todos os usuários remotos', DATE_SUB(@agora, INTERVAL 295 MINUTE)),
    (3, 2, 'ALTERACAO_STATUS',     'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 285 MINUTE)),

    (4, 3, 'ALTERACAO_STATUS',     'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 1400 MINUTE)),
    (4, 3, 'ALTERACAO_STATUS',     'status', 'EM_ATENDIMENTO', 'AGUARDANDO_USUARIO',  NULL, DATE_SUB(@agora, INTERVAL 1380 MINUTE)),

    (6, 2, 'ALTERACAO_STATUS',     'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 560 MINUTE)),

    (7, 3, 'ALTERACAO_STATUS',     'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 1950 MINUTE)),
    (7, 3, 'RESOLUCAO',            'status', 'EM_ATENDIMENTO', 'RESOLVIDO',           'Solução registrada', DATE_SUB(@agora, INTERVAL 1800 MINUTE)),

    (8, 2, 'ALTERACAO_STATUS',     'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 3950 MINUTE)),
    (8, 2, 'ALTERACAO_STATUS',     'status', 'EM_ATENDIMENTO', 'AGUARDANDO_TERCEIRO', 'Aguardando troca pelo fornecedor', DATE_SUB(@agora, INTERVAL 3880 MINUTE)),

    (10, 3, 'ALTERACAO_STATUS',    'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 8990 MINUTE)),
    (10, 3, 'RESOLUCAO',           'status', 'EM_ATENDIMENTO', 'RESOLVIDO',           'Solução registrada', DATE_SUB(@agora, INTERVAL 8800 MINUTE)),
    (10, 4, 'ENCERRAMENTO',        'status', 'RESOLVIDO',      'FECHADO',             'Chamado encerrado', DATE_SUB(@agora, INTERVAL 7000 MINUTE)),

    (11, 3, 'ALTERACAO_STATUS',    'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 2900 MINUTE)),
    (11, 3, 'RESOLUCAO',           'status', 'EM_ATENDIMENTO', 'RESOLVIDO',           'Solução registrada', DATE_SUB(@agora, INTERVAL 1500 MINUTE)),
    (11, 6, 'REABERTURA',          'status', 'RESOLVIDO',      'EM_ATENDIMENTO',      CONCAT('Problema voltou a ocorrer. resolvido_em anterior: ', DATE_FORMAT(DATE_SUB(@agora, INTERVAL 1500 MINUTE), '%Y-%m-%d %H:%i:%s'), '; fechado_em anterior: nenhum'), DATE_SUB(@agora, INTERVAL 500 MINUTE)),

    (12, 2, 'ALTERACAO_STATUS',    'status', 'ABERTO',         'EM_ATENDIMENTO',      NULL, DATE_SUB(@agora, INTERVAL 5900 MINUTE)),
    (12, 2, 'RESOLUCAO',           'status', 'EM_ATENDIMENTO', 'RESOLVIDO',           'Solução registrada', DATE_SUB(@agora, INTERVAL 5700 MINUTE));
