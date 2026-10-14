package com.umc.pediupatrao.security;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import(SecurityConfig.class)
class AutorizacaoPorPerfilTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;
    @MockitoBean
    private ClienteService clienteService;
    @MockitoBean
    private ProdutoService produtoService;
    @MockitoBean
    private UsuarioService usuarioService;
    @MockitoBean
    private AuditoriaService auditoriaService;
    @MockitoBean
    private UsuarioDetailsService usuarioDetailsService;

    @BeforeEach
    void preparar() {
        when(usuarioService.listarTodos()).thenReturn(List.of());
    }

    @ParameterizedTest(name = "{0} é bloqueado em {1} {2}")
    @CsvSource({
            "ATENDENTE, PUT,    /api/pedidos/p1/cancelar",
            "ATENDENTE, PUT,    /api/pedidos/p1/desconto",
            "ATENDENTE, GET,    /api/auditoria",
            "ATENDENTE, GET,    /auditoria",
            "ATENDENTE, GET,    /api/usuarios",
            "ATENDENTE, POST,   /api/usuarios",
            "ATENDENTE, GET,    /usuarios",
            "ATENDENTE, DELETE, /api/clientes/c1",
            "ATENDENTE, POST,   /api/produtos",
            "ATENDENTE, DELETE, /api/produtos/x1",

            "GERENTE,   GET,    /api/usuarios",
            "GERENTE,   POST,   /api/usuarios",
            "GERENTE,   GET,    /usuarios",
            "GERENTE,   POST,   /api/clientes",
            "GERENTE,   PUT,    /api/clientes/c1",
            "GERENTE,   DELETE, /api/auditoria/a1",
            "GERENTE,   POST,   /api/auditoria",
            "GERENTE,   PUT,    /api/auditoria/a1",

            "ADMIN,     POST,   /api/pedidos",
            "ADMIN,     PUT,    /api/pedidos/p1/status?status=EM_PREPARACAO",
            "ADMIN,     PUT,    /api/pedidos/p1/cancelar",
            "ADMIN,     PUT,    /api/pedidos/p1/desconto",
            "ADMIN,     GET,    /api/clientes",
            "ADMIN,     GET,    /clientes",
            "ADMIN,     POST,   /api/clientes",
            "ADMIN,     DELETE, /api/clientes/c1",
            "ADMIN,     GET,    /pedidos/novo",
            "ADMIN,     POST,   /api/produtos",
            "ADMIN,     DELETE, /api/auditoria/a1",
            "ADMIN,     POST,   /api/auditoria"
    })
    @DisplayName("Operação fora das permissões do perfil retorna 403")
    void operacaoNaoAutorizadaRetorna403(String perfil, String metodo, String url) throws Exception {
        executar(perfil, metodo, url).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} é autorizado em {1} {2}")
    @CsvSource({
            "ATENDENTE, POST,   /api/pedidos",
            "ATENDENTE, PUT,    /api/pedidos/p1/status?status=EM_PREPARACAO",
            "ATENDENTE, GET,    /api/pedidos",
            "ATENDENTE, GET,    /api/clientes",
            "ATENDENTE, POST,   /api/clientes",
            "ATENDENTE, PUT,    /api/clientes/c1",
            "ATENDENTE, GET,    /api/produtos",

            "GERENTE,   POST,   /api/pedidos",
            "GERENTE,   PUT,    /api/pedidos/p1/status?status=PRONTO",
            "GERENTE,   PUT,    /api/pedidos/p1/cancelar",
            "GERENTE,   PUT,    /api/pedidos/p1/desconto",
            "GERENTE,   GET,    /api/pedidos",
            "GERENTE,   GET,    /api/clientes",
            "GERENTE,   DELETE, /api/clientes/c1",
            "GERENTE,   GET,    /api/produtos",
            "GERENTE,   POST,   /api/produtos",
            "GERENTE,   GET,    /api/auditoria",

            "ADMIN,     GET,    /api/pedidos",
            "ADMIN,     GET,    /api/produtos",
            "ADMIN,     GET,    /api/auditoria",
            "ADMIN,     GET,    /api/usuarios",
            "ADMIN,     POST,   /api/usuarios"
    })
    @DisplayName("Operação dentro das permissões do perfil passa pela segurança")
    void operacaoAutorizadaPassaPelaSeguranca(String perfil, String metodo, String url) throws Exception {
        int status = executar(perfil, metodo, url).andReturn().getResponse().getStatus();

        assertNotEquals(403, status, "A segurança bloqueou uma operação que deveria ser permitida");
        assertFalse(status >= 300 && status < 400, "A requisição foi redirecionada em vez de autorizada");
    }

    @ParameterizedTest(name = "Sem login, {0} {1} redireciona para o login")
    @CsvSource({
            "GET,    /api/pedidos",
            "GET,    /api/auditoria",
            "GET,    /usuarios",
            "POST,   /api/usuarios",
            "DELETE, /api/clientes/c1",
            "PUT,    /api/pedidos/p1/cancelar"
    })
    @DisplayName("Sem autenticação, rotas protegidas redirecionam para o login")
    void semLoginRedirecionaParaLogin(String metodo, String url) throws Exception {
        mockMvc.perform(requisicao(metodo, url).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("CSRF: operação sensível sem token é recusada, mesmo para o perfil autorizado")
    void semTokenCsrfOperacaoEBloqueada() throws Exception {
        mockMvc.perform(requisicao("PUT", "/api/pedidos/p1/cancelar").with(user("gerente").roles("GERENTE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(requisicao("DELETE", "/api/clientes/c1").with(user("gerente").roles("GERENTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CSRF: a mesma operação com token válido é aceita para o GERENTE")
    void comTokenCsrfOperacaoEAceita() throws Exception {
        executar("GERENTE", "PUT", "/api/pedidos/p1/cancelar").andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder requisicao(String metodo, String url) {
        return request(HttpMethod.valueOf(metodo), url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
    }

    private ResultActions executar(String perfil, String metodo, String url) throws Exception {
        return mockMvc.perform(requisicao(metodo, url).with(user("teste").roles(perfil)).with(csrf()));
    }
}