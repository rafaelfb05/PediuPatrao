package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.DashboardDTO;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @Test
    @DisplayName("GET /resumo: devolve o JSON dos indicadores e repassa os filtros ao serviço")
    void resumoDevolveJsonERepassaFiltros() throws Exception {
        DashboardDTO.Resumo resumo = new DashboardDTO.Resumo(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7),
                4, 3, 1, 25.0, 166.0, 55.33, 6, 4.0, 25.0);
        when(dashboardService.resumo("2026-10-01", "2026-10-07", "FINALIZADO", "Pizza")).thenReturn(resumo);

        mockMvc.perform(get("/api/dashboard/resumo")
                        .param("inicio", "2026-10-01")
                        .param("fim", "2026-10-07")
                        .param("status", "FINALIZADO")
                        .param("tipo", "Pizza")
                        .with(user("gerente").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-10-01"))
                .andExpect(jsonPath("$.fim").value("2026-10-07"))
                .andExpect(jsonPath("$.totalPedidos").value(4))
                .andExpect(jsonPath("$.pedidosCancelados").value(1))
                .andExpect(jsonPath("$.faturamento").value(166.0))
                .andExpect(jsonPath("$.ticketMedio").value(55.33))
                .andExpect(jsonPath("$.tempoMedioSaidaMinutos").value(25.0));
    }

    @Test
    @DisplayName("GET /resumo sem parâmetros: todos os filtros chegam nulos ao serviço")
    void semParametrosRepassaNulos() throws Exception {
        mockMvc.perform(get("/api/dashboard/resumo").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(dashboardService).resumo(isNull(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("Erro de validação do serviço vira 400 com a mensagem no campo 'erro'")
    void erroDeValidacaoVira400() throws Exception {
        when(dashboardService.resumo(eq("abc"), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Data inválida em 'inicio': use o formato AAAA-MM-DD."));

        mockMvc.perform(get("/api/dashboard/resumo")
                        .param("inicio", "abc")
                        .with(user("gerente").roles("GERENTE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(containsString("AAAA-MM-DD")));
    }

    @Test
    @DisplayName("GET /produtos-mais-vendidos: repassa limite e ordenação e devolve o ranking")
    void rankingRepassaParametros() throws Exception {
        when(dashboardService.produtosMaisVendidos(any(), any(), any(), any(), eq("3"), eq("faturamento")))
                .thenReturn(List.of(new DashboardDTO.ProdutoRanking(1, "p1", "Pizza Calabresa", "Pizza", 2, 100.0)));

        mockMvc.perform(get("/api/dashboard/produtos-mais-vendidos")
                        .param("limite", "3")
                        .param("ordenarPor", "faturamento")
                        .with(user("gerente").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].posicao").value(1))
                .andExpect(jsonPath("$[0].nome").value("Pizza Calabresa"))
                .andExpect(jsonPath("$[0].quantidade").value(2))
                .andExpect(jsonPath("$[0].faturamento").value(100.0));
    }

    @Test
    @DisplayName("GET /pedidos-em-andamento: devolve a lista de pedidos abertos")
    void pedidosEmAndamentoDevolveLista() throws Exception {
        when(dashboardService.pedidosEmAndamento()).thenReturn(List.of(
                new DashboardDTO.PedidoAndamento("ped1", "Maria", "RECEBIDO", "Recebido",
                        50.0, LocalDateTime.of(2026, 10, 7, 13, 0), 95)));

        mockMvc.perform(get("/api/dashboard/pedidos-em-andamento").with(user("gerente").roles("GERENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cliente").value("Maria"))
                .andExpect(jsonPath("$[0].statusDescricao").value("Recebido"))
                .andExpect(jsonPath("$[0].minutosEmAberto").value(95));
    }

    @Test
    @DisplayName("GET /filtros: devolve as opções de tipo e de status")
    void filtrosDevolveOpcoes() throws Exception {
        when(dashboardService.filtros()).thenReturn(new DashboardDTO.Filtros(
                List.of("Bebida", "Pizza"),
                List.of(new DashboardDTO.StatusOpcao("RECEBIDO", "Recebido"))));

        mockMvc.perform(get("/api/dashboard/filtros").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos[1]").value("Pizza"))
                .andExpect(jsonPath("$.status[0].valor").value("RECEBIDO"));
    }
}