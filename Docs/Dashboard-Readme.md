## Dashboard Administrativo

Painel para o responsável pelo estabelecimento acompanhar o desempenho das vendas, a situação dos pedidos e as tendências da operação. É composto por uma **API de leitura** (`/api/dashboard/*`) e por uma **tela** (`/dashboard`) que consome essa API.

- **Acesso:** perfis `ADMIN` e `GERENTE`. O `ATENDENTE` recebe `403`; sem login, o sistema redireciona para `/login`.
- **Menu:** item **Dashboard** na barra lateral (visível apenas para ADMIN e GERENTE).
- **Somente leitura:** apenas `GET`. Qualquer outro método em `/dashboard/**` ou `/api/dashboard/**` é negado a todos os perfis.

### O que a tela mostra

| Requisito | Recurso |
|-----------|---------|
| Indicadores gerais de vendas | Faturamento, pedidos vendidos, ticket médio, itens vendidos, descontos concedidos, pedidos cancelados (e taxa de cancelamento) e tempo médio até a saída |
| Análise de vendas (gráficos) | Vendas por período (por dia ou por mês), vendas por tipo de produto e vendas por horário (mostra os picos) |
| Situação dos pedidos | Gráfico de pedidos por status e lista de **pedidos em andamento**, com cliente, valor e há quantos minutos estão abertos |
| Produtos mais vendidos | Ranking Top 5 ou Top 10, ordenado por quantidade ou por faturamento |
| Filtros de consulta | Período (atalhos *Hoje*, *7 dias*, *30 dias*, *Este mês* ou datas livres), status e tipo de produto |
| Integração com a API | Todos os dados vêm de `fetch` aos endpoints abaixo; a tela não calcula nada de negócio |
| Atualização e tratamento de dados | Atualização automática a cada 60 s (pode ser desligada), botão *Atualizar*, indicador de carregamento, horário da última atualização, mensagem de erro com *Tentar novamente* e avisos de "sem dados" |

### Regras de negócio dos indicadores

- **Faturamento:** soma dos pedidos **não cancelados**, já com o desconto aplicado (preço × quantidade × (1 − desconto)).
- **Período:** filtrado pela **data de entrada** do pedido. Sem datas, usa os **últimos 30 dias**; se só a data final for informada, o início fica 30 dias antes. Máximo de **366 dias**.
- **Filtro de status:** considera apenas pedidos com o status escolhido.
- **Filtro de tipo** (Pizza, Bebida, Esfiha...): só os **itens daquele tipo** entram na conta. Itens de produtos que saíram do cadastro aparecem como **Outros**.
- **Ticket médio:** faturamento ÷ pedidos vendidos. **Taxa de cancelamento:** cancelados ÷ total de pedidos do filtro.
- **Tempo médio de saída:** média entre a entrada e a saída (entrega/retirada) dos pedidos vendidos que têm saída registrada; vem vazio quando não há nenhum.
- **Tratamento de dados:** pedidos sem status, sem data ou itens sem preço não quebram o painel. Períodos sem pedidos devolvem zeros (sem divisão por zero). Dias e meses sem vendas aparecem zerados nos gráficos, para a linha do tempo ficar contínua.

### Endpoints

Todos são `GET`, retornam JSON e exigem sessão autenticada como ADMIN ou GERENTE.

**Parâmetros de filtro** (opcionais, aceitos pelos endpoints marcados com ✔):

| Parâmetro | Formato | Descrição |
|-----------|---------|-----------|
| `inicio` | `AAAA-MM-DD` | Primeiro dia do período |
| `fim` | `AAAA-MM-DD` | Último dia do período |
| `status` | nome do status, ex.: `FINALIZADO` | Filtra por status do pedido |
| `tipo` | texto, ex.: `Pizza` | Filtra por tipo de produto (ignora maiúsculas/minúsculas) |

| Endpoint | Filtros | Parâmetros extras | Retorno |
|----------|:-------:|-------------------|---------|
| `/api/dashboard/resumo` | ✔ | — | Indicadores gerais (objeto) |
| `/api/dashboard/vendas-por-periodo` | ✔ | `agrupamento` = `dia` (padrão) ou `mes` | Lista `{periodo, pedidos, faturamento}` |
| `/api/dashboard/vendas-por-tipo` | ✔ | — | Lista `{tipo, quantidade, faturamento}` |
| `/api/dashboard/vendas-por-horario` | ✔ | — | 24 faixas `{hora, pedidos, faturamento}` |
| `/api/dashboard/pedidos-por-status` | ✔ | — | Todos os status, inclusive com zero: `{status, descricao, quantidade}` |
| `/api/dashboard/pedidos-em-andamento` | — | — | Pedidos abertos (até 50), do mais antigo ao mais novo |
| `/api/dashboard/produtos-mais-vendidos` | ✔ | `limite` (1 a 20, padrão 5); `ordenarPor` = `quantidade` (padrão) ou `faturamento` | Lista `{posicao, produtoId, nome, tipo, quantidade, faturamento}` |
| `/api/dashboard/filtros` | — | — | Tipos de produto e status disponíveis, para montar os filtros |

**Exemplo**

```http
GET /api/dashboard/resumo?inicio=2026-10-01&fim=2026-10-31&tipo=Pizza
```

```json
{
  "inicio": "2026-10-01",
  "fim": "2026-10-31",
  "totalPedidos": 62,
  "pedidosVendidos": 55,
  "pedidosCancelados": 7,
  "taxaCancelamento": 11.29,
  "faturamento": 4821.50,
  "ticketMedio": 87.66,
  "itensVendidos": 118,
  "descontoTotal": 213.40,
  "tempoMedioSaidaMinutos": 41.5
}
```

> Os valores acima são ilustrativos; o formato dos campos segue o `DashboardDTO`.

**Erros**

| Código | Quando | Corpo |
|--------|--------|-------|
| `400` | Data fora do formato, início depois do fim, período acima de 366 dias, status inexistente, `agrupamento`/`ordenarPor` inválidos, `limite` não numérico ou fora de 1–20 | `{"erro": "mensagem explicando o problema"}` |
| `403` | Usuário sem permissão (ATENDENTE) | — |
| `302` → `/login` | Sem autenticação | — |

### Dados de demonstração (opcional)

> **Esta etapa é totalmente opcional.** O dashboard funciona normalmente com os pedidos reais do sistema. Os dados de demonstração servem apenas para **ver os gráficos preenchidos** quando o banco ainda tem poucos pedidos (por exemplo, para apresentações).

Por padrão **nada é gerado**. Para ativar, adicione ao arquivo `.env`:

```properties
app.dados-demo.ativar=true
```

Ao iniciar a aplicação, são criados cerca de **70 pedidos finalizados/cancelados nos últimos 30 dias**, com picos nos horários de almoço e jantar, alguns descontos, e **6 pedidos em andamento** (para a lista da tela). Os pedidos de demonstração são identificados pelo responsável `demo`.

Pré-requisitos: ao menos **1 cliente cadastrado** e **3 produtos ativos**; caso contrário, nada é gerado e o motivo aparece no console.

| Propriedade | Efeito |
|-------------|--------|
| `app.dados-demo.ativar=true` | Gera os dados na inicialização (só uma vez: se já existirem, nada é feito) |
| `app.dados-demo.recriar=true` | Apaga os pedidos de demonstração e gera de novo (use junto com `ativar`) |
| `app.dados-demo.remover=true` | Apaga apenas os pedidos de demonstração e não gera nada (use junto com `ativar`) |

Pedidos reais **nunca** são apagados: a remoção atinge somente os pedidos com responsável `demo`. Depois de usar `recriar` ou `remover`, volte a propriedade para `false` (ou apague a linha) para que não se repita a cada inicialização.

### Testes

Executar: `./mvnw test`. Os testes não usam banco de dados.

- `DashboardServiceTest`: cálculo dos indicadores, filtros por status e por tipo (sem diferenciar maiúsculas), período padrão de 30 dias, períodos vazios, dados incompletos, validações (datas, período máximo, status, agrupamento, limite, ordenação), vendas por dia e por mês, por tipo, por horário, pedidos por status, pedidos em andamento e ranking.
- `DashboardControllerTest`: formato do JSON, repasse dos filtros ao serviço e erro de validação convertido em `400` com a mensagem no campo `erro`.
- `AutorizacaoPorPerfilTest`: regras de acesso do dashboard por perfil.
