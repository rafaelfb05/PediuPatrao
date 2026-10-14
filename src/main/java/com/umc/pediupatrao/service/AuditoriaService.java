package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.enums.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AuditoriaService {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = auditoriaRepository;
    }

    public void registrar(TipoOperacao operacao, String entidade, String entidadeId,
                          Map<String, String> antes, Map<String, String> depois, String justificativa) {

        List<AlteracaoCampo> alteracoes = compararCampos(antes, depois);

        boolean semCampos = operacao == TipoOperacao.INCLUSAO_CLIENTE || operacao == TipoOperacao.EXCLUSAO_CLIENTE;
        if (alteracoes.isEmpty() && !semCampos) {
            return;
        }

        auditoriaRepository.save(new RegistroAuditoria(
                LocalDateTime.now(), usuarioLogado(), perfilLogado(),
                operacao, entidade, entidadeId, alteracoes, justificativa));
    }

    public Map<String, String> camposPedido(Pedido p) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("clienteId", p.getClienteId());
        campos.put("itens", p.getItens() == null ? null : p.getItens().stream()
                .map(i -> i.getQuantidade() + "× " + i.getNomeProduto() + " (" + dinheiro(i.getPrecoUnitario()) + ")")
                .collect(Collectors.joining("; ")));
        campos.put("status", p.getStatus() == null ? null : p.getStatus().getDescricao());
        campos.put("descontoPercentual", p.getDescontoPercentual() == null ? null
                : String.format(PT_BR, "%.2f%%", p.getDescontoPercentual()));
        campos.put("valorTotal", dinheiro(p.getValorTotal()));
        campos.put("dataSaida", p.getDataSaida() == null ? null : p.getDataSaida().format(FORMATO_DATA));
        campos.put("responsavelSaida", p.getResponsavelSaida());
        return campos;
    }

    public Map<String, String> camposCliente(Cliente c) {
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("nome", c.getNome());
        campos.put("telefone", c.getTelefone());
        campos.put("cep", c.getCep());
        campos.put("logradouro", c.getLogradouro());
        campos.put("numero", c.getNumero());
        campos.put("complemento", c.getComplemento());
        campos.put("bairro", c.getBairro());
        campos.put("cidade", c.getCidade());
        campos.put("estado", c.getEstado());
        campos.put("endereco", c.getEndereco());
        return campos;
    }

    private List<AlteracaoCampo> compararCampos(Map<String, String> antes, Map<String, String> depois) {
        List<AlteracaoCampo> alteracoes = new ArrayList<>();

        Set<String> campos = new LinkedHashSet<>(depois.keySet());
        campos.addAll(antes.keySet());

        for (String campo : campos) {
            String valorAntes = normalizar(antes.get(campo));
            String valorDepois = normalizar(depois.get(campo));
            if (!Objects.equals(valorAntes, valorDepois)) {
                alteracoes.add(new AlteracaoCampo(campo, valorAntes, valorDepois));
            }
        }
        return alteracoes;
    }

    private String normalizar(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private String dinheiro(Double valor) {
        return valor == null ? null : String.format(PT_BR, "R$ %.2f", valor);
    }

    private String usuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "sistema";
    }

    private String perfilLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "SISTEMA";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .findFirst()
                .orElse("SEM_PERFIL");
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<RegistroAuditoria> listar(String entidade) {
        if (entidade == null || entidade.isBlank()) {
            return auditoriaRepository.findAllByOrderByDataHoraDesc();
        }
        return auditoriaRepository.findByEntidadeOrderByDataHoraDesc(entidade.trim().toUpperCase());
    }
}
