package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.enums.TipoOperacao;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ClienteService clienteService;

    @BeforeEach
    void preparar() {
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    @DisplayName("Cadastrar cliente gera registro de inclusão na auditoria")
    void novoClienteEAuditado() {
        clienteService.novoCliente(cliente(null, "(11) 1111-1111"));

        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_CLIENTE), eq(RegistroAuditoria.CLIENTE),
                any(), anyMap(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Alterar cliente existente gera registro de alteração na auditoria")
    void alteracaoDeClienteEAuditada() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "(11) 1111-1111")));

        clienteService.salvar(cliente("c1", "(11) 2222-2222"));

        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq(RegistroAuditoria.CLIENTE),
                eq("c1"), anyMap(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Alterar cliente que não existe é recusado e não grava nada")
    void alteracaoDeClienteInexistente() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> clienteService.salvar(cliente("c1", "(11) 2222-2222")));
        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("Excluir cliente existente gera registro de exclusão na auditoria")
    void exclusaoEAuditada() {
        when(clienteRepository.existsById("c1")).thenReturn(true);

        clienteService.excluir("c1");

        verify(clienteRepository).deleteById("c1");
        verify(auditoriaService).registrar(eq(TipoOperacao.EXCLUSAO_CLIENTE), eq(RegistroAuditoria.CLIENTE),
                eq("c1"), anyMap(), anyMap(), isNull());
    }

    @Test
    @DisplayName("Excluir cliente que não existe não audita nada")
    void exclusaoDeClienteInexistenteNaoAudita() {
        when(clienteRepository.existsById("c1")).thenReturn(false);

        clienteService.excluir("c1");

        verify(clienteRepository, never()).deleteById(anyString());
        verify(auditoriaService, never()).registrar(any(), any(), any(), anyMap(), anyMap(), any());
    }

    private Cliente cliente(String id, String telefone) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNome("Maria");
        cliente.setTelefone(telefone);
        return cliente;
    }
}