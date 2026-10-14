# PediuPatrão

Sistema web de gestão de pedidos para pizzaria.

- **Autor:** Rafael Fernandes Barbosa

O projeto parte de um código base (cadastro de clientes, produtos e pedidos simples, com login) e o evolui com **controle de acesso por três perfis, pedidos com itens e fluxo de status, cancelamento e desconto exclusivos do gerente, e auditoria persistente de pedidos e clientes**.

## Tecnologias

- Java 21
- Spring Boot 3.4.4 (Web, Data MongoDB, Security, Thymeleaf)
- MongoDB (Atlas)
- Bootstrap 5 e Bootstrap Icons
- JUnit 5, Mockito e Spring Security Test

## Como executar

### Pré-requisitos

- JDK 21
- Um banco MongoDB acessível (foi usado o MongoDB Atlas). Se for o Atlas, o IP da máquina precisa estar liberado em *Network Access*.

### Passo a passo

1. Clone o repositório.
2. Na raiz do projeto, copie o arquivo `.env.example` para `.env` e preencha com a URI do seu MongoDB:

   ```properties
   spring.data.mongodb.uri=mongodb+srv://USUARIO:SENHA@SEU-CLUSTER.mongodb.net/?appName=Cluster0
   ```

   O `.env` está no `.gitignore` e **não deve ser versionado**. Se a senha tiver caracteres especiais (`@`, `#`, `/`), ela precisa estar codificada na URI.

3. Execute a aplicação:

   ```bash
   ./mvnw spring-boot:run        # Linux/macOS
   mvnw.cmd spring-boot:run      # Windows
   ```

4. Acesse **http://localhost:8080**. Sem login, o sistema redireciona para `/login`.

Na primeira execução, o sistema cria automaticamente os usuários de demonstração e alguns produtos de exemplo (somente se a coleção `produtos` estiver vazia).

### Usuários de demonstração

| Usuário     | Senha      | Perfil    |
|-------------|------------|-----------|
| `admin`     | `teste123` | ADMIN     |
| `gerente`   | `teste123` | GERENTE   |
| `atendente` | `teste123` | ATENDENTE |

> Essas credenciais existem apenas para demonstração. Se o usuário `admin` já existia no banco sem perfil (dados do projeto base), o sistema corrige o perfil para ADMIN e **mantém a senha que ele já tinha**.

### Executar os testes

```bash
./mvnw test
```

Os testes não usam banco de dados nem exigem o arquivo `.env`.

## Perfis e permissões

| Operação                                   | ADMIN | GERENTE | ATENDENTE |
|--------------------------------------------|:-----:|:-------:|:---------:|
| Administrar usuários e atribuir perfis     | Sim   | Não     | Não       |
| Consultar pedidos                          | Sim   | Sim     | Sim       |
| Registrar pedidos                          | Não   | Sim     | Sim       |
| Avançar o status de um pedido              | Não   | Sim     | Sim       |
| Aplicar desconto (até 20%)                 | Não   | Sim     | Não       |
| Cancelar pedido (com justificativa)        | Não   | Sim     | Não       |
| Consultar clientes                         | Não   | Sim     | Sim       |
| Cadastrar e atualizar clientes             | Não   | Não     | Sim       |
| Excluir clientes                           | Não   | Sim     | Não       |
| Consultar produtos                         | Sim   | Sim     | Sim       |
| Alterar produtos (API)                     | Não   | Sim     | Não       |
| Consultar a auditoria                      | Sim   | Sim     | Não       |

As permissões são aplicadas **no backend**, em duas camadas: regras de URL no `SecurityConfig` e `@PreAuthorize` nos serviços. Esconder botões na interface é apenas um recurso visual.

## Requisitos implementados

### RF01 — Controle de usuários e permissões

- Três perfis: `ADMIN`, `GERENTE` e `ATENDENTE`. O `UsuarioDetailsService` agora lê o perfil gravado no banco (antes todo usuário autenticado recebia `USER`). Usuários antigos sem perfil válido recebem o menor privilégio (`ATENDENTE`).
- Rotas de tela e de API protegidas por perfil no `SecurityConfig`. A rota `/api/usuarios`, que antes era pública, passou a ser exclusiva do ADMIN.
- Proteção contra autoelevação de privilégios:
    - somente o ADMIN cria, edita e exclui usuários;
    - o perfil é validado (apenas os três valores válidos) e o nome de usuário é único;
    - um `id` enviado na criação é ignorado, para impedir a sobrescrita de outro usuário;
    - ninguém altera o próprio perfil nem exclui a própria conta;
    - a senha é aceita na entrada, mas **nunca devolvida** nas respostas da API.
- CSRF reativado. Os formulários Thymeleaf recebem o token automaticamente, e as chamadas `fetch` das telas enviam o token pelo cabeçalho. O logout é feito por formulário `POST`.

### RF02 — Pedido com itens

- O `Pedido` passou a ter uma lista de `ItemPedido` (produto, nome e preço copiados no momento do pedido, e quantidade), além de responsável, data/hora de entrada, desconto e valor total.
- A API de criação aceita apenas `clienteId` e a lista de `{produtoId, quantidade}` (`PedidoRequest`). Preço, total, status, responsável e data são definidos **pelo servidor**. Campos extras enviados na requisição são ignorados.
- Validações: ao menos um item, quantidade entre 1 e 99, produto existente e ativo, cliente existente.
- Nova tela `/pedidos/novo`, com seleção de cliente e linhas de itens.

### RF03 — Fluxo de status, entrada e saída

Status (enum `StatusPedido`) e transições permitidas:

```
RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA → FINALIZADO
                                  ↘ RETIRADO ─────────↗
```

- O enum conhece os próximos passos válidos de cada status. Pular etapas, voltar atrás ou alterar um pedido cancelado ou finalizado resulta em erro **409** com mensagem explicativa. `CANCELADO` não é alcançável por esse fluxo.
- Entrada: responsável e data/hora são gravados na criação. Saída: ao passar para `SAIU_PARA_ENTREGA` ou `RETIRADO`, o servidor grava a data/hora e o responsável.
- A tela de pedidos mostra apenas os botões dos próximos passos válidos de cada pedido.

### RF04 — Cancelamento e desconto (exclusivos do GERENTE)

- **Cancelamento:** exige justificativa e só é permitido enquanto o pedido está em `RECEBIDO`, `EM_PREPARACAO` ou `PRONTO`. O pedido **não é apagado**: continua no banco com status `CANCELADO`, justificativa, data e responsável. Depois disso, nenhuma alteração é aceita.
- **Desconto:** percentual de 0% a 20%, apenas antes da saída do pedido. O novo percentual **substitui** o anterior (não acumula), então o limite de 20% nunca é ultrapassado. O total é recalculado no servidor a partir dos itens.
- Duas camadas de bloqueio: regra de URL (`/api/pedidos/*/cancelar` e `/desconto`) e `@PreAuthorize("hasRole('GERENTE')")` no serviço.

### RF05 — Auditoria

Entidade, repositório e serviço próprios (`RegistroAuditoria`, `AuditoriaRepository`, `AuditoriaService`), com a coleção `auditoria` no MongoDB.

Cada registro contém: usuário autenticado e perfil, data/hora, tipo de operação, entidade e identificador, lista de alterações (campo, valor anterior e valor novo) e justificativa, quando a regra exige.

| Operação auditada      | Campos registrados                                                       |
|------------------------|--------------------------------------------------------------------------|
| Inclusão de pedido     | cliente, itens, status, desconto, valor total                            |
| Alteração de status    | status e, nas saídas, data/hora e responsável da saída                   |
| Desconto               | percentual de desconto e valor total                                     |
| Cancelamento           | status (com a justificativa informada)                                   |
| Inclusão de cliente    | apenas o identificador                                                   |
| Alteração de cliente   | somente os campos que mudaram (nome, telefone e endereço)                |
| Exclusão de cliente    | apenas o identificador                                                   |

- **Consulta restrita:** tela `/auditoria` e `GET /api/auditoria`, somente para ADMIN e GERENTE, com filtro por tipo de entidade.
- **Integridade:** o `AuditoriaRepository` só expõe gravar e consultar (não há método de exclusão ou atualização), o controller só tem `GET`, e qualquer outro método HTTP nessas rotas é negado a todos os perfis (`denyAll`).
- **Proteção de dados:** senhas, tokens e credenciais nunca entram nos registros. Em clientes, só o campo alterado é gravado (não uma cópia do cadastro), e inclusão e exclusão guardam apenas o identificador. Alterações sem mudança real de valor não geram registro.

## API REST

| Método e rota                         | Perfis autorizados       | Descrição                         |
|---------------------------------------|--------------------------|-----------------------------------|
| `GET /api/pedidos`                    | ADMIN, GERENTE, ATENDENTE | Lista pedidos                    |
| `POST /api/pedidos`                   | GERENTE, ATENDENTE       | Registra pedido                   |
| `PUT /api/pedidos/{id}/status`        | GERENTE, ATENDENTE       | Avança o status (`?status=`)      |
| `PUT /api/pedidos/{id}/desconto`      | GERENTE                  | Aplica desconto (`{percentual}`)  |
| `PUT /api/pedidos/{id}/cancelar`      | GERENTE                  | Cancela (`{justificativa}`)       |
| `GET /api/clientes`                   | GERENTE, ATENDENTE       | Lista clientes                    |
| `POST` e `PUT /api/clientes`          | ATENDENTE                | Cadastra e atualiza clientes      |
| `DELETE /api/clientes/{id}`           | GERENTE                  | Exclui cliente                    |
| `GET /api/produtos`                   | ADMIN, GERENTE, ATENDENTE | Lista produtos                   |
| `POST`, `PUT`, `DELETE /api/produtos` | GERENTE                  | Altera produtos                   |
| `/api/usuarios` (todos os métodos)    | ADMIN                    | Administra usuários               |
| `GET /api/auditoria`                  | ADMIN, GERENTE           | Consulta a auditoria              |

Códigos de erro usados: `400` (dados inválidos), `403` (sem permissão ou sem token CSRF), `404` (não encontrado) e `409` (operação incompatível com o estado atual do pedido). As respostas de erro trazem o campo `erro` com a mensagem.

> Como o CSRF está ativo, requisições `POST`, `PUT` e `DELETE` feitas fora do navegador precisam enviar o token CSRF da sessão.

## Principais alterações em relação ao projeto base

| Lacuna do projeto base                                          | O que foi feito                                                                                      |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------|
| Perfis ADMIN e USER; autenticação atribuía `USER` a todos       | Três perfis reais, `UsuarioDetailsService` lendo o perfil do banco, regras por rota, CSRF reativado  |
| `/api/usuarios` pública; qualquer pessoa criava usuário         | Rotas de usuário exclusivas do ADMIN, validações e proteção contra autoelevação                      |
| Pedido com uma única pizza, status em texto livre               | `Pedido` com itens, total calculado no servidor, `StatusPedido` (enum) com transições validadas      |
| Sem cancelamento, desconto ou registro de saída                 | Cancelamento com justificativa, desconto limitado a 20%, saída com data/hora e responsável           |
| Sem auditoria                                                   | Auditoria persistente de pedidos e clientes, com consulta restrita e registros imutáveis             |
| Sem testes                                                      | Testes de regras de negócio e testes de segurança por perfil                                         |

Arquivos novos e alterados, por pacote (`com.umc.pediupatrao`):

- `config`: `SecurityConfig` (regras por perfil, CSRF, `@EnableMethodSecurity`) e `DataInitializer` (usuários e produtos de demonstração)
- `entity`: `Usuario`, `Pedido`, `ItemPedido`, `RegistroAuditoria`, `AlteracaoCampo`
- `enums`: `StatusPedido`, `TipoOperacao`
- `dto`: `PedidoRequest`, `CancelamentoRequest`, `DescontoRequest`
- `exception`: `EstadoInvalidoException`, `RecursoNaoEncontradoException`
- `repository`: `AuditoriaRepository`
- `service`: `UsuarioDetailsService`, `UsuarioService`, `PedidoService`, `ClienteService`, `AuditoriaService`
- `controller`: `PedidoController`, `UsuarioController`, `AuditoriaController`, `HomeController`, `ApiExceptionHandler`
- Templates: `pedidos/lista.html`, `pedidos/form.html`, `auditoria/lista.html`, `fragments/sidebar.html` (itens por perfil e botão Sair), `login.html` (botão de mostrar senha), `layout.html` (token CSRF e modais padronizados)

## Testes automatizados

Em `src/test/java`:

- `PedidoServiceTest`: criação com total calculado no servidor, transições válidas e inválidas, registro de saída, limites do desconto, cancelamento com justificativa e bloqueios depois da saída.
- `UsuarioServiceTest` e `UsuarioDetailsServiceTest`: perfis atribuídos no login, perfil inválido, nome duplicado, bloqueio de alteração do próprio perfil e de exclusão da própria conta.
- `AuditoriaServiceTest` e `ClienteServiceTest`: registro apenas dos campos alterados, usuário e perfil identificados, justificativa, inclusão e exclusão sem dados pessoais, e geração dos registros nas operações de cliente.
- `AutorizacaoPorPerfilTest`: sobe a camada web com a `SecurityConfig` real e verifica, por perfil, quais rotas retornam `403`, quais são autorizadas, o redirecionamento para o login sem autenticação, a recusa de operações sem token CSRF e a impossibilidade de alterar ou excluir registros de auditoria.