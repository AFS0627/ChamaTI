package br.com.chamati;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.chamati.entity.Categoria;
import br.com.chamati.entity.Chamado;
import br.com.chamati.entity.PoliticaSla;
import br.com.chamati.entity.Usuario;
import br.com.chamati.enums.Perfil;
import br.com.chamati.enums.Prioridade;
import br.com.chamati.enums.StatusChamado;
import br.com.chamati.enums.TipoComentario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/** Lê os dados de demonstração (V100) pelas entidades. Cada teste é revertido ao final. */
@Transactional
class EntidadesIntegrationTest extends IntegrationTestBase {

    @PersistenceContext
    EntityManager em;

    @Test
    void carregaChamadoComRelacionamentosEEnums() {
        Chamado c = em.find(Chamado.class, 3L);

        assertThat(c.getProtocolo()).isEqualTo("CT-2026-000003");
        assertThat(c.getStatus()).isEqualTo(StatusChamado.EM_ATENDIMENTO);
        assertThat(c.getPrioridade()).isEqualTo(Prioridade.CRITICA);
        assertThat(c.getCategoria().getNome()).isEqualTo("Rede e conectividade");
        assertThat(c.getSubcategoria().getNome()).isEqualTo("VPN");
        assertThat(c.getSubcategoria().getCategoria().getId()).isEqualTo(c.getCategoria().getId());
        assertThat(c.getSolicitante().getEmail()).isEqualTo("juliana.rocha@chamati.local");
        assertThat(c.getTecnico().getPerfil()).isEqualTo(Perfil.TECNICO);
        assertThat(c.getVersao()).isZero();
        assertThat(c.getSlaResolucaoLimite()).isNotNull();
        assertThat(c.getComentarios()).hasSize(3);
        assertThat(c.getComentarios())
                .anyMatch(k -> k.getTipo() == TipoComentario.NOTA_INTERNA);
        assertThat(c.getHistorico()).isNotEmpty();
    }

    @Test
    void chamadoReabertoPreservaContadores() {
        Chamado c = em.find(Chamado.class, 11L);

        assertThat(c.getTotalReaberturas()).isEqualTo(1);
        assertThat(c.getReabertoEm()).isNotNull();
        assertThat(c.getResolvidoEm()).isNull();
        assertThat(c.getSubcategoria()).isNull(); // subcategoria é opcional
    }

    @Test
    void carregaCategoriaComSubcategoriasEPoliticaSla() {
        Categoria hardware = em.find(Categoria.class, 1L);
        assertThat(hardware.getSubcategorias()).extracting("nome")
                .containsExactly("Desktop", "Monitor", "Notebook");

        PoliticaSla critica = em.createQuery(
                "select p from PoliticaSla p where p.prioridade = :p", PoliticaSla.class)
                .setParameter("p", Prioridade.CRITICA).getSingleResult();
        assertThat(critica.getPrazoPrimeiraRespostaMin()).isEqualTo(15);
        assertThat(critica.getPrazoResolucaoMin()).isEqualTo(240);
    }

    @Test
    void persisteChamadoNovoEIncrementaVersao() {
        Chamado c = new Chamado();
        c.setProtocolo("CT-TESTE-000001");
        c.setTitulo("Chamado de teste");
        c.setDescricao("Descrição de teste");
        c.setCategoria(em.getReference(Categoria.class, 1L));
        c.setSolicitante(em.getReference(Usuario.class, 4L));

        em.persist(c);
        em.flush();

        assertThat(c.getId()).isNotNull();
        assertThat(c.getStatus()).isEqualTo(StatusChamado.ABERTO);
        assertThat(c.getPrioridade()).isEqualTo(Prioridade.MEDIA);
        assertThat(c.getCriadoEm()).isNotNull();
        assertThat(c.getVersao()).isZero();

        c.setTitulo("Chamado de teste alterado");
        em.flush();

        assertThat(c.getVersao()).isEqualTo(1L);
    }
}
