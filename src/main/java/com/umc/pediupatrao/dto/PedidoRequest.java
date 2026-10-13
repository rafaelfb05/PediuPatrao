package com.umc.pediupatrao.dto;

import java.util.List;

public record PedidoRequest(String clienteId, List<ItemRequest> itens) {

    public record  ItemRequest(String produtoId, int quantidade){}
}
