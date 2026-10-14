package com.umc.pediupatrao.enums;

public enum TipoOperacao {

    INCLUSAO_PEDIDO("Inclusão de pedido"),
    ALTERACAO_STATUS("Alteração de status"),
    DESCONTO("Desconto em pedido"),
    CANCELAMENTO("Cancelamento de pedido"),
    INCLUSAO_CLIENTE("Inclusão de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
