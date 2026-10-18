INSERT INTO categorias (id, nome, descricao, ativo) VALUES
    (1, 'Hardware',                'Computadores, notebooks, monitores e demais equipamentos físicos', TRUE),
    (2, 'Software',                'Instalação, atualização e problemas em programas', TRUE),
    (3, 'Rede e conectividade',    'Internet, Wi-Fi, VPN e acesso à rede corporativa', TRUE),
    (4, 'Sistemas e aplicações',   'Sistemas internos, ERP e aplicações corporativas', TRUE),
    (5, 'Acesso e permissões',     'Criação de contas, redefinição de senhas e permissões', TRUE),
    (6, 'Impressoras e periféricos','Impressoras, scanners, mouses, teclados e headsets', TRUE),
    (7, 'E-mail',                  'Caixas de e-mail, listas de distribuição e agenda', TRUE),
    (8, 'Outros',                  'Demandas que não se enquadram nas demais categorias', TRUE);

INSERT INTO subcategorias (categoria_id, nome) VALUES
    (1, 'Notebook'), (1, 'Desktop'), (1, 'Monitor'),
    (2, 'Instalação'), (2, 'Licenciamento'), (2, 'Erro/travamento'),
    (3, 'Wi-Fi'), (3, 'VPN'), (3, 'Internet'),
    (4, 'ERP'), (4, 'Sistema interno'),
    (5, 'Nova conta'), (5, 'Redefinição de senha'), (5, 'Permissão de pasta'),
    (6, 'Impressora'), (6, 'Scanner'),
    (7, 'Caixa de e-mail'), (7, 'Lista de distribuição');

INSERT INTO politicas_sla (prioridade, prazo_primeira_resposta_min, prazo_resolucao_min) VALUES
    ('CRITICA',   15,  240),
    ('ALTA',      60,  480),
    ('MEDIA',    240, 1440),
    ('BAIXA',    480, 4320);
