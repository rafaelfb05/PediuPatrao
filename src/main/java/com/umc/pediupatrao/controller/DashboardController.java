package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardDTO;
import com.umc.pediupatrao.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/resumo")
    public DashboardDTO.Resumo resumo(
            @RequestParam(name = "inicio", required = false) String inicio,
            @RequestParam(name = "fim", required = false) String fim,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tipo", required = false) String tipo) {
        return dashboardService.resumo(inicio, fim, status, tipo);
    }

    @GetMapping("/vendas-por-periodo")
    public List<DashboardDTO.VendaPeriodo> vendasPorPeriodo(
            @RequestParam(name = "inicio", required = false) String inicio,
            @RequestParam(name = "fim", required = false) String fim,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "agrupamento", required = false) String agrupamento) {
        return dashboardService.vendasPorPeriodo(inicio, fim, status, tipo, agrupamento);
    }

    @GetMapping("/vendas-por-tipo")
    public List<DashboardDTO.VendaTipo> vendasPorTipo(
            @RequestParam(name = "inicio", required = false) String inicio,
            @RequestParam(name = "fim", required = false) String fim,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tipo", required = false) String tipo) {
        return dashboardService.vendasPorTipo(inicio, fim, status, tipo);
    }

    @GetMapping("/vendas-por-horario")
    public List<DashboardDTO.VendaHorario> vendasPorHorario(
            @RequestParam(name = "inicio", required = false) String inicio,
            @RequestParam(name = "fim", required = false) String fim,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tipo", required = false) String tipo) {
        return dashboardService.vendasPorHorario(inicio, fim, status, tipo);
    }

    @GetMapping("/pedidos-por-status")
    public List<DashboardDTO.PedidosPorStatus> pedidosPorStatus(
            @RequestParam(name = "inicio", required = false) String inicio,
            @RequestParam(name = "fim", required = false) String fim,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tipo", required = false) String tipo) {
        return dashboardService.pedidosPorStatus(inicio, fim, status, tipo);
    }

    @GetMapping("/pedidos-em-andamento")
    public List<DashboardDTO.PedidoAndamento> pedidosEmAndamento() {
        return dashboardService.pedidosEmAndamento();
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<DashboardDTO.ProdutoRanking> produtosMaisVendidos(
            @RequestParam(name = "inicio", required = false) String inicio,
            @RequestParam(name = "fim", required = false) String fim,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "tipo", required = false) String tipo,
            @RequestParam(name = "limite", required = false) String limite,
            @RequestParam(name = "ordenarPor", required = false) String ordenarPor) {
        return dashboardService.produtosMaisVendidos(inicio, fim, status, tipo, limite, ordenarPor);
    }

    @GetMapping("/filtros")
    public DashboardDTO.Filtros filtros() {
        return dashboardService.filtros();
    }
}