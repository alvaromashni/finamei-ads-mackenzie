# Performance das consultas (ONF06, ONF07)

Revisão das consultas mais usadas do backend, com evidência antes/depois.

## Como reproduzir

```bash
cd backend
./mvnw test -Dtest=QueryPerformanceTest   # imprime as linhas QUERYCOUNT e PLAN
```

`QueryPerformanceTest` sobe a aplicação com `hibernate.generate_statistics=true`, popula cerca de
**40 mil lançamentos** (20 mil de um usuário "pesado", 5 usuários de ruído com 4 mil cada, 3% excluídos
logicamente, 2024 a 2026) e, para cada consulta, conta as instruções SQL preparadas
(`Statistics.getPrepareStatementCount`) e mostra a mediana de tempo. As contagens são **asserções** do
teste: uma regressão (por exemplo, voltar a um N+1) quebra o build.

Limitações: o banco dos testes é H2, não PostgreSQL (o Docker não estava disponível no ambiente da revisão).
Contagem de instruções vale igual nos dois; tempos e planos são só indicativos e servem para comparar
antes/depois no mesmo ambiente.

Tempos de ponta a ponta por HTTP, com 10 mil lançamentos, em [`teste-de-carga.md`](teste-de-carga.md).

## Consultas mais usadas

| Consulta | Endpoint |
|---|---|
| Listagem/filtro de lançamentos | `GET /api/v1/transactions` |
| Resumo de saldo | `GET /api/v1/transactions/summary` |
| Faturamento anual | `GET /api/v1/revenue/summary` |
| Guias do DAS | `GET /api/v1/das` |
| Categorias | `GET /api/v1/categories` |

## Resultado (instruções SQL por chamada)

| Consulta | Antes | Depois | Tempo (mediana, H2) antes / depois |
|---|---|---|---|
| Listagem, página de 100 | 3 | 3 | 10,1 ms / 9,0 ms (sem mudança) |
| Listagem, página de 20 com filtros (mês + tipo) | 3 | 3 | 8,0 ms / 6,7 ms (sem mudança) |
| Resumo de saldo do mês | **4** | **1** | **26,3 ms / 2,0 ms** |
| Faturamento anual | 2 | 2 | 1,3 ms / 1,4 ms (sem mudança) |
| Categorias do usuário | 1 | 1 | 1,5 ms / 0,8 ms (sem mudança) |
| DAS do ano corrente, 1º acesso (cria 12 guias) | **14** | **2** | n/a (as repetições medidas já não criam guias) |
| DAS do ano corrente, acessos seguintes | **2** | **1** | 3,1 ms / 2,1 ms |

## Mudanças

1. **Resumo de saldo: 4 consultas viraram 1.** Antes eram duas somas totais (receita, despesa) e duas do mês.
   Agora `TransactionRepository.totalsByType` devolve, com um `GROUP BY type`, o total geral e o do mês
   de cada tipo (`sum(case when data no mês ...)`). Mesmos valores, uma única passada pelos lançamentos
   do usuário.
2. **DAS: sem consulta duplicada e com inserção em lote.** `listGuides` buscava as guias do ano, criava
   as faltantes e buscava de novo; agora reaproveita a primeira consulta. Com `hibernate.jdbc.batch_size=25`,
   as 12 inserções da primeira consulta do ano saem em lote (uma instrução preparada em vez de 12).
   `order_inserts` ficou desligado de propósito: as entidades se referenciam só por UUID, sem associação
   mapeada, e reordenar os inserts por tipo violaria as chaves estrangeiras (um teste existente pegou isso).
3. **Categorias com teto.** A listagem de categorias não é paginada porque o cliente precisa de todas
   para montar os seletores. Para não ser ilimitada, cada usuário pode ter no máximo **200** categorias
   (as padrão são cerca de 20); a criação acima disso responde 400 com erro no campo `name`
   (`CategoryLimitTest`). A listagem fica limitada por construção.

## O que já estava correto (verificado, sem mudança)

- **Listagem de lançamentos não é ilimitada**: paginada, `size` limitado a 100
  (`listagemNuncaRetornaColecaoIlimitada` pede 1.000.000 e recebe 100).
- **Sem N+1 na listagem**: a categoria de cada lançamento é carregada em lote (`findAllById`). Uma página
  cheia de 100 lançamentos de ~12 categorias custa 3 instruções (contagem, página, categorias), e o teste
  quebra se passar disso. Com N+1 seriam cerca de 100.
- **Acessos do contador**: a lista carrega os contadores em lote (`findAllById`), também sem N+1. Ela
  é limitada pelo número de contadores distintos que o MEI autorizou manualmente; não foi paginada.
- **DAS**: no máximo 12 guias por ano consultado.

## Índices

**Nenhum índice novo foi adicionado.** Justificativa:

- O plano de execução (`EXPLAIN`) mostra a listagem filtrada e as somas por período usando
  `IX_TRANSACTIONS_USER_DATE` (`user_id`, `date`) (`consultasDeFiltroEPeriodoUsamOIndiceDeUsuarioEData`
  garante isso).
- Experimento: índice `(user_id, date DESC, created_at DESC)` para servir a ordenação da listagem
  sem filtro. No H2 o planejador continuou usando o índice da chave estrangeira e o ganho de tempo
  (10,1 ms para 7,3 ms) ficou dentro do ruído; o experimento não justifica o custo de escrita de um
  índice adicional, e o H2 não representa o planejador do PostgreSQL (que tem ordenação incremental).
  Fica como ponto a reavaliar com `EXPLAIN ANALYZE` em PostgreSQL com volume real.
- Observação sem ação: `ix_transactions_user_id` e `ix_das_guides_user_id` são prefixos de índices
  compostos já existentes (`ix_transactions_user_date`, `uk_das_guides_user_competence`) e, em
  PostgreSQL, são candidatos a remoção numa migration futura se a escrita for um problema.
- Limite conhecido: o saldo total soma todos os lançamentos ativos do usuário (custo linear no
  número de lançamentos dele). Com 20 mil lançamentos continua em ~2 ms; se isso crescer em ordens
  de grandeza, a saída é um saldo materializado ou índice cobrindo `amount`, não implementados
  por não terem justificativa medida hoje. A busca por trecho da descrição (`like '%x%'`) também
  não usa índice, mas sempre é restrita ao usuário.
