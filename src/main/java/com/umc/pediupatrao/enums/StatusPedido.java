package com.umc.pediupatrao.enums;

import java.util.EnumSet;
import java.util.Set;

public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusPedido(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public Set<StatusPedido> proximosStatus() {
        return switch (this) {
            case RECEBIDO -> EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO -> EnumSet.of(PRONTO);
            case PRONTO -> EnumSet.of(SAIU_PARA_ENTREGA, RETIRADO);
            case SAIU_PARA_ENTREGA, RETIRADO -> EnumSet.of(FINALIZADO);
            case FINALIZADO, CANCELADO -> EnumSet.noneOf(StatusPedido.class);
        };
    }

    public boolean podeIrPara(StatusPedido destino) {
        return proximosStatus().contains(destino);
    }

    public boolean registraSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }
}