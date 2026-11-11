package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.enums.StatusPedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByClienteId(String clienteId);

    @Query("{ 'dataEntrada': { $gte: ?0, $lt: ?1 } }")
    List<Pedido> buscarPorPeriodo(LocalDateTime inicio, LocalDateTime fim);

    List<Pedido> findByStatusIn(Collection<StatusPedido> status);

    boolean existsByResponsavel(String responsavel);

    long deleteByResponsavel(String responsavel);
}
