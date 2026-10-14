package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.enums.StatusPedido;
import com.umc.pediupatrao.enums.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @BeforeEach
    void autenticarComoAtendente() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "atendente", null, List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE"))));
    }

    @AfterEach
    void limparSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Registra usuário, perfil, data/hora e somente os campos que mudaram")
    void registraSomenteCamposAlterados() {
        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("telefone", "(11) 1111-1111");
        antes.put("cidade", "Mogi das Cruzes");
        Map<String, String> depois = new LinkedHashMap<>();
        depois.put("telefone", "(11) 2222-2222");
        depois.put("cidade", "Mogi das Cruzes");

        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, RegistroAuditoria.CLIENTE, "cli1", antes, depois, null);

        RegistroAuditoria registro = capturarRegistro();
        assertEquals("atendente", registro.getUsuario());
        assertEquals("ATENDENTE", registro.getPerfil());
        assertEquals(TipoOperacao.ALTERACAO_CLIENTE, registro.getOperacao());
        assertEquals(RegistroAuditoria.CLIENTE, registro.getEntidade());
        assertEquals("cli1", registro.getEntidadeId());
        assertNotNull(registro.getDataHora());
        assertNull(registro.getJustificativa());
        assertEquals(1, registro.getAlteracoes().size());
        assertEquals("telefone", registro.getAlteracoes().get(0).getCampo());
        assertEquals("(11) 1111-1111", registro.getAlteracoes().get(0).getValorAnterior());
        assertEquals("(11) 2222-2222", registro.getAlteracoes().get(0).getValorNovo());
    }

    @Test
    @DisplayName("Não registra nada quando nenhum campo mudou")
    void naoRegistraSemMudanca() {
        Map<String, String> campos = Map.of("telefone", "(11) 1111-1111");

        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, RegistroAuditoria.CLIENTE, "cli1", campos, campos, null);

        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Valor vazio e valor nulo são tratados como iguais (sem alteração falsa)")
    void vazioENuloSaoIguais() {
        Map<String, String> antes = new LinkedHashMap<>();
        antes.put("complemento", null);
        Map<String, String> depois = new LinkedHashMap<>();
        depois.put("complemento", "");

        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, RegistroAuditoria.CLIENTE, "cli1", antes, depois, null);

        verify(auditoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Exclusão de cliente é registrada só com o id, sem dados pessoais")
    void exclusaoDeClienteNaoCopiaDadosPessoais() {
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, RegistroAuditoria.CLIENTE, "cli1", Map.of(), Map.of(), null);

        RegistroAuditoria registro = capturarRegistro();
        assertEquals("cli1", registro.getEntidadeId());
        assertTrue(registro.getAlteracoes().isEmpty());
    }

    @Test
    @DisplayName("Guarda a justificativa quando a operação exige (cancelamento)")
    void guardaJustificativa() {
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO, RegistroAuditoria.PEDIDO, "ped1",
                Map.of("status", "Recebido"), Map.of("status", "Cancelado"), "falta de ingredientes");

        RegistroAuditoria registro = capturarRegistro();
        assertEquals("falta de ingredientes", registro.getJustificativa());
        assertEquals("Recebido", registro.getAlteracoes().get(0).getValorAnterior());
        assertEquals("Cancelado", registro.getAlteracoes().get(0).getValorNovo());
    }

    @Test
    @DisplayName("Campos do pedido são formatados para leitura")
    void camposDoPedidoSaoLegiveis() {
        ItemPedido item = new ItemPedido();
        item.setProdutoId("p1");
        item.setNomeProduto("Pizza Calabresa");
        item.setPrecoUnitario(50.0);
        item.setQuantidade(2);

        Pedido pedido = new Pedido();
        pedido.getItens().add(item);
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDescontoPercentual(10.0);
        pedido.recalcularTotal();

        Map<String, String> campos = auditoriaService.camposPedido(pedido);

        assertEquals("Recebido", campos.get("status"));
        assertEquals("R$ 90,00", campos.get("valorTotal"));
        assertEquals("10,00%", campos.get("descontoPercentual"));
        assertTrue(campos.get("itens").contains("2× Pizza Calabresa"));
    }

    private RegistroAuditoria capturarRegistro() {
        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(auditoriaRepository).save(captor.capture());
        return captor.getValue();
    }
}