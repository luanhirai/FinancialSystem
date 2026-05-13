# Importacao de Produtos Tiny com Mock

Esta documentacao explica como funciona a importacao mockada de produtos do Tiny dentro do backend. A ideia e simular uma resposta da API do Tiny usando um arquivo local (`mock.md`), permitindo testar o fluxo de importacao sem depender da integracao real.

## Objetivo

O fluxo importa produtos de um arquivo mockado e salva esses produtos no banco de dados do sistema, vinculando cada produto ao ecommerce correspondente e ao usuario autenticado.

Esse recurso e util para:

- testar a importacao antes de conectar com a API real do Tiny;
- validar o cadastro ou atualizacao de produtos;
- popular o banco com dados consistentes para desenvolvimento;
- simular produtos vindos de diferentes marketplaces, como Mercado Livre, Shopee, Amazon e site proprio.

## Arquivos envolvidos

| Arquivo | Responsabilidade |
| --- | --- |
| `mock.md` | Contem a resposta mockada no formato JSON dentro de um bloco Markdown. |
| `ProdutosMockController.java` | Expoe o endpoint HTTP usado para disparar a importacao. |
| `TinyProductImportService.java` | Le o mock, converte o JSON em objetos Java e persiste os produtos. |
| `ProductRepository.java` | Busca produtos ja importados para evitar duplicacao por usuario. |
| `EcommerceRepository.java` | Busca ou cria o ecommerce associado ao produto. |

## Endpoint

A importacao e iniciada pelo endpoint:

```http
POST /produtos/importar-tiny
```

O controller recebe a requisicao e chama:

```java
tinyProductImportService.ImportacaoTiny();
```

O retorno e uma lista de produtos importados ou atualizados.

## Fluxo da importacao

1. O usuario autenticado e recuperado pelo `AuthenticatedUserService`.
2. O arquivo `mock.md` e lido a partir do caminho configurado em `CAMINHO_MOCK`.
3. O conteudo Markdown e processado para extrair apenas o JSON.
4. O JSON e convertido para records Java usando `ObjectMapper`.
5. Para cada produto recebido:
   - o ecommerce do produto e buscado pelo nome;
   - se o ecommerce nao existir para o usuario, ele e criado;
   - o produto e buscado por `id_olist` e `userId`;
   - se ja existir, seus dados sao atualizados;
   - se nao existir, um novo produto e criado;
   - o produto e salvo no banco.

## Leitura do arquivo mock

O caminho do mock esta definido em:

```java
private static final Path CAMINHO_MOCK = Path.of("mock.md");
```

Isso significa que a aplicacao espera encontrar o arquivo `mock.md` no diretorio em que o backend estiver sendo executado.

O metodo responsavel pela leitura e:

```java
private TinyProdutosResponse lerProdutosTiny()
```

Ele executa tres passos principais:

1. le o arquivo com `Files.readString(CAMINHO_MOCK)`;
2. extrai o JSON do Markdown com `extrairJsonDoMarkdown(markdown)`;
3. converte o JSON para `TinyProdutosResponse` usando Jackson.

## Formato esperado do mock

O arquivo `mock.md` pode conter o JSON diretamente ou dentro de um bloco Markdown:

````markdown
```json
{
  "itens": [],
  "paginacao": {
    "pagina": 1,
    "limite": 100,
    "total": 0,
    "totalPaginas": 1
  }
}
```
````

A estrutura principal esperada e:

```json
{
  "itens": [
    {
      "id": 101,
      "sku": "CAM-BASIC-P",
      "descricao": "Camiseta Basic Preta",
      "tipo": "S",
      "situacao": "A",
      "unidade": "UN",
      "precos": {
        "preco": 75.00,
        "precoPromocional": 75.00,
        "precoCusto": 32.00,
        "precoCustoMedio": 32.00
      },
      "estoque": {
        "controlar": true,
        "localizacao": "A1",
        "quantidade": 40
      },
      "ecommerce": {
        "id": 55,
        "nome": "Mercado Livre"
      }
    }
  ],
  "paginacao": {
    "pagina": 1,
    "limite": 100,
    "total": 1,
    "totalPaginas": 1
  }
}
```

## Mapeamento dos dados

Durante a importacao, os campos do Tiny sao convertidos para a entidade `Product`:

| Campo no mock | Campo no produto |
| --- | --- |
| `id` | `id_olist` |
| `descricao` | `name` |
| `precos.preco` | `original_price` |
| `precos.precoCusto` | `cost` |
| `estoque.quantidade` | `quantity` |
| `ecommerce.nome` | ecommerce vinculado ao produto |

O `sku`, `tipo`, `situacao`, `unidade`, `precoPromocional`, `precoCustoMedio`, `controlar` e `localizacao` sao lidos pelo record, mas atualmente nao sao persistidos na entidade `Product`.

## Regra de ecommerce

Antes de salvar o produto, o sistema procura um ecommerce do usuario logado com o mesmo nome recebido no mock:

```java
ecommerce.getName().equalsIgnoreCase(nome)
```

Se encontrar, reutiliza esse ecommerce.

Se nao encontrar, cria um novo ecommerce com:

```java
rate = 0F
fixed_rate = 0F
user = usuarioLogado
```

## Regra para evitar duplicacao de produtos

O produto e buscado por:

```java
findByIdOlistAndUserId(String.valueOf(produtoTiny.id()), user.getId())
```

Isso evita que o mesmo produto do Tiny seja duplicado para o mesmo usuario.

Quando o produto ja existe, ele e atualizado com os dados atuais do mock. Quando nao existe, um novo registro e criado.

## Tratamento de erros

Se o arquivo `mock.md` nao puder ser lido, o sistema lanca:

```java
UncheckedIOException
```

com a mensagem:

```text
Nao foi possivel ler o mock de produtos do Tiny em mock.md
```

Se o Markdown tiver um bloco `json` aberto incorretamente, o sistema lanca:

```java
IllegalArgumentException
```

com a mensagem:

```text
O arquivo mock.md possui um bloco JSON invalido
```

## Como testar manualmente

1. Garanta que o backend esta rodando.
2. Garanta que existe um usuario autenticado.
3. Garanta que o arquivo `mock.md` esta no diretorio esperado pelo backend.
4. Envie uma requisicao:

```http
POST /produtos/importar-tiny
Authorization: Bearer <token>
```

5. Confira se os produtos foram criados ou atualizados no banco.

## Pontos de atencao

- O arquivo `mock.md` precisa conter um JSON valido.
- A chave principal deve se chamar `itens`.
- Cada item precisa ter `id`, `descricao`, `precos`, `estoque` e `ecommerce`.
- O ecommerce e comparado pelo nome, ignorando maiusculas e minusculas.
- O produto e identificado pelo `id` recebido do Tiny convertido para `String`.
- A importacao roda dentro de uma transacao por causa da anotacao `@Transactional`.

## Evolucao futura

Quando a API real do Tiny for integrada, esse fluxo pode ser reaproveitado. A principal mudanca sera trocar a origem dos dados:

- hoje: `mock.md`;
- futuro: chamada HTTP para a API do Tiny.

O restante da logica, como converter a resposta, buscar ou criar ecommerce e salvar produtos, pode continuar centralizado no service.
