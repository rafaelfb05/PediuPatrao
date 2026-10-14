package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.enums.TipoOperacao;
import com.umc.pediupatrao.repository.ClienteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ClienteService {

    @Autowired
    private ClienteRepository clienteRepository;
    @Autowired
    private AuditoriaService auditoriaService;

    public Cliente novoCliente(Cliente cliente) {
        return criar(cliente);
    }

    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    // Método para excluir cliente
    public void excluir(String id) {
        if (clienteRepository.existsById(id)) {
            clienteRepository.deleteById(id);
            auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, RegistroAuditoria.CLIENTE, id,
                    Map.of(), Map.of(), null);
        }
    }

    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    // Método para salvar um novo cliente ou atualizar um cliente existente
    public Cliente salvar(Cliente cliente) {

        if (cliente.getId() == null) {
            return criar(cliente);
        }
        Cliente anterior = clienteRepository.findById(cliente.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cliente não encontrado para atualização."));
        Map<String, String> antes = auditoriaService.camposCliente(anterior);

        Cliente atualizado = clienteRepository.save(cliente);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, RegistroAuditoria.CLIENTE, atualizado.getId(),
                antes, auditoriaService.camposCliente(atualizado), null);

        return atualizado;
    }

    private Cliente criar(Cliente cliente) {
        Cliente salvo = clienteRepository.save(cliente);
        auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, RegistroAuditoria.CLIENTE, salvo.getId(),
                Map.of(), Map.of(), null);
        return salvo;
    }
}
