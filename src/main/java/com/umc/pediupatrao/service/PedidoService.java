package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.enums.StatusPedido;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.exception.EstadoInvalidoException;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PedidoService {

    private static final int QUANTIDADE_MAX = 99;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteService clienteService;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository, ClienteService clienteService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteService = clienteService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoRequest request) {
        if (request == null || request.clienteId() == null || request.clienteId().isBlank()) {
            throw new IllegalArgumentException("Selecione o cliente do pedido.");
        }
        if (clienteService.buscarPorId(request.clienteId()).isEmpty()) {
            throw new IllegalArgumentException("Cliente não encontrado.");
        }
        if (request.itens() == null || request.itens().isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa ter pelo menos um item.");
        }

        Pedido pedido = new Pedido();
        pedido.setClienteId(request.clienteId());

        for (PedidoRequest.ItemRequest itemRequest : request.itens()) {
            if (itemRequest == null || itemRequest.produtoId() == null || itemRequest.produtoId().isBlank()) {
                throw new IllegalArgumentException("Selecione o produto em todos os itens.");
            }
            if (itemRequest.quantidade() < 1 || itemRequest.quantidade() > QUANTIDADE_MAX) {
                throw new IllegalArgumentException("A quantidade de cada item deve ficar entre 1 e " + QUANTIDADE_MAX + ".");
            }

            Produto produto = produtoRepository.findById(itemRequest.produtoId())
                    .orElseThrow(() -> new IllegalArgumentException("Produto não encontrado."));
            if (!Boolean.TRUE.equals(produto.getAtivo()) || produto.getPreco() == null) {
                throw new IllegalArgumentException("O produto \"" + produto.getNome() + "\" não está disponível.");
            }

            ItemPedido item = new ItemPedido();
            item.setProdutoId(produto.getId());
            item.setNomeProduto(produto.getNome());
            item.setPrecoUnitario(produto.getPreco()); // preço vem do cadastro, não da requisição
            item.setQuantidade(itemRequest.quantidade());
            pedido.getItens().add(item);
        }

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDescontoPercentual(0.0);
        pedido.setResponsavel(usuarioLogado());
        pedido.setDataEntrada(LocalDateTime.now());
        pedido.recalcularTotal();

        return pedidoRepository.save(pedido);
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));

        StatusPedido atual = pedido.getStatus();
        StatusPedido destino = converterStatus(status);

        if (atual == StatusPedido.CANCELADO) {
            throw new EstadoInvalidoException("Pedido cancelado não pode ser alterado.");
        }
        if (!atual.podeIrPara(destino)) {
            throw new EstadoInvalidoException("Não é possível passar de "
                    + atual.getDescricao() + " para " + destino.getDescricao() + ".");
        }

        pedido.setStatus(destino);

        if (destino.registraSaida()) {
            pedido.setDataSaida(LocalDateTime.now());
            pedido.setResponsavelSaida(usuarioLogado());
        }

        return pedidoRepository.save(pedido);
    }

    private StatusPedido converterStatus(String status) {
        try {
            return StatusPedido.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Status inválido: " + status);
        }
    }

    private String usuarioLogado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
