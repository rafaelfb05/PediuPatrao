package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.enums.StatusPedido;
import com.umc.pediupatrao.enums.TipoOperacao;
import com.umc.pediupatrao.exception.EstadoInvalidoException;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteService clienteService;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private PedidoService pedidoService;

    @BeforeEach
    void preparar() {
        autenticar("atendente", "ATENDENTE");
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @AfterEach
    void limparSeguranca() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Criar pedido: o servidor calcula o total com os preços do cadastro")
    void criarPedidoCalculaTotalNoServidor() {
        when(clienteService.buscarPorId("cli1")).thenReturn(Optional.of(new Cliente()));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Pizza Calabresa", 45.90, true)));
        when(produtoRepository.findById("p2")).thenReturn(Optional.of(produto("p2", "Refrigerante 2L", 12.00, true)));

        PedidoRequest request = new PedidoRequest("cli1", List.of(
                new PedidoRequest.ItemRequest("p1", 2),
                new PedidoRequest.ItemRequest("p2", 1)));

        Pedido pedido = pedidoService.criarPedido(request);

        assertEquals(103.80, pedido.getValorTotal(), 0.001);
        assertEquals(StatusPedido.RECEBIDO, pedido.getStatus());
        assertEquals("atendente", pedido.getResponsavel());
        assertNotNull(pedido.getDataEntrada());
        assertEquals(0.0, pedido.getDescontoPercentual(), 0.001);
        assertEquals(2, pedido.getItens().size());
        assertEquals(45.90, pedido.getItens().get(0).getPrecoUnitario(), 0.001);
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_PEDIDO), eq(RegistroAuditoria.PEDIDO),
                any(), anyMap(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Criar pedido: produto inativo é recusado")
    void criarPedidoRecusaProdutoInativo() {
        when(clienteService.buscarPorId("cli1")).thenReturn(Optional.of(new Cliente()));
        when(produtoRepository.findById("p1")).thenReturn(Optional.of(produto("p1", "Pizza", 45.90, false)));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criarPedido(new PedidoRequest("cli1", List.of(new PedidoRequest.ItemRequest("p1", 1)))));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criar pedido: pedido sem itens é recusado")
    void criarPedidoRecusaSemItens() {
        when(clienteService.buscarPorId("cli1")).thenReturn(Optional.of(new Cliente()));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criarPedido(new PedidoRequest("cli1", List.of())));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criar pedido: quantidade zero é recusada")
    void criarPedidoRecusaQuantidadeZero() {
        when(clienteService.buscarPorId("cli1")).thenReturn(Optional.of(new Cliente()));

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criarPedido(new PedidoRequest("cli1", List.of(new PedidoRequest.ItemRequest("p1", 0)))));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criar pedido: cliente inexistente é recusado")
    void criarPedidoRecusaClienteInexistente() {
        when(clienteService.buscarPorId("cli1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.criarPedido(new PedidoRequest("cli1", List.of(new PedidoRequest.ItemRequest("p1", 1)))));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Status: avança para o próximo passo válido e audita")
    void statusAvancaParaProximoValido() {
        pedidoNoBanco(StatusPedido.RECEBIDO);

        Pedido resultado = pedidoService.atualizarStatus("ped1", "EM_PREPARACAO");

        assertEquals(StatusPedido.EM_PREPARACAO, resultado.getStatus());
        assertNull(resultado.getDataSaida());
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_STATUS), eq(RegistroAuditoria.PEDIDO),
                eq("ped1"), anyMap(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Status: não é possível pular etapas")
    void statusNaoPodePularEtapa() {
        pedidoNoBanco(StatusPedido.RECEBIDO);

        EstadoInvalidoException erro = assertThrows(EstadoInvalidoException.class,
                () -> pedidoService.atualizarStatus("ped1", "FINALIZADO"));

        assertTrue(erro.getMessage().contains("Recebido"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Status: não é possível voltar para uma etapa anterior")
    void statusNaoPodeVoltar() {
        pedidoNoBanco(StatusPedido.PRONTO);

        assertThrows(EstadoInvalidoException.class,
                () -> pedidoService.atualizarStatus("ped1", "EM_PREPARACAO"));
    }

    @Test
    @DisplayName("Status: o endpoint de status não cancela pedido (cancelamento é do GERENTE)")
    void statusNaoCancelaPedido() {
        pedidoNoBanco(StatusPedido.RECEBIDO);

        assertThrows(EstadoInvalidoException.class,
                () -> pedidoService.atualizarStatus("ped1", "CANCELADO"));
    }

    @Test
    @DisplayName("Status: pedido cancelado não pode ser alterado")
    void statusPedidoCanceladoNaoMuda() {
        pedidoNoBanco(StatusPedido.CANCELADO);

        assertThrows(EstadoInvalidoException.class,
                () -> pedidoService.atualizarStatus("ped1", "EM_PREPARACAO"));
    }

    @Test
    @DisplayName("Status: pedido finalizado não tem próximo passo")
    void statusFinalizadoNaoAvanca() {
        pedidoNoBanco(StatusPedido.FINALIZADO);

        assertThrows(EstadoInvalidoException.class,
                () -> pedidoService.atualizarStatus("ped1", "RETIRADO"));
    }

    @Test
    @DisplayName("Saída: retirada registra data/hora e responsável definidos pelo servidor")
    void retiradaRegistraSaida() {
        pedidoNoBanco(StatusPedido.PRONTO);

        Pedido resultado = pedidoService.atualizarStatus("ped1", "RETIRADO");

        assertEquals(StatusPedido.RETIRADO, resultado.getStatus());
        assertNotNull(resultado.getDataSaida());
        assertEquals("atendente", resultado.getResponsavelSaida());
    }

    @Test
    @DisplayName("Saída: saída para entrega também registra data/hora e responsável")
    void saidaParaEntregaRegistraSaida() {
        pedidoNoBanco(StatusPedido.PRONTO);

        Pedido resultado = pedidoService.atualizarStatus("ped1", "SAIU_PARA_ENTREGA");

        assertNotNull(resultado.getDataSaida());
        assertEquals("atendente", resultado.getResponsavelSaida());
    }

    @Test
    @DisplayName("Status: valor inexistente é recusado")
    void statusInexistenteERecusado() {
        pedidoNoBanco(StatusPedido.RECEBIDO);

        assertThrows(IllegalArgumentException.class,
                () -> pedidoService.atualizarStatus("ped1", "VOANDO"));
    }

    @Test
    @DisplayName("Status: pedido inexistente gera erro de não encontrado")
    void statusPedidoInexistente() {
        when(pedidoRepository.findById("nao-existe")).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class,
                () -> pedidoService.atualizarStatus("nao-existe", "EM_PREPARACAO"));
    }

    @Test
    @DisplayName("Desconto: 10% sobre R$ 100,00 resulta em R$ 90,00 e é auditado")
    void descontoRecalculaTotalNoServidor() {
        pedidoNoBanco(StatusPedido.RECEBIDO);
        autenticar("gerente", "GERENTE");

        Pedido resultado = pedidoService.aplicarDesconto("ped1", 10.0);

        assertEquals(10.0, resultado.getDescontoPercentual(), 0.001);
        assertEquals(90.0, resultado.getValorTotal(), 0.001);
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO), eq(RegistroAuditoria.PEDIDO),
                eq("ped1"), anyMap(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Desconto: o novo percentual substitui o anterior (não acumula)")
    void descontoSubstituiOAnterior() {
        pedidoNoBanco(StatusPedido.RECEBIDO);
        autenticar("gerente", "GERENTE");

        pedidoService.aplicarDesconto("ped1", 10.0);
        Pedido resultado = pedidoService.aplicarDesconto("ped1", 5.0);

        assertEquals(5.0, resultado.getDescontoPercentual(), 0.001);
        assertEquals(95.0, resultado.getValorTotal(), 0.001);
    }

    @Test
    @DisplayName("Desconto: 20% é aceito e 0% remove o desconto")
    void descontoAceitaLimiteEZero() {
        pedidoNoBanco(StatusPedido.RECEBIDO);
        autenticar("gerente", "GERENTE");

        assertEquals(80.0, pedidoService.aplicarDesconto("ped1", 20.0).getValorTotal(), 0.001);
        assertEquals(100.0, pedidoService.aplicarDesconto("ped1", 0.0).getValorTotal(), 0.001);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-1.0, 20.01, 25.0, 100.0})
    @DisplayName("Desconto: percentual fora de 0% a 20% é recusado")
    void descontoForaDoLimiteERecusado(double percentual) {
        pedidoNoBanco(StatusPedido.RECEBIDO);
        autenticar("gerente", "GERENTE");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.aplicarDesconto("ped1", percentual));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Desconto: percentual nulo é recusado")
    void descontoNuloERecusado() {
        pedidoNoBanco(StatusPedido.RECEBIDO);
        autenticar("gerente", "GERENTE");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.aplicarDesconto("ped1", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO", "CANCELADO"})
    @DisplayName("Desconto: só é permitido antes da saída do pedido")
    void descontoBloqueadoDepoisDaSaida(String status) {
        pedidoNoBanco(StatusPedido.valueOf(status));
        autenticar("gerente", "GERENTE");

        assertThrows(EstadoInvalidoException.class, () -> pedidoService.aplicarDesconto("ped1", 10.0));
        verify(pedidoRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Cancelamento: justificativa vazia é recusada")
    void cancelamentoExigeJustificativa(String justificativa) {
        pedidoNoBanco(StatusPedido.RECEBIDO);
        autenticar("gerente", "GERENTE");

        assertThrows(IllegalArgumentException.class, () -> pedidoService.cancelarPedido("ped1", justificativa));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancelamento: antes da saída, preserva os dados e registra a justificativa")
    void cancelamentoPreservaDadosERegistraJustificativa() {
        pedidoNoBanco(StatusPedido.EM_PREPARACAO);
        autenticar("gerente", "GERENTE");

        Pedido resultado = pedidoService.cancelarPedido("ped1", "  falta de ingredientes  ");

        assertEquals(StatusPedido.CANCELADO, resultado.getStatus());
        assertEquals("falta de ingredientes", resultado.getJustificativaCancelamento());
        assertEquals("gerente", resultado.getResponsavelCancelamento());
        assertNotNull(resultado.getDataCancelamento());
        // nada é apagado: itens e valor continuam no pedido
        assertEquals(1, resultado.getItens().size());
        assertEquals(100.0, resultado.getValorTotal(), 0.001);
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO), eq(RegistroAuditoria.PEDIDO),
                eq("ped1"), anyMap(), anyMap(), eq("falta de ingredientes"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAIU_PARA_ENTREGA", "RETIRADO", "FINALIZADO"})
    @DisplayName("Cancelamento: bloqueado depois da saída do pedido")
    void cancelamentoBloqueadoDepoisDaSaida(String status) {
        pedidoNoBanco(StatusPedido.valueOf(status));
        autenticar("gerente", "GERENTE");

        assertThrows(EstadoInvalidoException.class, () -> pedidoService.cancelarPedido("ped1", "motivo"));
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancelamento: pedido já cancelado não pode ser cancelado de novo")
    void cancelamentoNaoSeRepete() {
        pedidoNoBanco(StatusPedido.CANCELADO);
        autenticar("gerente", "GERENTE");

        assertThrows(EstadoInvalidoException.class, () -> pedidoService.cancelarPedido("ped1", "motivo"));
    }

    private void autenticar(String usuario, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + perfil))));
    }

    private Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setPreco(preco);
        produto.setAtivo(ativo);
        return produto;
    }

    private Pedido pedidoNoBanco(StatusPedido status) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId("prod1");
        item.setNomeProduto("Pizza");
        item.setPrecoUnitario(50.0);
        item.setQuantidade(2);

        Pedido pedido = new Pedido();
        pedido.setId("ped1");
        pedido.setClienteId("cli1");
        pedido.getItens().add(item);
        pedido.setStatus(status);
        pedido.setDescontoPercentual(0.0);
        pedido.recalcularTotal();

        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(pedido));
        return pedido;
    }
}