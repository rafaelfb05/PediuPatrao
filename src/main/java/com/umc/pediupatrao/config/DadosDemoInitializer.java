package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.enums.StatusPedido;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Configuration
@ConditionalOnProperty(prefix = "app.dados-demo", name = "ativar", havingValue = "true")
public class DadosDemoInitializer {

    private static final String RESPONSAVEL_DEMO = "demo";
    private static final int PEDIDOS_HISTORICO = 70;
    private static final int DIAS_DE_HISTORICO = 30;

    private static final int[] HORAS = {11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23};
    private static final int[] PESOS = {4, 9, 8, 3, 2, 2, 3, 9, 13, 13, 10, 6, 2};

    private static final double[] DESCONTOS = {5.0, 10.0, 10.0, 15.0, 20.0};
    private static final String[] MOTIVOS = {
            "Cliente desistiu do pedido",
            "Falta de ingrediente",
            "Pedido duplicado",
            "Endereço fora da área de entrega"
    };

    @Value("${app.dados-demo.recriar:false}")
    private boolean recriar;

    @Value("${app.dados-demo.remover:false}")
    private boolean remover;

    @Bean
    @Order(2)
    CommandLineRunner gerarDadosDemo(PedidoRepository pedidoRepository,
                                     ProdutoRepository produtoRepository,
                                     ClienteRepository clienteRepository) {
        return args -> {
            if (remover) {
                long apagados = pedidoRepository.deleteByResponsavel(RESPONSAVEL_DEMO);
                System.out.println("Dados de demonstração: " + apagados + " pedido(s) removido(s).");
                return;
            }

            if (recriar) {
                long apagados = pedidoRepository.deleteByResponsavel(RESPONSAVEL_DEMO);
                System.out.println("Dados de demonstração: " + apagados + " pedido(s) antigo(s) removido(s).");
            } else if (pedidoRepository.existsByResponsavel(RESPONSAVEL_DEMO)) {
                System.out.println("Dados de demonstração já existem. Nada a fazer "
                        + "(use app.dados-demo.recriar=true para gerar de novo).");
                return;
            }

            List<Produto> produtos = produtoRepository.findAll().stream()
                    .filter(p -> Boolean.TRUE.equals(p.getAtivo()) && p.getPreco() != null)
                    .toList();
            if (produtos.size() < 3) {
                System.out.println("Dados de demonstração: são necessários pelo menos 3 produtos ativos. Nada foi gerado.");
                return;
            }

            List<Cliente> clientes = clienteRepository.findAll();
            if (clientes.isEmpty()) {
                System.out.println("Dados de demonstração: cadastre pelo menos 1 cliente. Nada foi gerado.");
                return;
            }

            Random random = new Random(2026);
            LocalDateTime agora = LocalDateTime.now();
            List<Pedido> pedidos = new ArrayList<>();

            for (int i = 0; i < PEDIDOS_HISTORICO; i++) {
                LocalDateTime entrada = sortearEntrada(random, agora);
                StatusPedido status = random.nextDouble() < 0.88 ? StatusPedido.FINALIZADO : StatusPedido.CANCELADO;
                pedidos.add(montarPedido(random, entrada, produtos, clientes, status));
            }

            StatusPedido[] andamento = {
                    StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.EM_PREPARACAO,
                    StatusPedido.PRONTO, StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO
            };
            for (int i = 0; i < andamento.length; i++) {
                LocalDateTime entrada = agora.minusMinutes(10 + i * 12L + random.nextInt(8));
                pedidos.add(montarPedido(random, entrada, produtos, clientes, andamento[i]));
            }

            pedidoRepository.saveAll(pedidos);
            System.out.println("Dados de demonstração: " + pedidos.size() + " pedidos criados.");
        };
    }

    private LocalDateTime sortearEntrada(Random random, LocalDateTime agora) {
        while (true) {
            LocalDate dia = agora.toLocalDate().minusDays(random.nextInt(DIAS_DE_HISTORICO));

            DayOfWeek diaDaSemana = dia.getDayOfWeek();
            boolean fimDeSemana = diaDaSemana == DayOfWeek.FRIDAY
                    || diaDaSemana == DayOfWeek.SATURDAY
                    || diaDaSemana == DayOfWeek.SUNDAY;
            if (!fimDeSemana && random.nextDouble() < 0.45) {
                continue;
            }

            LocalDateTime entrada = dia.atTime(sortearHora(random), random.nextInt(60));
            if (entrada.isBefore(agora.minusHours(3))) {
                return entrada;
            }
        }
    }

    private int sortearHora(Random random) {
        int sorteio = random.nextInt(Arrays.stream(PESOS).sum());
        for (int i = 0; i < HORAS.length; i++) {
            sorteio -= PESOS[i];
            if (sorteio < 0) {
                return HORAS[i];
            }
        }
        return 20;
    }

    private Pedido montarPedido(Random random, LocalDateTime entrada, List<Produto> produtos,
                                List<Cliente> clientes, StatusPedido status) {
        Pedido pedido = new Pedido();
        pedido.setClienteId(clientes.get(random.nextInt(clientes.size())).getId());

        Set<Integer> escolhidos = new HashSet<>();
        int quantidadeDeItens = Math.min(1 + random.nextInt(3), produtos.size());
        while (escolhidos.size() < quantidadeDeItens) {
            int indice = (int) (Math.pow(random.nextDouble(), 2.0) * produtos.size());
            if (escolhidos.add(indice)) {
                Produto produto = produtos.get(indice);
                ItemPedido item = new ItemPedido();
                item.setProdutoId(produto.getId());
                item.setNomeProduto(produto.getNome());
                item.setPrecoUnitario(produto.getPreco());
                item.setQuantidade(1 + random.nextInt(3));
                pedido.getItens().add(item);
            }
        }

        pedido.setStatus(status);
        pedido.setResponsavel(RESPONSAVEL_DEMO);
        pedido.setDataEntrada(entrada);

        double desconto = random.nextDouble() < 0.15 ? DESCONTOS[random.nextInt(DESCONTOS.length)] : 0.0;
        pedido.setDescontoPercentual(desconto);
        pedido.recalcularTotal();

        if (status.registraSaida() || status == StatusPedido.FINALIZADO) {
            pedido.setDataSaida(entrada.plusMinutes(15 + random.nextInt(31)));
            pedido.setResponsavelSaida(RESPONSAVEL_DEMO);
        }

        if (status == StatusPedido.CANCELADO) {
            pedido.setJustificativaCancelamento(MOTIVOS[random.nextInt(MOTIVOS.length)]);
            pedido.setDataCancelamento(entrada.plusMinutes(5 + random.nextInt(16)));
            pedido.setResponsavelCancelamento(RESPONSAVEL_DEMO);
        }

        return pedido;
    }
}