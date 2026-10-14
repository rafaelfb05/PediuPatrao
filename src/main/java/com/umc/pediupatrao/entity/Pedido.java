package com.umc.pediupatrao.entity;

import com.umc.pediupatrao.enums.StatusPedido;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "pedidos")
public class Pedido {

    @Id
    private String id;
    private String clienteId;
    private List<ItemPedido> itens = new ArrayList<>();
    private StatusPedido status;
    private Double descontoPercentual = 0.0;
    private Double valorTotal;
    private String responsavel;
    private LocalDateTime dataEntrada;
    private LocalDateTime dataSaida;
    private String responsavelSaida;
    private String justificativaCancelamento;
    private LocalDateTime dataCancelamento;
    private String responsavelCancelamento;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public List<ItemPedido> getItens() { return itens; }
    public void setItens(List<ItemPedido> itens) { this.itens = itens; }

    public StatusPedido getStatus() { return status; }
    public void setStatus(StatusPedido status) { this.status = status; }

    public Double getDescontoPercentual() { return descontoPercentual; }
    public void setDescontoPercentual(Double descontoPercentual) { this.descontoPercentual = descontoPercentual; }

    public Double getValorTotal() { return valorTotal; }
    public void setValorTotal(Double valorTotal) { this.valorTotal = valorTotal; }

    public String getResponsavel() { return responsavel; }
    public void setResponsavel(String responsavel) { this.responsavel = responsavel; }

    public LocalDateTime getDataEntrada() { return dataEntrada; }
    public void setDataEntrada(LocalDateTime dataEntrada) { this.dataEntrada = dataEntrada; }

    public LocalDateTime getDataSaida() { return dataSaida; }
    public void setDataSaida(LocalDateTime dataSaida) { this.dataSaida = dataSaida; }

    public String getResponsavelSaida() { return responsavelSaida; }
    public void setResponsavelSaida(String responsavelSaida) { this.responsavelSaida = responsavelSaida; }

    public String getJustificativaCancelamento() { return justificativaCancelamento; }
    public void setJustificativaCancelamento(String justificativaCancelamento) { this.justificativaCancelamento = justificativaCancelamento; }

    public LocalDateTime getDataCancelamento() { return dataCancelamento; }
    public void setDataCancelamento(LocalDateTime dataCancelamento) { this.dataCancelamento = dataCancelamento; }

    public String getResponsavelCancelamento() { return responsavelCancelamento; }
    public void setResponsavelCancelamento(String responsavelCancelamento) { this.responsavelCancelamento = responsavelCancelamento; }

    public void recalcularTotal() {
        double bruto = 0.0;
        for (ItemPedido item : itens) {
            bruto += item.getPrecoUnitario() * item.getQuantidade();
        }
        double desconto = descontoPercentual == null ? 0.0 : descontoPercentual;
        this.valorTotal = Math.round(bruto * (1 - desconto / 100.0) * 100.0) / 100.0;
    }
}
