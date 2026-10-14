package com.umc.pediupatrao.entity;

import com.umc.pediupatrao.enums.TipoOperacao;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "auditoria")
public class RegistroAuditoria {

    public static final String PEDIDO = "PEDIDO";
    public static final String CLIENTE = "CLIENTE";

    @Id
    private String id;
    private LocalDateTime dataHora;
    private String usuario;
    private String perfil;
    private TipoOperacao operacao;
    private String entidade;
    private String entidadeId;
    private List<AlteracaoCampo> alteracoes = new ArrayList<>();
    private String justificativa;

    public RegistroAuditoria() {
    }

    public RegistroAuditoria(LocalDateTime dataHora, String usuario, String perfil, TipoOperacao operacao,
                             String entidade, String entidadeId, List<AlteracaoCampo> alteracoes,
                             String justificativa) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.alteracoes = alteracoes;
        this.justificativa = justificativa;
    }

    public String getId() { return id; }
    public LocalDateTime getDataHora() { return dataHora; }
    public String getUsuario() { return usuario; }
    public String getPerfil() { return perfil; }
    public TipoOperacao getOperacao() { return operacao; }
    public String getEntidade() { return entidade; }
    public String getEntidadeId() { return entidadeId; }
    public List<AlteracaoCampo> getAlteracoes() { return alteracoes; }
    public String getJustificativa() { return justificativa; }
}
