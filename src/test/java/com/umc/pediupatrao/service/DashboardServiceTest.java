package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDTO;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.enums.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardServiceTest {

    private static final String INICIO = "2026-10-01";
    private static final String FIM = "2026-10-07";

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ProdutoRepository produtoRepository;
    @Mock
    private ClienteService clienteService;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void montarCenario() {
        when(produtoRepository.findAll()).thenReturn(List.of(
                produto("p1", "Pizza Calabresa", "Pizza", 50.0),
                produto("p2", "Pizza Mussarela", "Pizza", 40.0),
                produto("p3", "Refrigerante 2L", "Bebida", 10.0)));
        when(pedidoRepository.buscarPorPeriodo(any(), any())).thenReturn(pedidosDoCenario());
    }

    @Test
    @DisplayName("Resumo: faturamento, ticket médio, itens, descontos, cancelamentos e tempo de saída")
    void resumoCalculaOsIndicadores() {
        DashboardDTO.Resumo r = dashboardService.resumo(INICIO, FIM, null, null);

        assertEquals(LocalDate.of(2026, 10, 1), r.inicio());
        assertEquals(LocalDate.of(2026, 10, 7), r.fim());
        assertEquals(4, r.totalPedidos());
        assertEquals(3, r.pedidosVendidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(25.0, r.taxaCancelamento(), 0.001);
        assertEquals(166.0, r.faturamento(), 0.001);
        assertEquals(55.33, r.ticketMedio(), 0.001);
        assertEquals(6, r.itensVendidos());
        assertEquals(4.0, r.descontoTotal(), 0.001);
        assertEquals(25.0, r.tempoMedioSaidaMinutos(), 0.001);
    }

    @Test
    @DisplayName("Resumo: filtro por tipo considera só os itens daquele tipo")
    void resumoComFiltroDeTipo() {
        DashboardDTO.Resumo r = dashboardService.resumo(INICIO, FIM, null, "Pizza");

        assertEquals(3, r.totalPedidos());
        assertEquals(2, r.pedidosVendidos());
        assertEquals(1, r.pedidosCancelados());
        assertEquals(33.33, r.taxaCancelamento(), 0.01);
        assertEquals(136.0, r.faturamento(), 0.001);
        assertEquals(68.0, r.ticketMedio(), 0.001);
        assertEquals(3, r.itensVendidos());
    }

    @Test
    @DisplayName("Resumo: o filtro de tipo ignora maiúsculas e minúsculas")
    void filtroDeTipoIgnoraCaixa() {
        assertEquals(136.0, dashboardService.resumo(INICIO, FIM, null, "pizza").faturamento(), 0.001);
    }

    @Test
    @DisplayName("Resumo: filtro por status")
    void resumoComFiltroDeStatus() {
        DashboardDTO.Resumo finalizados = dashboardService.resumo(INICIO, FIM, "FINALIZADO", null);
        assertEquals(2, finalizados.totalPedidos());
        assertEquals(146.0, finalizados.faturamento(), 0.001);
        assertEquals(73.0, finalizados.ticketMedio(), 0.001);

        DashboardDTO.Resumo cancelados = dashboardService.resumo(INICIO, FIM, "CANCELADO", null);
        assertEquals(1, cancelados.totalPedidos());
        assertEquals(0, cancelados.pedidosVendidos());
        assertEquals(100.0, cancelados.taxaCancelamento(), 0.001);
        assertEquals(0.0, cancelados.faturamento(), 0.001);
        assertEquals(0.0, cancelados.ticketMedio(), 0.001);
        assertNull(cancelados.tempoMedioSaidaMinutos());
    }

    @Test
    @DisplayName("Resumo: período sem pedidos devolve zeros, sem erro de divisão")
    void resumoSemPedidos() {
        when(pedidoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of());

        DashboardDTO.Resumo r = dashboardService.resumo(INICIO, FIM, null, null);

        assertEquals(0, r.totalPedidos());
        assertEquals(0.0, r.faturamento(), 0.001);
        assertEquals(0.0, r.ticketMedio(), 0.001);
        assertEquals(0.0, r.taxaCancelamento(), 0.001);
        assertNull(r.tempoMedioSaidaMinutos());
    }

    @Test
    @DisplayName("Resumo: sem datas, usa os últimos 30 dias")
    void periodoPadraoSao30Dias() {
        DashboardDTO.Resumo r = dashboardService.resumo(null, null, null, null);

        assertEquals(LocalDate.now(), r.fim());
        assertEquals(LocalDate.now().minusDays(29), r.inicio());
    }

    @Test
    @DisplayName("Resumo: só a data final informada, o início fica 30 dias antes")
    void apenasDataFinalInformada() {
        DashboardDTO.Resumo r = dashboardService.resumo(null, "2026-10-31", null, null);

        assertEquals(LocalDate.of(2026, 10, 2), r.inicio());
        assertEquals(LocalDate.of(2026, 10, 31), r.fim());
    }

    @Test
    @DisplayName("Dados incompletos (sem status, sem data ou sem preço) não quebram o painel")
    void dadosIncompletosSaoTratados() {
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 5, 12, 0);
        Pedido semStatus = pedido("S", null, entrada, 0.0, item("p1", "Pizza Calabresa", 50.0, 1));
        Pedido semData = pedido("D", StatusPedido.FINALIZADO, null, 0.0, item("p1", "Pizza Calabresa", 50.0, 1));
        Pedido completo = pedido("OK", StatusPedido.FINALIZADO, entrada, 0.0, item("p1", "Pizza Calabresa", 50.0, 1));
        Pedido semPreco = pedido("SP", StatusPedido.FINALIZADO, entrada, 0.0);
        ItemPedido itemSemPreco = item("p1", "Pizza Calabresa", 0.0, 1);
        itemSemPreco.setPrecoUnitario(null);
        semPreco.getItens().add(itemSemPreco);
        when(pedidoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(semStatus, semData, completo, semPreco));

        DashboardDTO.Resumo r = dashboardService.resumo(INICIO, FIM, null, null);

        assertEquals(2, r.totalPedidos());
        assertEquals(50.0, r.faturamento(), 0.001);
    }

    @Test
    @DisplayName("Filtros: data inicial posterior à final é recusada")
    void dataInicialPosteriorEhRecusada() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> dashboardService.resumo("2026-10-10", "2026-10-01", null, null));
        assertTrue(erro.getMessage().contains("posterior"));
    }

    @Test
    @DisplayName("Filtros: data fora do formato AAAA-MM-DD é recusada")
    void dataInvalidaEhRecusada() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> dashboardService.resumo("abc", FIM, null, null));
        assertTrue(erro.getMessage().contains("AAAA-MM-DD"));
    }

    @Test
    @DisplayName("Filtros: período maior que 366 dias é recusado")
    void periodoMuitoLongoEhRecusado() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> dashboardService.resumo("2024-01-01", "2026-10-01", null, null));
        assertTrue(erro.getMessage().contains("366"));
    }

    @Test
    @DisplayName("Filtros: status inexistente é recusado")
    void statusInvalidoEhRecusado() {
        IllegalArgumentException erro = assertThrows(IllegalArgumentException.class,
                () -> dashboardService.resumo(INICIO, FIM, "VOANDO", null));
        assertTrue(erro.getMessage().contains("Status inválido"));
    }

    @Test
    @DisplayName("Vendas por dia: todos os dias do período aparecem, e cancelados não entram")
    void vendasPorDia() {
        List<DashboardDTO.VendaPeriodo> lista =
                dashboardService.vendasPorPeriodo("2026-10-05", "2026-10-07", null, null, "dia");

        assertEquals(3, lista.size());
        assertEquals("2026-10-05", lista.get(0).periodo());
        assertEquals(2, lista.get(0).pedidos());
        assertEquals(146.0, lista.get(0).faturamento(), 0.001);
        assertEquals("2026-10-06", lista.get(1).periodo());
        assertEquals(0, lista.get(1).pedidos());
        assertEquals(0.0, lista.get(1).faturamento(), 0.001);
        assertEquals("2026-10-07", lista.get(2).periodo());
        assertEquals(1, lista.get(2).pedidos());
        assertEquals(20.0, lista.get(2).faturamento(), 0.001);
    }

    @Test
    @DisplayName("Vendas por mês: agrupa por mês e mostra também os meses sem vendas")
    void vendasPorMes() {
        List<DashboardDTO.VendaPeriodo> lista =
                dashboardService.vendasPorPeriodo("2026-09-15", "2026-10-31", null, null, "mes");

        assertEquals(2, lista.size());
        assertEquals("2026-09", lista.get(0).periodo());
        assertEquals(0, lista.get(0).pedidos());
        assertEquals("2026-10", lista.get(1).periodo());
        assertEquals(3, lista.get(1).pedidos());
        assertEquals(166.0, lista.get(1).faturamento(), 0.001);
    }

    @Test
    @DisplayName("Vendas por período: agrupamento inexistente é recusado")
    void agrupamentoInvalidoEhRecusado() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.vendasPorPeriodo(INICIO, FIM, null, null, "ano"));
    }

    @Test
    @DisplayName("Vendas por tipo: soma quantidade e faturamento, do maior para o menor")
    void vendasPorTipo() {
        List<DashboardDTO.VendaTipo> lista = dashboardService.vendasPorTipo(INICIO, FIM, null, null);

        assertEquals(2, lista.size());
        assertEquals("Pizza", lista.get(0).tipo());
        assertEquals(3, lista.get(0).quantidade());
        assertEquals(136.0, lista.get(0).faturamento(), 0.001);
        assertEquals("Bebida", lista.get(1).tipo());
        assertEquals(3, lista.get(1).quantidade());
        assertEquals(30.0, lista.get(1).faturamento(), 0.001);
    }

    @Test
    @DisplayName("Vendas por tipo: produto que saiu do cadastro vai para 'Outros'")
    void produtoRemovidoVaiParaOutros() {
        Pedido pedido = pedido("X", StatusPedido.FINALIZADO, LocalDateTime.of(2026, 10, 5, 12, 0), 0.0,
                item("removido", "Pizza Antiga", 30.0, 2));
        when(pedidoRepository.buscarPorPeriodo(any(), any())).thenReturn(List.of(pedido));

        List<DashboardDTO.VendaTipo> lista = dashboardService.vendasPorTipo(INICIO, FIM, null, null);

        assertEquals(1, lista.size());
        assertEquals("Outros", lista.get(0).tipo());
        assertEquals(60.0, lista.get(0).faturamento(), 0.001);
    }

    @Test
    @DisplayName("Vendas por horário: sempre 24 faixas, com os picos nas horas certas")
    void vendasPorHorario() {
        List<DashboardDTO.VendaHorario> horas = dashboardService.vendasPorHorario(INICIO, FIM, null, null);

        assertEquals(24, horas.size());
        assertEquals(1, horas.get(19).pedidos());
        assertEquals(110.0, horas.get(19).faturamento(), 0.001);
        assertEquals(36.0, horas.get(20).faturamento(), 0.001);
        assertEquals(20.0, horas.get(13).faturamento(), 0.001);
        assertEquals(0, horas.get(12).pedidos());
    }

    @Test
    @DisplayName("Pedidos por status: todos os status aparecem, inclusive os com zero")
    void pedidosPorStatus() {
        List<DashboardDTO.PedidosPorStatus> lista = dashboardService.pedidosPorStatus(INICIO, FIM, null, null);

        assertEquals(StatusPedido.values().length, lista.size());
        assertEquals(1, quantidadeDoStatus(lista, "RECEBIDO"));
        assertEquals(2, quantidadeDoStatus(lista, "FINALIZADO"));
        assertEquals(1, quantidadeDoStatus(lista, "CANCELADO"));
        assertEquals(0, quantidadeDoStatus(lista, "PRONTO"));
    }

    @Test
    @DisplayName("Em andamento: só status abertos, os mais antigos primeiro, com o nome do cliente")
    void pedidosEmAndamento() {
        Cliente maria = new Cliente();
        maria.setId("cli1");
        maria.setNome("Maria");
        when(clienteService.listarClientes()).thenReturn(List.of(maria));

        LocalDateTime agora = LocalDateTime.now();
        Pedido recente = pedido("R", StatusPedido.PRONTO, agora.minusMinutes(10), 0.0,
                item("p1", "Pizza Calabresa", 50.0, 1));
        Pedido antigo = pedido("A", StatusPedido.RECEBIDO, agora.minusMinutes(90), 0.0,
                item("p3", "Refrigerante 2L", 10.0, 2));
        antigo.setClienteId("cliente-removido");
        when(pedidoRepository.findByStatusIn(any())).thenReturn(List.of(recente, antigo));

        List<DashboardDTO.PedidoAndamento> lista = dashboardService.pedidosEmAndamento();

        assertEquals(2, lista.size());
        assertEquals("A", lista.get(0).id());
        assertEquals("—", lista.get(0).cliente());
        assertTrue(lista.get(0).minutosEmAberto() >= 90);
        assertEquals("R", lista.get(1).id());
        assertEquals("Maria", lista.get(1).cliente());
        assertEquals(50.0, lista.get(1).valorTotal(), 0.001);

        ArgumentCaptor<Collection<StatusPedido>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(pedidoRepository).findByStatusIn(captor.capture());
        assertTrue(captor.getValue().contains(StatusPedido.RECEBIDO));
        assertFalse(captor.getValue().contains(StatusPedido.FINALIZADO));
        assertFalse(captor.getValue().contains(StatusPedido.CANCELADO));
    }

    @Test
    @DisplayName("Em andamento: a lista é limitada a 50 pedidos")
    void pedidosEmAndamentoTemLimite() {
        LocalDateTime agora = LocalDateTime.now();
        List<Pedido> muitos = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            muitos.add(pedido("P" + i, StatusPedido.RECEBIDO, agora.minusMinutes(i), 0.0,
                    item("p1", "Pizza Calabresa", 50.0, 1)));
        }
        when(pedidoRepository.findByStatusIn(any())).thenReturn(muitos);
        when(clienteService.listarClientes()).thenReturn(List.of());

        assertEquals(50, dashboardService.pedidosEmAndamento().size());
    }
    @Test
    @DisplayName("Ranking: por padrão ordena pela quantidade vendida")
    void rankingPorQuantidade() {
        List<DashboardDTO.ProdutoRanking> ranking =
                dashboardService.produtosMaisVendidos(INICIO, FIM, null, null, null, null);

        assertEquals(3, ranking.size());
        assertEquals(1, ranking.get(0).posicao());
        assertEquals("p3", ranking.get(0).produtoId());
        assertEquals(3, ranking.get(0).quantidade());
        assertEquals(30.0, ranking.get(0).faturamento(), 0.001);
        assertEquals("p1", ranking.get(1).produtoId());
        assertEquals("Pizza", ranking.get(1).tipo());
        assertEquals("p2", ranking.get(2).produtoId());
    }

    @Test
    @DisplayName("Ranking: ordenado por faturamento muda a ordem")
    void rankingPorFaturamento() {
        List<DashboardDTO.ProdutoRanking> ranking =
                dashboardService.produtosMaisVendidos(INICIO, FIM, null, null, null, "faturamento");

        assertEquals("p1", ranking.get(0).produtoId());
        assertEquals("p2", ranking.get(1).produtoId());
        assertEquals("p3", ranking.get(2).produtoId());
    }

    @Test
    @DisplayName("Ranking: respeita o limite e o filtro de tipo")
    void rankingComLimiteEFiltro() {
        assertEquals(2, dashboardService.produtosMaisVendidos(INICIO, FIM, null, null, "2", null).size());

        List<DashboardDTO.ProdutoRanking> bebidas =
                dashboardService.produtosMaisVendidos(INICIO, FIM, null, "Bebida", null, null);
        assertEquals(1, bebidas.size());
        assertEquals("p3", bebidas.get(0).produtoId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "21", "abc"})
    @DisplayName("Ranking: limite fora de 1 a 20 ou que não é número é recusado")
    void rankingRecusaLimiteInvalido(String limite) {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.produtosMaisVendidos(INICIO, FIM, null, null, limite, null));
    }

    @Test
    @DisplayName("Ranking: ordenação inexistente é recusada")
    void rankingRecusaOrdenacaoInvalida() {
        assertThrows(IllegalArgumentException.class,
                () -> dashboardService.produtosMaisVendidos(INICIO, FIM, null, null, null, "preco"));
    }
    @Test
    @DisplayName("Filtros: tipos distintos e ordenados, e todos os status do pedido")
    void opcoesDosFiltros() {
        when(produtoRepository.findAll()).thenReturn(List.of(
                produto("p1", "Pizza Calabresa", "Pizza", 50.0),
                produto("p2", "Pizza Mussarela", "Pizza", 40.0),
                produto("p3", "Refrigerante 2L", "Bebida", 10.0),
                produto("p4", "Sem tipo", null, 5.0),
                produto("p5", "Tipo em branco", "  ", 5.0)));

        DashboardDTO.Filtros filtros = dashboardService.filtros();

        assertEquals(List.of("Bebida", "Pizza"), filtros.tipos());
        assertEquals(StatusPedido.values().length, filtros.status().size());
        assertEquals("RECEBIDO", filtros.status().get(0).valor());
        assertEquals("Recebido", filtros.status().get(0).descricao());
    }
    private List<Pedido> pedidosDoCenario() {
        LocalDateTime entradaA = LocalDateTime.of(2026, 10, 5, 19, 30);
        Pedido a = pedido("A", StatusPedido.FINALIZADO, entradaA, 0.0,
                item("p1", "Pizza Calabresa", 50.0, 2), item("p3", "Refrigerante 2L", 10.0, 1));
        a.setDataSaida(entradaA.plusMinutes(30));

        LocalDateTime entradaB = LocalDateTime.of(2026, 10, 5, 20, 15);
        Pedido b = pedido("B", StatusPedido.FINALIZADO, entradaB, 10.0,
                item("p2", "Pizza Mussarela", 40.0, 1));
        b.setDataSaida(entradaB.plusMinutes(20));

        Pedido c = pedido("C", StatusPedido.CANCELADO, LocalDateTime.of(2026, 10, 6, 12, 0), 0.0,
                item("p1", "Pizza Calabresa", 50.0, 1));

        Pedido d = pedido("D", StatusPedido.RECEBIDO, LocalDateTime.of(2026, 10, 7, 13, 0), 0.0,
                item("p3", "Refrigerante 2L", 10.0, 2));

        return List.of(a, b, c, d);
    }

    private Pedido pedido(String id, StatusPedido status, LocalDateTime entrada, double desconto, ItemPedido... itens) {
        Pedido pedido = new Pedido();
        pedido.setId(id);
        pedido.setClienteId("cli1");
        pedido.setStatus(status);
        pedido.setDataEntrada(entrada);
        pedido.setDescontoPercentual(desconto);
        pedido.getItens().addAll(Arrays.asList(itens));
        pedido.recalcularTotal();
        return pedido;
    }

    private ItemPedido item(String produtoId, String nome, double preco, int quantidade) {
        ItemPedido item = new ItemPedido();
        item.setProdutoId(produtoId);
        item.setNomeProduto(nome);
        item.setPrecoUnitario(preco);
        item.setQuantidade(quantidade);
        return item;
    }

    private Produto produto(String id, String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setId(id);
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }

    private long quantidadeDoStatus(List<DashboardDTO.PedidosPorStatus> lista, String status) {
        return lista.stream()
                .filter(s -> s.status().equals(status))
                .findFirst()
                .orElseThrow()
                .quantidade();
    }
}