package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.enums.StatusPedido;
import com.umc.pediupatrao.enums.TipoOperacao;
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
import java.util.Map;

@Service
public class PedidoService {

    private static final int QUANTIDADE_MAX = 99;
    private static final double DESCONTO_MAXIMO = 20.0;

    private final PedidoRepository pedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final ClienteService clienteService;
    private final AuditoriaService auditoriaService;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoRepository produtoRepository, ClienteService clienteService, AuditoriaService auditoriaService) {
        this.pedidoRepository = pedidoRepository;
        this.produtoRepository = produtoRepository;
        this.clienteService = clienteService;
        this.auditoriaService = auditoriaService;
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
            item.setPrecoUnitario(produto.getPreco());
            item.setQuantidade(itemRequest.quantidade());
            pedido.getItens().add(item);
        }

        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDescontoPercentual(0.0);
        pedido.setResponsavel(usuarioLogado());
        pedido.setDataEntrada(LocalDateTime.now());
        pedido.recalcularTotal();

        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.INCLUSAO_PEDIDO, RegistroAuditoria.PEDIDO, salvo.getId(),
                Map.of(), auditoriaService.camposPedido(salvo), null);

        return salvo;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido atualizarStatus(String id, String status) {
        Pedido pedido = buscarOuFalhar(id);

        StatusPedido atual = pedido.getStatus();
        StatusPedido destino = converterStatus(status);

        if (atual == StatusPedido.CANCELADO) {
            throw new EstadoInvalidoException("Pedido cancelado não pode ser alterado.");
        }
        if (!atual.podeIrPara(destino)) {
            throw new EstadoInvalidoException("Não é possível passar de "
                    + atual.getDescricao() + " para " + destino.getDescricao() + ".");
        }

        Map<String, String> antes = auditoriaService.camposPedido(pedido);

        pedido.setStatus(destino);

        if (destino.registraSaida()) {
            pedido.setDataSaida(LocalDateTime.now());
            pedido.setResponsavelSaida(usuarioLogado());
        }

        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_STATUS, RegistroAuditoria.PEDIDO, salvo.getId(),
                antes, auditoriaService.camposPedido(salvo), null);

        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelarPedido(String id, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new IllegalArgumentException("A justificativa do cancelamento é obrigatória.");
        }

        Pedido pedido = buscarOuFalhar(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new EstadoInvalidoException("Este pedido já está cancelado.");
        }
        if (!pedido.getStatus().antesDaSaida()) {
            throw new EstadoInvalidoException("Só é possível cancelar pedidos que ainda não saíram para entrega ou retirada. Status atual: "
                    + pedido.getStatus().getDescricao() + ".");
        }

        Map<String, String> antes = auditoriaService.camposPedido(pedido);

        pedido.setStatus(StatusPedido.CANCELADO);
        pedido.setJustificativaCancelamento(justificativa.trim());
        pedido.setDataCancelamento(LocalDateTime.now());
        pedido.setResponsavelCancelamento(usuarioLogado());

        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.CANCELAMENTO, RegistroAuditoria.PEDIDO, salvo.getId(),
                antes, auditoriaService.camposPedido(salvo), justificativa.trim());

        return salvo;
    }


    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, Double percentual) {
        if (percentual == null || percentual.isNaN() || percentual < 0 || percentual > DESCONTO_MAXIMO) {
            throw new IllegalArgumentException("O desconto deve ficar entre 0% e 20%.");
        }

        Pedido pedido = buscarOuFalhar(id);

        if (pedido.getStatus() == StatusPedido.CANCELADO) {
            throw new EstadoInvalidoException("Pedido cancelado não pode receber desconto.");
        }
        if (!pedido.getStatus().antesDaSaida()) {
            throw new EstadoInvalidoException("O desconto só pode ser aplicado antes da saída para entrega ou retirada. Status atual: "
                    + pedido.getStatus().getDescricao() + ".");
        }

        Map<String, String> antes = auditoriaService.camposPedido(pedido);

        pedido.setDescontoPercentual(percentual);
        pedido.recalcularTotal();
        Pedido salvo = pedidoRepository.save(pedido);

        auditoriaService.registrar(TipoOperacao.DESCONTO, RegistroAuditoria.PEDIDO, salvo.getId(),
                antes, auditoriaService.camposPedido(salvo), null);

        return salvo;
    }

    private Pedido buscarOuFalhar(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
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
