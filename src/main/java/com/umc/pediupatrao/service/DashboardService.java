package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardDTO;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.enums.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    private static final int PERIODO_PADRAO_DIAS = 30;
    private static final int PERIODO_MAXIMO_DIAS = 366;
    private static final int LIMITE_PADRAO_RANKING = 5;
    private static final int LIMITE_MAXIMO_RANKING = 20;
    private static final int LIMITE_ANDAMENTO = 50;
    private static final String TIPO_DESCONHECIDO = "Outros";

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private ClienteService clienteService;

    private record Filtro(LocalDate inicio, LocalDate fim, StatusPedido status, String tipo) {
    }

    private static class Acumulador {
        long pedidos;
        long quantidade;
        double faturamento;
    }


    public DashboardDTO.Resumo resumo(String inicio, String fim, String status, String tipo) {
        Filtro filtro = montarFiltro(inicio, fim, status, tipo);
        Map<String, Produto> produtos = mapaProdutos();
        List<Pedido> pedidos = carregarPedidos(filtro, produtos);
        List<Pedido> vendidos = vendidos(pedidos);
        long cancelados = pedidos.size() - vendidos.size();

        double faturamento = 0.0;
        double descontoTotal = 0.0;
        long itensVendidos = 0;
        for (Pedido pedido : vendidos) {
            double desconto = descontoDe(pedido);
            for (ItemPedido item : itensConsiderados(pedido, filtro, produtos)) {
                double bruto = precoDe(item) * item.getQuantidade();
                faturamento += bruto * (1 - desconto / 100.0);
                descontoTotal += bruto * desconto / 100.0;
                itensVendidos += item.getQuantidade();
            }
        }

        double ticketMedio = vendidos.isEmpty() ? 0.0 : faturamento / vendidos.size();
        double taxaCancelamento = pedidos.isEmpty() ? 0.0 : cancelados * 100.0 / pedidos.size();

        OptionalDouble tempoMedio = vendidos.stream()
                .filter(p -> p.getDataEntrada() != null && p.getDataSaida() != null)
                .mapToLong(p -> Duration.between(p.getDataEntrada(), p.getDataSaida()).toMinutes())
                .average();

        return new DashboardDTO.Resumo(
                filtro.inicio(), filtro.fim(),
                pedidos.size(), vendidos.size(), cancelados,
                arredondar(taxaCancelamento), arredondar(faturamento), arredondar(ticketMedio),
                itensVendidos, arredondar(descontoTotal),
                tempoMedio.isPresent() ? arredondar(tempoMedio.getAsDouble()) : null);
    }


    public List<DashboardDTO.VendaPeriodo> vendasPorPeriodo(String inicio, String fim, String status,
                                                            String tipo, String agrupamento) {
        boolean porMes = "mes".equalsIgnoreCase(agrupamento);
        if (agrupamento != null && !agrupamento.isBlank() && !porMes && !"dia".equalsIgnoreCase(agrupamento)) {
            throw new IllegalArgumentException("Agrupamento inválido: use 'dia' ou 'mes'.");
        }

        Filtro filtro = montarFiltro(inicio, fim, status, tipo);
        Map<String, Produto> produtos = mapaProdutos();

        Map<String, Acumulador> grupos = new LinkedHashMap<>();
        if (porMes) {
            YearMonth ultimo = YearMonth.from(filtro.fim());
            for (YearMonth mes = YearMonth.from(filtro.inicio()); !mes.isAfter(ultimo); mes = mes.plusMonths(1)) {
                grupos.put(mes.toString(), new Acumulador());
            }
        } else {
            for (LocalDate dia = filtro.inicio(); !dia.isAfter(filtro.fim()); dia = dia.plusDays(1)) {
                grupos.put(dia.toString(), new Acumulador());
            }
        }

        for (Pedido pedido : vendidos(carregarPedidos(filtro, produtos))) {
            String chave = porMes
                    ? YearMonth.from(pedido.getDataEntrada()).toString()
                    : pedido.getDataEntrada().toLocalDate().toString();
            Acumulador acumulador = grupos.computeIfAbsent(chave, k -> new Acumulador());
            acumulador.pedidos++;
            acumulador.faturamento += faturamentoDoPedido(pedido, filtro, produtos);
        }

        return grupos.entrySet().stream()
                .map(e -> new DashboardDTO.VendaPeriodo(e.getKey(), e.getValue().pedidos,
                        arredondar(e.getValue().faturamento)))
                .toList();
    }

    public List<DashboardDTO.VendaTipo> vendasPorTipo(String inicio, String fim, String status, String tipo) {
        Filtro filtro = montarFiltro(inicio, fim, status, tipo);
        Map<String, Produto> produtos = mapaProdutos();

        Map<String, Acumulador> grupos = new HashMap<>();
        for (Pedido pedido : vendidos(carregarPedidos(filtro, produtos))) {
            double desconto = descontoDe(pedido);
            for (ItemPedido item : itensConsiderados(pedido, filtro, produtos)) {
                Acumulador acumulador = grupos.computeIfAbsent(tipoDoItem(item, produtos), k -> new Acumulador());
                acumulador.quantidade += item.getQuantidade();
                acumulador.faturamento += receitaLiquida(item, desconto);
            }
        }

        return grupos.entrySet().stream()
                .map(e -> new DashboardDTO.VendaTipo(e.getKey(), e.getValue().quantidade,
                        arredondar(e.getValue().faturamento)))
                .sorted(Comparator.comparingDouble(DashboardDTO.VendaTipo::faturamento).reversed())
                .toList();
    }

    public List<DashboardDTO.VendaHorario> vendasPorHorario(String inicio, String fim, String status, String tipo) {
        Filtro filtro = montarFiltro(inicio, fim, status, tipo);
        Map<String, Produto> produtos = mapaProdutos();

        Acumulador[] horas = new Acumulador[24];
        for (int h = 0; h < 24; h++) {
            horas[h] = new Acumulador();
        }

        for (Pedido pedido : vendidos(carregarPedidos(filtro, produtos))) {
            Acumulador acumulador = horas[pedido.getDataEntrada().getHour()];
            acumulador.pedidos++;
            acumulador.faturamento += faturamentoDoPedido(pedido, filtro, produtos);
        }

        return IntStream.range(0, 24)
                .mapToObj(h -> new DashboardDTO.VendaHorario(h, horas[h].pedidos, arredondar(horas[h].faturamento)))
                .toList();
    }

    public List<DashboardDTO.PedidosPorStatus> pedidosPorStatus(String inicio, String fim, String status, String tipo) {
        Filtro filtro = montarFiltro(inicio, fim, status, tipo);
        List<Pedido> pedidos = carregarPedidos(filtro, mapaProdutos());

        Map<StatusPedido, Long> contagem = new EnumMap<>(StatusPedido.class);
        for (Pedido pedido : pedidos) {
            contagem.merge(pedido.getStatus(), 1L, Long::sum);
        }

        return Arrays.stream(StatusPedido.values())
                .map(s -> new DashboardDTO.PedidosPorStatus(s.name(), s.getDescricao(), contagem.getOrDefault(s, 0L)))
                .toList();
    }

    public List<DashboardDTO.PedidoAndamento> pedidosEmAndamento() {
        List<StatusPedido> abertos = List.of(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO,
                StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO);

        Map<String, String> nomes = clienteService.listarClientes().stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Cliente::getId, c -> c.getNome() == null ? "" : c.getNome(), (a, b) -> a));

        LocalDateTime agora = LocalDateTime.now();

        return pedidoRepository.findByStatusIn(abertos).stream()
                .filter(p -> p.getDataEntrada() != null)
                .sorted(Comparator.comparing(Pedido::getDataEntrada))
                .limit(LIMITE_ANDAMENTO)
                .map(p -> new DashboardDTO.PedidoAndamento(
                        p.getId(),
                        nomes.getOrDefault(p.getClienteId(), "—"),
                        p.getStatus().name(),
                        p.getStatus().getDescricao(),
                        arredondar(p.getValorTotal() == null ? 0.0 : p.getValorTotal()),
                        p.getDataEntrada(),
                        Duration.between(p.getDataEntrada(), agora).toMinutes()))
                .toList();
    }

    public List<DashboardDTO.ProdutoRanking> produtosMaisVendidos(String inicio, String fim, String status,
                                                                  String tipo, String limite, String ordenarPor) {
        int maximo = LIMITE_PADRAO_RANKING;
        if (limite != null && !limite.isBlank()) {
            try {
                maximo = Integer.parseInt(limite.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("O limite deve ser um número inteiro.");
            }
        }
        if (maximo < 1 || maximo > LIMITE_MAXIMO_RANKING) {
            throw new IllegalArgumentException("O limite deve ficar entre 1 e " + LIMITE_MAXIMO_RANKING + ".");
        }

        boolean porFaturamento = "faturamento".equalsIgnoreCase(ordenarPor);
        if (ordenarPor != null && !ordenarPor.isBlank() && !porFaturamento && !"quantidade".equalsIgnoreCase(ordenarPor)) {
            throw new IllegalArgumentException("Ordenação inválida: use 'quantidade' ou 'faturamento'.");
        }

        Filtro filtro = montarFiltro(inicio, fim, status, tipo);
        Map<String, Produto> produtos = mapaProdutos();

        Map<String, Acumulador> grupos = new HashMap<>();
        Map<String, String> nomes = new HashMap<>();
        for (Pedido pedido : vendidos(carregarPedidos(filtro, produtos))) {
            double desconto = descontoDe(pedido);
            for (ItemPedido item : itensConsiderados(pedido, filtro, produtos)) {
                if (item.getProdutoId() == null) {
                    continue;
                }
                Acumulador acumulador = grupos.computeIfAbsent(item.getProdutoId(), k -> new Acumulador());
                acumulador.quantidade += item.getQuantidade();
                acumulador.faturamento += receitaLiquida(item, desconto);
                nomes.putIfAbsent(item.getProdutoId(), item.getNomeProduto());
            }
        }

        List<Map.Entry<String, Acumulador>> ordenados = new ArrayList<>(grupos.entrySet());
        ordenados.sort((a, b) -> {
            int comparacao = porFaturamento
                    ? Double.compare(b.getValue().faturamento, a.getValue().faturamento)
                    : Long.compare(b.getValue().quantidade, a.getValue().quantidade);
            if (comparacao != 0) {
                return comparacao;
            }
            return String.valueOf(nomes.get(a.getKey())).compareTo(String.valueOf(nomes.get(b.getKey())));
        });

        List<DashboardDTO.ProdutoRanking> ranking = new ArrayList<>();
        int posicao = 1;
        for (Map.Entry<String, Acumulador> entrada : ordenados) {
            if (posicao > maximo) {
                break;
            }
            String produtoId = entrada.getKey();
            ranking.add(new DashboardDTO.ProdutoRanking(
                    posicao++, produtoId, nomes.get(produtoId), tipoDoProduto(produtoId, produtos),
                    entrada.getValue().quantidade, arredondar(entrada.getValue().faturamento)));
        }
        return ranking;
    }

    public DashboardDTO.Filtros filtros() {
        List<String> tipos = produtoRepository.findAll().stream()
                .map(Produto::getTipo)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .sorted()
                .toList();

        List<DashboardDTO.StatusOpcao> status = Arrays.stream(StatusPedido.values())
                .map(s -> new DashboardDTO.StatusOpcao(s.name(), s.getDescricao()))
                .toList();

        return new DashboardDTO.Filtros(tipos, status);
    }

    private Filtro montarFiltro(String inicio, String fim, String status, String tipo) {
        LocalDate dataInicio = lerData(inicio, "inicio");
        LocalDate dataFim = lerData(fim, "fim");

        if (dataInicio == null && dataFim == null) {
            dataFim = LocalDate.now();
            dataInicio = dataFim.minusDays(PERIODO_PADRAO_DIAS - 1);
        } else if (dataInicio == null) {
            dataInicio = dataFim.minusDays(PERIODO_PADRAO_DIAS - 1);
        } else if (dataFim == null) {
            dataFim = LocalDate.now();
        }

        if (dataInicio.isAfter(dataFim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        }
        if (ChronoUnit.DAYS.between(dataInicio, dataFim) + 1 > PERIODO_MAXIMO_DIAS) {
            throw new IllegalArgumentException("O período máximo permitido é de " + PERIODO_MAXIMO_DIAS + " dias.");
        }

        StatusPedido statusFiltro = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFiltro = StatusPedido.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Status inválido: " + status + ".");
            }
        }

        String tipoFiltro = (tipo == null || tipo.isBlank()) ? null : tipo.trim();
        return new Filtro(dataInicio, dataFim, statusFiltro, tipoFiltro);
    }

    private LocalDate lerData(String valor, String parametro) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida em '" + parametro + "': use o formato AAAA-MM-DD.");
        }
    }

    private List<Pedido> carregarPedidos(Filtro filtro, Map<String, Produto> produtos) {
        List<Pedido> pedidos = pedidoRepository.buscarPorPeriodo(
                filtro.inicio().atStartOfDay(), filtro.fim().plusDays(1).atStartOfDay());

        return pedidos.stream()
                .filter(p -> p.getStatus() != null && p.getDataEntrada() != null)
                .filter(p -> filtro.status() == null || p.getStatus() == filtro.status())
                .filter(p -> filtro.tipo() == null || p.getItens().stream()
                        .anyMatch(i -> tipoDoItem(i, produtos).equalsIgnoreCase(filtro.tipo())))
                .toList();
    }

    private List<Pedido> vendidos(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> p.getStatus() != StatusPedido.CANCELADO).toList();
    }

    private List<ItemPedido> itensConsiderados(Pedido pedido, Filtro filtro, Map<String, Produto> produtos) {
        return pedido.getItens().stream()
                .filter(i -> filtro.tipo() == null || tipoDoItem(i, produtos).equalsIgnoreCase(filtro.tipo()))
                .toList();
    }

    private double faturamentoDoPedido(Pedido pedido, Filtro filtro, Map<String, Produto> produtos) {
        double desconto = descontoDe(pedido);
        double total = 0.0;
        for (ItemPedido item : itensConsiderados(pedido, filtro, produtos)) {
            total += receitaLiquida(item, desconto);
        }
        return total;
    }

    private Map<String, Produto> mapaProdutos() {
        Map<String, Produto> mapa = new HashMap<>();
        for (Produto produto : produtoRepository.findAll()) {
            if (produto.getId() != null) {
                mapa.put(produto.getId(), produto);
            }
        }
        return mapa;
    }

    private String tipoDoItem(ItemPedido item, Map<String, Produto> produtos) {
        return tipoDoProduto(item.getProdutoId(), produtos);
    }

    private String tipoDoProduto(String produtoId, Map<String, Produto> produtos) {
        Produto produto = produtoId == null ? null : produtos.get(produtoId);
        if (produto == null || produto.getTipo() == null || produto.getTipo().isBlank()) {
            return TIPO_DESCONHECIDO;
        }
        return produto.getTipo();
    }

    private double descontoDe(Pedido pedido) {
        return pedido.getDescontoPercentual() == null ? 0.0 : pedido.getDescontoPercentual();
    }

    private double precoDe(ItemPedido item) {
        return item.getPrecoUnitario() == null ? 0.0 : item.getPrecoUnitario();
    }

    private double receitaLiquida(ItemPedido item, double desconto) {
        return precoDe(item) * item.getQuantidade() * (1 - desconto / 100.0);
    }

    private double arredondar(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}