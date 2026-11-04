# Papel Administrador

O administrador gerencia contas (listar, consultar status, ativar/desativar e liberar bloqueio de login). Ele **não** recebe acesso implícito aos dados financeiros de ninguém: lançamentos, categorias, DAS, faturamento e relatórios continuam filtrados pelo id do próprio token.

## Como criar um administrador

Não existe endpoint público para isso (o cadastro `/api/v1/auth/register` sempre cria `MEI`). O administrador é criado promovendo, direto no banco, uma conta que já se cadastrou normalmente. Nenhuma senha ou hash fica versionado.

1. Cadastre a conta pela API ou pela tela de cadastro (a senha é escolhida por quem vai administrar).
2. Promova a conta com SQL, conectando ao PostgreSQL com credenciais de operação:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'admin@seu-dominio.com';
```

Em desenvolvimento local, com o Docker Compose do projeto:

```bash
docker compose exec postgres psql -U <usuario> -d <banco> \
  -c "UPDATE users SET role = 'ADMIN' WHERE email = 'admin@seu-dominio.com';"
```

3. Faça login de novo: o papel vai dentro do JWT, então tokens emitidos antes da promoção continuam com o papel antigo.

Para rebaixar: `UPDATE users SET role = 'MEI' WHERE email = '...';`.

## Endpoints (`/api/v1/admin/users`, somente ADMIN)

| Método e caminho | O que faz |
| --- | --- |
| `GET /api/v1/admin/users?page=0&size=20` | Lista paginada (`size` entre 1 e 100), da conta mais antiga para a mais nova. |
| `GET /api/v1/admin/users/{id}` | Consulta de uma conta e do seu status. |
| `PATCH /api/v1/admin/users/{id}/status` com `{"active": false}` | Desativa ou reativa a conta. |
| `POST /api/v1/admin/users/{id}/login-unlock` | Libera o bloqueio de login por tentativas (RN09) e zera a contagem. |

Resposta de conta: `id`, `name`, `email`, `role`, `active`, `createdAt`, `loginLocked`, `loginLockedUntil`. Nunca devolve hash de senha.

Regras:

- Outros papéis recebem 403 `ACCESS_DENIED`; sem token, 401.
- O administrador não pode desativar a própria conta (409 `CANNOT_DEACTIVATE_OWN_ACCOUNT`).
- Pedir o status que a conta já tem não altera nada nem gera auditoria.
- Usuário inexistente: 404 `NOT_FOUND`.
- Não há exclusão de conta.

## Auditoria

Toda alteração gera um registro em `audit_logs` (`resource_type = USER`, `action = UPDATE`, `actor_user_id` = administrador, `resource_id` = conta alterada), na mesma transação da alteração. O campo `details` é `activated`, `deactivated` ou `login lock cleared`.

## Conta desativada e token já emitido

O `JwtAuthenticationFilter` consulta o banco a cada requisição autenticada (`existsByIdAndActiveFalse`, uma consulta por chave primária). Se a conta está desativada, a requisição é tratada como não autenticada (401), mesmo com token ainda dentro da validade. Reativar a conta devolve o acesso com o mesmo token. O login já recusava contas inativas.

## Isolamento dos dados financeiros

`AdminFinancialIsolationTest` prova que um ADMIN não vê lançamentos, saldo, faturamento, categorias nem guias DAS de um MEI e não consegue editar ou apagar lançamento de outro usuário (404). Observação: por regra existente (ONF05), `ADMIN` ainda pode gravar nos módulos financeiros, mas somente nos próprios dados.
