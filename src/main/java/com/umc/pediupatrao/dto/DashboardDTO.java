package com.umc.pediupatrao.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DashboardDTO {

    private DashboardDTO() {
    }

    public record Resumo(
            LocalDate inicio,
            LocalDate fim,
            long totalPedidos,
            long pedidosVendidos,
            long pedidosCancelados,
            double taxaCancelamento,
            double faturamento,
            double ticketMedio,
            long itensVendidos,
            double descontoTotal,
            Double tempoMedioSaidaMinutos) {
    }

    public record VendaPeriodo(String periodo, long pedidos, double faturamento) {
    }

    public record VendaTipo(String tipo, long quantidade, double faturamento) {
    }

    public record VendaHorario(int hora, long pedidos, double faturamento) {
    }

    public record PedidosPorStatus(String status, String descricao, long quantidade) {
    }

    public record PedidoAndamento(String id, String cliente, String status, String statusDescricao,
                                  double valorTotal, LocalDateTime dataEntrada, long minutosEmAberto) {
    }

    public record ProdutoRanking(int posicao, String produtoId, String nome, String tipo,
                                 long quantidade, double faturamento) {
    }

    public record StatusOpcao(String valor, String descricao) {
    }

    public record Filtros(List<String> tipos, List<StatusOpcao> status) {
    }
}
