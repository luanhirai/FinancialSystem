{
"data": {
"id": 987654321,
"numero": "PV-2025-004782",
"numeroPedidoExterno": "MKTPL-B2C-00412",
"situacao": "aprovado",
"dataPedido": "2025-05-06T14:32:10-03:00",
"dataPrevisao": "2025-05-10",
"dataSaida": null,
"observacoes": "Cliente solicitou embalagem para presente. Entregar no período da manhã.",
"observacoesInternas": "Verificar estoque do item SKU-7823 antes de faturar.",

    "cliente": {
      "id": 112233,
      "codigo": "CLI-00891",
      "nome": "Maria Fernanda Oliveira",
      "tipoPessoa": "F",
      "cpfCnpj": "345.678.901-23",
      "inscricaoEstadual": null,
      "email": "mariafernanda.oliveira@email.com.br",
      "telefone": "(11) 98765-4321",
      "celular": "(11) 91234-5678",
      "endereco": "Rua das Orquídeas, 542",
      "bairro": "Jardim Primavera",
      "cidade": "São Paulo",
      "uf": "SP",
      "cep": "04567-890",
      "pais": "Brasil"
    },

    "enderecoEntrega": {
      "nome": "Maria Fernanda Oliveira",
      "endereco": "Rua das Orquídeas, 542",
      "numero": "542",
      "complemento": "Apto 31",
      "bairro": "Jardim Primavera",
      "cidade": "São Paulo",
      "uf": "SP",
      "cep": "04567-890",
      "pais": "Brasil"
    },

    "itens": [
      {
        "id": 10001,
        "sequencia": 1,
        "produto": {
          "id": 55001,
          "codigo": "SKU-7823",
          "nome": "Tênis Esportivo Runner Pro - Preto - Nº 38",
          "unidade": "UN",
          "gtin": "7891234567890",
          "ncm": "6404.11.00"
        },
        "quantidade": 2,
        "valorUnitario": 349.90,
        "valorDesconto": 34.99,
        "valorAcrescimo": 0.00,
        "valorTotal": 664.81,
        "aliquotaIpi": 0.00,
        "aliquotaIcms": 12.00,
        "cfop": "6.102",
        "origemProduto": "0"
      },
      {
        "id": 10002,
        "sequencia": 2,
        "produto": {
          "id": 55002,
          "codigo": "SKU-3341",
          "nome": "Meia Esportiva Cano Alto Dry-Fit - Kit com 3 pares",
          "unidade": "KT",
          "gtin": "7890987654321",
          "ncm": "6115.95.00"
        },
        "quantidade": 1,
        "valorUnitario": 59.90,
        "valorDesconto": 0.00,
        "valorAcrescimo": 0.00,
        "valorTotal": 59.90,
        "aliquotaIpi": 0.00,
        "aliquotaIcms": 12.00,
        "cfop": "6.102",
        "origemProduto": "0"
      },
      {
        "id": 10003,
        "sequencia": 3,
        "produto": {
          "id": 55003,
          "codigo": "SKU-1100",
          "nome": "Garrafa Térmica Inox 750ml - Azul",
          "unidade": "UN",
          "gtin": "7899876543210",
          "ncm": "9617.00.00"
        },
        "quantidade": 1,
        "valorUnitario": 89.90,
        "valorDesconto": 9.00,
        "valorAcrescimo": 0.00,
        "valorTotal": 80.90,
        "aliquotaIpi": 0.00,
        "aliquotaIcms": 12.00,
        "cfop": "6.102",
        "origemProduto": "0"
      }
    ],

    "totais": {
      "subtotalItens": 499.70,
      "totalDesconto": 43.99,
      "totalAcrescimo": 0.00,
      "totalFrete": 18.90,
      "totalIpi": 0.00,
      "totalImpostos": 0.00,
      "totalPedido": 805.61
    },

    "formasPagamento": [
      {
        "id": 201,
        "formaPagamento": {
          "id": 3,
          "nome": "Cartão de Crédito"
        },
        "parcelas": 3,
        "valor": 805.61,
        "valorParcela": 268.54,
        "dataVencimento": "2025-06-06"
      }
    ],

    "frete": {
      "modalidade": "CIF",
      "valorFrete": 18.90,
      "prazoEntrega": 4,
      "transportadora": {
        "id": 77,
        "nome": "Transportadora Expressa SP",
        "cnpj": "11.222.333/0001-44"
      },
      "codigoRastreio": "BR789456123SP",
      "servico": "SEDEX"
    },

    "vendedor": {
      "id": 8,
      "nome": "Carlos Eduardo Mendes",
      "email": "carlos.mendes@loja.com.br"
    },

    "intermediador": {
      "id": 5,
      "nome": "Mercado Livre",
      "cnpj": "33.168.308/0001-52"
    },

    "ecommerce": {
      "numeroPedido": "MKTPL-B2C-00412",
      "canal": "Mercado Livre",
      "marketplace": true
    },

    "tags": ["marketplace", "presente", "frete-gratis-nao"],

    "historico": [
      {
        "data": "2025-05-06T14:32:10-03:00",
        "situacao": "aberto",
        "descricao": "Pedido criado via integração Mercado Livre"
      },
      {
        "data": "2025-05-06T15:10:44-03:00",
        "situacao": "aprovado",
        "descricao": "Pagamento confirmado pelo intermediador"
      }
    ]
}
}