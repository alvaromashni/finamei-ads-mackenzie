# Teste de carga leve (ONF06, ONF07)

Valida os tempos de resposta com cerca de **10 mil lançamentos** de um usuário:

- ONF06: 95% das operações medidas abaixo de **2 s**;
- ONF07: relatório anual abaixo de **5 s**.

A análise das consultas (contagem de instruções SQL e planos) está em [`performance.md`](performance.md);
este documento mede o tempo de ponta a ponta, por HTTP.

## Ferramenta

`LoadResponseTimeTest` (`backend/src/test/java/br/com/finamei/performance/`), um teste JUnit marcado com
`@Tag("load")`. Ele sobe a aplicação inteira numa porta real, cria o usuário e a massa de dados, faz login
e dispara requisições HTTP reais (`java.net.http.HttpClient`) contra a API, passando por filtro JWT,
controller, service, Hibernate e banco. Não depende de nenhum serviço externo nem de ferramenta extra.

Ele **não roda no `./mvnw verify` padrão** (o CI continua rápido): o `pom.xml` exclui o grupo `load` do
Surefire, e o perfil Maven `load-test` o liga.

## Massa de dados

- 1 usuário com as categorias padrão e **10.000 lançamentos** (`load.transactions`), 40% receitas e 60%
  despesas, datas aleatórias (semente fixa) entre 01/01/2025 e 15/09/2026, 3% excluídos logicamente.
- Relógio fixo em 15/09/2026, para o resultado não depender do dia em que o teste roda.
- Inserção direta por JDBC em lote; ao final o teste apaga o que criou. Roda `ANALYZE` depois da carga.

## Operações medidas

| Operação | Requisição |
|---|---|
| Login | `POST /api/v1/auth/login` (BCrypt, custo 10) |
| Listagem de lançamentos, sem filtro | `GET /api/v1/transactions?page=N&size=20` |
| Listagem com filtro (mês + tipo) | `GET /api/v1/transactions?type=INCOME&from=&to=` (mês varia a cada repetição) |
| Listagem com filtro (categoria + descrição) | `GET /api/v1/transactions?categoryId=&description=` |
| Resumo de saldo | `GET /api/v1/transactions/summary?month=` |
| Faturamento | `GET /api/v1/revenue/summary?year=2026` |
| DAS | `GET /api/v1/das?year=` |
| Relatório mensal | `GET /api/v1/reports?period=MONTHLY&year=&month=` |
| Relatório anual | `GET /api/v1/reports?period=ANNUAL&year=` |

Cada operação tem 10 requisições de aquecimento (descartadas) e depois **50 repetições medidas**, variando
página, mês e ano para não medir só um cache quente. Duas fases:

1. **Sequencial**: 1 usuário, 450 medições.
2. **Concorrente**: 5 usuários simultâneos (threads) usando o mesmo token, 25 repetições por operação cada
   (1.125 medições).

Todas as respostas precisam ser 200, senão o teste falha. O teste também falha se:

- o p95 de todas as medições (em cada fase) for >= 2 s (`load.p95Ms`);
- o maior tempo do relatório anual (em cada fase) for >= 5 s (`load.annualMs`).

## Como reproduzir

Pré-requisitos: JDK 21 (o `mvnw` baixa o Maven). Para PostgreSQL, Docker.

### Em H2 (padrão, sem Docker)

```bash
cd backend
./mvnw test -Pload-test          # Windows: mvnw.cmd test -Pload-test
```

### Em PostgreSQL 16

```bash
docker compose up -d db          # PostgreSQL 16 do projeto, porta 5432
cd backend
./mvnw test -Pload-test \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/finamei \
  -Dspring.datasource.username=finamei \
  -Dspring.datasource.password=finamei \
  -Dspring.datasource.driver-class-name=org.postgresql.Driver
```

(Nas medições abaixo a porta foi trocada para 55432 com `docker run` para não interferir no banco de
desenvolvimento existente; é a mesma imagem `postgres:16-alpine` do `docker-compose.yml`.)

A tabela completa é impressa no console e gravada em `backend/target/load-test-results.md`.
Parâmetros opcionais (`-D`): `load.transactions` (10000), `load.reps` (50), `load.threads` (5),
`load.p95Ms` (2000), `load.annualMs` (5000).

## Ambiente das medições (04/11/2026)

| Item | Valor |
|---|---|
| Máquina | Intel Core i5-10400 (6 núcleos / 12 threads, 2,9 GHz), 16 GB de RAM |
| Sistema | Windows 11 Pro |
| Java | OpenJDK 21.0.12 (LTS) |
| Aplicação | Spring Boot 4.1.1, Tomcat embutido (200 threads), pool Hikari padrão (10 conexões) |
| Banco (1) | H2 2.4.240 em memória, modo PostgreSQL (perfil `test`) |
| Banco (2) | PostgreSQL 16.15 (`postgres:16-alpine`, Docker Desktop no mesmo computador) |
| Cliente | O próprio teste, na mesma máquina (sem latência de rede) |

## Resultados

Tempos em milissegundos, por requisição HTTP completa. 10.000 lançamentos, 50 repetições por operação.

### PostgreSQL 16, sequencial (1 usuário)

| Operação | Amostras | p50 | p95 | Máx |
|---|---:|---:|---:|---:|
| Login | 50 | 72,7 | 79,1 | 86,7 |
| Listagem de lançamentos (página 20, sem filtro) | 50 | 19,1 | 24,5 | 25,7 |
| Listagem de lançamentos (mês + tipo) | 50 | 15,5 | 17,7 | 18,7 |
| Listagem de lançamentos (categoria + descrição) | 50 | 17,0 | 20,1 | 23,0 |
| Resumo de saldo do mês | 50 | 10,7 | 13,7 | 17,8 |
| Faturamento anual | 50 | 9,8 | 12,3 | 14,2 |
| DAS do ano | 50 | 6,5 | 8,6 | 8,7 |
| Relatório mensal | 50 | 12,8 | 19,7 | 24,1 |
| **Relatório anual** | 50 | 14,9 | 18,7 | 18,9 |
| **Todas** | 450 | 14,6 | 72,9 | 86,7 |

### PostgreSQL 16, 5 usuários simultâneos

| Operação | Amostras | p50 | p95 | Máx |
|---|---:|---:|---:|---:|
| Login | 125 | 130,2 | 148,0 | 152,6 |
| Listagem de lançamentos (página 20, sem filtro) | 125 | 19,4 | 43,6 | 79,5 |
| Listagem de lançamentos (mês + tipo) | 125 | 15,9 | 23,7 | 34,2 |
| Listagem de lançamentos (categoria + descrição) | 125 | 16,3 | 19,6 | 22,4 |
| Resumo de saldo do mês | 125 | 11,0 | 13,3 | 15,9 |
| Faturamento anual | 125 | 10,2 | 12,1 | 12,8 |
| DAS do ano | 125 | 6,4 | 9,5 | 13,9 |
| Relatório mensal | 125 | 13,9 | 16,6 | 17,9 |
| **Relatório anual** | 125 | 17,5 | 21,9 | 24,2 |
| **Todas** | 1125 | 15,0 | 131,5 | 152,6 |

### H2 em memória (o banco dos testes), sequencial

| Operação | p50 | p95 | Máx |
|---|---:|---:|---:|
| Login | 69,7 | 77,3 | 86,5 |
| Listagem de lançamentos (página 20, sem filtro) | 17,8 | 27,2 | 31,2 |
| Listagem de lançamentos (mês + tipo) | 10,8 | 14,8 | 21,7 |
| Listagem de lançamentos (categoria + descrição) | 16,9 | 28,1 | 33,2 |
| Resumo de saldo do mês | 13,1 | 15,4 | 16,6 |
| Faturamento anual | 4,6 | 6,9 | 18,2 |
| DAS do ano | 5,0 | 6,9 | 9,1 |
| Relatório mensal | 6,5 | 11,6 | 16,5 |
| Relatório anual | 15,2 | 19,5 | 26,8 |
| **Todas** | 13,0 | 69,9 | 86,5 |

Com 5 usuários simultâneos em H2 o p95 geral foi 141,5 ms e o maior tempo 162,4 ms (relatório anual: p95 60,8 ms).

### Folga de escala (PostgreSQL, 50 mil lançamentos, sequencial)

Mesma execução com `-Dload.transactions=50000` (5 vezes a massa pedida): p95 geral 74,1 ms; relatório
anual p50 36,5 ms, p95 45,4 ms, máx 47,4 ms; resumo de saldo p95 36,7 ms. O tempo cresce de forma
aproximadamente linear com a massa e continua duas ordens de grandeza abaixo dos limites.

## Critérios

| Critério | Limite | Medido (PostgreSQL, 10 mil lançamentos) | Resultado |
|---|---|---|---|
| 95% das operações abaixo de 2 s | p95 < 2.000 ms | 100% das 450 medições < 2 s; p95 geral 72,9 ms (131,5 ms com 5 simultâneos) | Atendido |
| Relatório anual | < 5 s | máx 18,9 ms (24,2 ms com 5 simultâneos) | Atendido |
| Procedimento reproduzível | | Comando único acima, massa com semente fixa e relógio fixo | Atendido |

## Gargalos e otimizações

- **O login é a operação mais lenta** (cerca de 75 ms; 130-150 ms com 5 simultâneos), por causa do BCrypt
  com custo 10 exigido pelo ONF03. É intencional: o custo é o que protege as senhas e fica ~25 vezes abaixo
  do limite. Não foi otimizado.
- **Listagem sem filtro e com descrição** são as mais caras entre as consultas (20-30 ms): a contagem total
  e o filtro por `like` na descrição percorrem os lançamentos do usuário (o índice `ix_transactions_user_date`
  cobre usuário e data, não o texto). Com 10 mil lançamentos por usuário isso é irrelevante; se algum dia
  um usuário passar de centenas de milhares, a descrição pediria índice trigrama (`pg_trgm`) e a página
  poderia trocar a contagem exata por paginação por cursor.
- **Relatórios** usam uma única agregação (`group by type`) mais o faturamento, sem carregar lançamentos
  em memória; por isso o relatório anual custa quase o mesmo que o mensal.
- Nenhuma otimização de código foi necessária para atingir os critérios. As otimizações que já existiam
  (resumo de saldo em 1 consulta, DAS em lote, categorias em lote, índices) estão em
  [`performance.md`](performance.md).

## Limitações

- Cliente e servidor na mesma máquina: não mede latência de rede nem o ambiente de produção (Render free
  tier, com CPU compartilhada, `maximum-pool-size: 5` e partida a frio). Em produção os tempos serão
  maiores; a folga de duas ordens de grandeza deixa margem, mas isso não foi medido.
- Carga "leve" por definição: 1 e 5 usuários simultâneos, um único usuário com massa grande. Não é um
  teste de capacidade nem de estresse.
- PostgreSQL rodou em contêiner no Docker Desktop (VM Linux no Windows), o que costuma ser mais lento que
  uma instalação nativa; H2 e PostgreSQL deram resultados da mesma ordem.
