# Revisão de auditoria e consistência dos recálculos (ONF10, RN08)

Revisão dos fluxos financeiros (criar, editar e excluir logicamente lançamentos) comparando o que a API mostra antes e depois de cada operação. Os cenários viraram testes de regressão de ponta a ponta em `backend/src/test/java/br/com/finamei/audit/FinancialConsistencyRegressionTest.java` (MockMvc, relógio fixo em 15/09/2026, rollback ao fim de cada teste).

## O que foi revisado

| Item | Onde | Resultado |
| --- | --- | --- |
| Criação, edição e exclusão de lançamento geram um registro de auditoria cada | `TransactionService.create/update/delete` chamam `AuditService.record` | OK |
| Auditoria identifica usuário, instante e operação | `AuditLog`: `actor_user_id` (do token), `occurred_at` (`Clock` injetado), `action` (CREATE/UPDATE/DELETE), `resource_id` | OK, coberto por teste |
| Auditoria na mesma transação (sem registro órfão nem operação sem registro) | `AuditService` com `Propagation.MANDATORY`; `AuditTrailTest` (PR #86) prova o rollback | OK |
| Escritas recusadas não deixam registro | Validação (400), outro usuário (404), inexistente (404), já excluído (404) lançam antes de gravar | OK, coberto por teste |
| Dados sensíveis fora da auditoria | `details` leva tipo, valor, data e categoria; a descrição (texto livre) fica de fora | OK |
| Excluídos fora de todos os totais | `deleted = false` em `totalsByType`, `totalsByTypeInPeriod`, `sumAmountByUserAndTypeAndPeriod` e em `TransactionSpecifications` (listagem) | OK |
| Saldo, faturamento e relatório usam a mesma regra | Faturamento soma só `INCOME`; saldo e relatório somam `INCOME - EXPENSE`; todos filtram por usuário do token e por `deleted = false` | OK |
| Limites de período | `between` inclusivo em datas (`LocalDate`): 01/01, 28/02, 01/03, 31/12 caem no período certo | OK, coberto por teste |

## Roteiro executado

Cada passo consulta `/transactions/summary?month=`, `/revenue/summary?year=` e `/reports?period=MONTHLY` antes e depois da operação.

1. Criar receita (1.000) e despesa (400) em setembro: saldo 600, receita do mês 1.000, despesa do mês 400, faturamento 1.000 (despesa não entra, RN04), relatório com 2 lançamentos.
2. Excluir a despesa: saldo 1.000, despesa do mês 0, relatório com 1 lançamento. Excluir a receita: tudo volta a zero e a listagem fica vazia.
3. Editar valor (1.000 para 1.750,50): saldo, mês, faturamento e relatório passam a 1.750,50.
4. Editar tipo (receita para despesa, com a categoria de despesa): saldo cai por 2 vezes o valor, faturamento perde o valor, despesa do mês o ganha; voltar a receita restaura tudo.
5. Editar data para outro mês (31/08): setembro esvazia e agosto recebe; saldo e faturamento anual não mudam. Para outro ano (31/12/2025): faturamento 2026 zera, 2025 recebe o valor, relatórios anuais acompanham.
6. Excluir lançamento de agosto, e depois de 31/12/2025: setembro (mês) permanece igual; só mudam o saldo geral e o período do lançamento excluído.
7. Fronteiras (01/01, 28/02, 01/03, 31/12): nenhum valor vaza para o período vizinho.
8. Operações de um usuário não alteram os totais de outro.
9. Auditoria: um registro por escrita bem-sucedida, com autor, instante do `Clock` e operação; tentativas recusadas (400/404), exclusão repetida e edição de excluído não geram registro.

## Achados

Nenhum bug de cálculo foi encontrado: todos os cenários passaram sem alterar o código de produção. Observações que ficam registradas, sem correção neste PR:

1. **Concorrência sem controle otimista.** `transactions` não tem coluna de versão. Duas requisições simultâneas sobre o mesmo lançamento (por exemplo, dois DELETE ao mesmo tempo, ou editar enquanto outro exclui) podem ambas passar por `findActiveOwned`, gerando dois registros de auditoria ou uma edição sobre um lançamento recém-excluído. Corrigir exige migration (`version`) e tratar `OptimisticLockingFailureException`; fica como pendência por ser raro no uso de um MEI por conta.
2. **Edição sem mudança gera registro `UPDATE`.** Enviar os mesmos valores audita mesmo assim (com `before` igual a `after`). É conservador e inofensivo; mantido.
3. **"Saldo atual" independe do mês consultado.** `balance` em `/transactions/summary` é o saldo acumulado de todos os lançamentos ativos (comportamento documentado no DTO); só `income` e `expense` dependem do mês. Os testes fixam esse contrato.

## Como reexecutar

```bash
cd backend
./mvnw -Dtest=FinancialConsistencyRegressionTest test
./mvnw verify
```
