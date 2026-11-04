# FinaMEI

Controle financeiro simples para o Microempreendedor Individual (MEI): receitas e despesas, saldo, acompanhamento do limite anual de faturamento, controle das guias do DAS, relatórios com exportação em PDF e planilha, e acesso somente leitura para o contador.

Projeto de Práticas Profissionais do curso de Análise e Desenvolvimento de Sistemas, Mackenzie, 2026.

| | |
|---|---|
| **Backend** | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway, PostgreSQL 16 |
| **Frontend** | React 18, TypeScript, Vite, Tailwind CSS, TanStack Query, React Hook Form, Zod |
| **Testes** | JUnit 5 + MockMvc + H2 (backend), Vitest + React Testing Library (frontend) |
| **Versão publicada** | Frontend na Vercel (<https://finamei-ads-mackenzie.vercel.app>) e API no Render |

### Iterações

Cada iteração entregue tem uma tag no repositório:

| Tag | Conteúdo |
|---|---|
| [`v1`](../../tree/v1) | Iteração 1: cadastro e login, painel, lançamentos, categorias, DAS, faturamento e telas iniciais. |
| `v2` | Iteração 2: perfil, acesso do contador, relatórios e exportação, auditoria, bloqueio de login, administrador, segurança, desempenho e testes. |

Para rodar uma iteração específica, troque para a tag dela antes de seguir os passos abaixo (ex.: `git checkout v1`).

---

## Como rodar na sua máquina

São três peças: o banco (PostgreSQL, no Docker), a API (backend) e a interface (frontend). Cada uma roda em um terminal. Os comandos abaixo funcionam no Linux, no macOS e no Windows; quando houver diferença, a versão do Windows aparece logo abaixo.

### 1. Pré-requisitos

| Ferramenta | Versão | Como conferir |
|---|---|---|
| Git | qualquer recente | `git --version` |
| Java (JDK) | **21** | `java -version` |
| Docker Desktop (ou Docker Engine + Compose) | Compose v2 ou mais novo | `docker compose version` |
| Node.js | **22 ou mais novo** (o CI usa o 22) | `node -v` |

Não é preciso instalar Maven: o projeto traz o Maven Wrapper (`mvnw`). Também não é preciso instalar PostgreSQL: ele roda no Docker.

> **Windows:** abra o Docker Desktop e espere ele ficar "running" antes do passo 3. Se o `java -version` mostrar outra versão, ajuste a variável `JAVA_HOME` para a pasta do JDK 21.

### 2. Clonar o repositório

```bash
git clone https://github.com/alvaromashni/finamei-ads-mackenzie.git
cd finamei-ads-mackenzie
```

### 3. Subir o banco de dados

Na raiz do projeto:

```bash
docker compose up -d db
```

Isso sobe um PostgreSQL 16 em `localhost:5432` (banco, usuário e senha `finamei`). Para conferir que ele está pronto, rode `docker compose ps` e espere o status `healthy`.

As tabelas não precisam ser criadas à mão: o Flyway cria e atualiza o schema sozinho quando a API sobe.

### 4. Rodar a API (backend)

Em um terminal, a partir da raiz do projeto:

```bash
cd backend
./mvnw spring-boot:run
```

No Windows (PowerShell ou Prompt de Comando):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

A primeira execução demora alguns minutos, porque o Maven baixa as dependências. A API está pronta quando aparecer a linha `Started FinaMeiApplication`. Ela fica em `http://localhost:8080`:

- Health check: <http://localhost:8080/actuator/health> (deve responder `{"status":"UP"}`)
- Documentação interativa da API (Swagger UI): <http://localhost:8080/swagger-ui.html>

Nenhuma configuração é obrigatória para rodar localmente: os valores padrão já apontam para o banco do passo 3. Veja [Configuração](#configuração) se precisar mudar algo.

### 5. Rodar a interface (frontend)

Em **outro** terminal, a partir da raiz do projeto:

```bash
cd frontend
npm ci
npm run dev
```

Abra <http://localhost:5173> no navegador. Em modo de desenvolvimento, a interface já usa a API local (`http://localhost:8080/api/v1`).

### 6. Primeiro acesso

1. Na tela de login, clique em **Criar conta** e cadastre um usuário. A senha precisa ter no mínimo 8 caracteres, com pelo menos uma letra e um número.
2. Depois do cadastro você entra direto no painel. As categorias padrão já vêm criadas, e as guias do DAS do ano são geradas no primeiro acesso à tela **DAS**.

Todo cadastro pela tela cria um usuário **MEI**. Os perfis **Contador** e **Administrador** são atribuídos direto no banco (não há tela para isso, de propósito). Para testar esses perfis, cadastre outra conta pela tela e promova-a:

```bash
# Contador: depois, entre como MEI, abra o menu "Contador" e libere o acesso para este e-mail
docker compose exec db psql -U finamei -d finamei -c "UPDATE users SET role='ACCOUNTANT' WHERE email='contador@exemplo.com'"

# Administrador (ver docs/administrador.md)
docker compose exec db psql -U finamei -d finamei -c "UPDATE users SET role='ADMIN' WHERE email='admin@exemplo.com'"
```

Troque o e-mail pelo da conta que você cadastrou. O perfil vai dentro do token de login, então **saia e entre de novo** com essa conta depois de promovê-la.

### 7. Parar tudo

- API e interface: `Ctrl+C` em cada terminal.
- Banco: `docker compose down` na raiz do projeto. Os dados ficam guardados para a próxima vez.
- Para apagar o banco e começar do zero: `docker compose down -v`.

---

## Testes

Os testes **não precisam** do banco do Docker nem da API rodando: o backend usa um banco H2 em memória e o frontend simula a API.

**Backend** (em `backend/`):

```bash
./mvnw verify          # Windows: .\mvnw.cmd verify
```

Roda todos os testes e gera o relatório de cobertura em `backend/target/site/jacoco/index.html`. O build falha se a cobertura ficar abaixo de 60% ([docs/cobertura-testes.md](docs/cobertura-testes.md)).

O teste de carga (10 mil lançamentos) fica fora do `verify` por ser mais lento. Para rodá-lo: `./mvnw test -Pload-test` ([docs/teste-de-carga.md](docs/teste-de-carga.md)).

**Frontend** (em `frontend/`):

```bash
npm run test           # testes (Vitest)
npm run test:coverage  # testes com relatório de cobertura
npm run lint           # ESLint
npm run format:check   # Prettier
npm run build          # checagem de tipos + build de produção
```

Os mesmos comandos rodam no GitHub Actions a cada pull request ([.github/workflows](.github/workflows)).

---

## Configuração

Tudo tem valor padrão para desenvolvimento local. Só mude se precisar.

**Banco no Docker** (`docker-compose.yml`): copie `.env.example` para `.env` na raiz e ajuste.

| Variável | Padrão | Para que serve |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | `finamei` | Nome do banco, usuário e senha |
| `POSTGRES_PORT` | `5432` | Porta do PostgreSQL na sua máquina |

**API** (`backend/src/main/resources/application.yml`): o Spring Boot **não lê** arquivo `.env`; defina as variáveis no terminal antes de rodar a API (ou na configuração de execução da sua IDE). O arquivo `backend/.env.example` lista todas elas.

| Variável | Padrão | Para que serve |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/finamei` | Endereço do banco |
| `DB_USER` / `DB_PASSWORD` | `finamei` | Credenciais do banco |
| `SERVER_PORT` | `8080` | Porta da API |
| `JWT_SECRET` | segredo só para desenvolvimento | Chave de assinatura do token (obrigatória em produção) |
| `JWT_EXPIRATION_MINUTES` | `30` | Duração da sessão |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,https://finamei-ads-mackenzie.vercel.app` | Origens autorizadas a chamar a API |
| `LOGIN_MAX_FAILED_ATTEMPTS` / `LOGIN_LOCK_MINUTES` | `5` / `15` | Bloqueio de login após tentativas erradas |

Exemplo, mudando a porta do banco para 5433:

```bash
# Linux/macOS
export DB_URL=jdbc:postgresql://localhost:5433/finamei
./mvnw spring-boot:run
```

```powershell
# Windows (PowerShell)
$env:DB_URL = "jdbc:postgresql://localhost:5433/finamei"
.\mvnw.cmd spring-boot:run
```

**Interface:** sem configuração, `npm run dev` usa a API local e `npm run build` usa a API publicada no Render. Para apontar para outra API, copie `frontend/.env.example` para `frontend/.env` e ajuste `VITE_API_URL`.

---

## Problemas comuns

| Sintoma | Causa e solução |
|---|---|
| `docker compose up` falha com "port is already allocated" ou "address already in use" | Já existe um PostgreSQL usando a porta 5432. Pare o outro serviço, ou crie um `.env` na raiz com `POSTGRES_PORT=5433` e rode a API com `DB_URL=jdbc:postgresql://localhost:5433/finamei`. |
| `docker compose` diz que não consegue conectar ao Docker | O Docker Desktop não está aberto. Abra-o e espere ficar "running". |
| A API não sobe: `Connection refused` ou `Connection to localhost:5432 refused` | O banco não está rodando. Rode `docker compose up -d db` e confira com `docker compose ps`. |
| A API não sobe: `release version 21 not supported` ou erro de versão de classe | O Maven está usando outro Java. Instale o JDK 21 e aponte `JAVA_HOME` para ele. |
| A API não sobe: `Port 8080 was already in use` | Outro programa usa a porta. Feche-o ou rode a API com `SERVER_PORT=8081` e o frontend com `VITE_API_URL=http://localhost:8081/api/v1`. |
| `./mvnw: Permission denied` (Linux/macOS) | Rode `chmod +x mvnw` uma vez. |
| A tela abre, mas o login dá erro de rede ou de CORS | A API não está rodando ou o frontend está em outra porta. Use a porta 5173, ou inclua a porta usada em `CORS_ALLOWED_ORIGINS`. |
| Conta bloqueada após errar a senha | É a regra de segurança: 5 erros bloqueiam por 15 minutos. Espere, ou libere com `docker compose exec db psql -U finamei -d finamei -c "DELETE FROM login_attempts"`. |

---

## Estrutura do repositório

```text
backend/    API REST (Spring Boot), migrations Flyway em src/main/resources/db/migration
frontend/   SPA (React + Vite), organizada por funcionalidade em src/features
docs/       Documentação de requisitos, testes, segurança, desempenho e deploy
.github/    Pipelines de CI do backend e do frontend
docker-compose.yml   PostgreSQL para desenvolvimento local
```

## Documentação

- [context.MD](context.MD): contexto do produto, regras de negócio e decisões do projeto
- [docs/tech-stack.md](docs/tech-stack.md): tecnologias e convenções
- [docs/roadmap.md](docs/roadmap.md): planejamento das sprints
- [docs/administrador.md](docs/administrador.md): perfil administrador
- [docs/checklist-seguranca.md](docs/checklist-seguranca.md): checklist de segurança
- [docs/performance.md](docs/performance.md) e [docs/teste-de-carga.md](docs/teste-de-carga.md): desempenho
- [docs/cobertura-testes.md](docs/cobertura-testes.md): cobertura de testes
- [docs/revisao-auditoria.md](docs/revisao-auditoria.md): auditoria e consistência dos cálculos
- [docs/responsividade.md](docs/responsividade.md) e [docs/roteiro-teste-navegadores.md](docs/roteiro-teste-navegadores.md): responsividade e navegadores
- [docs/deploy-render.md](docs/deploy-render.md): publicação da API no Render
