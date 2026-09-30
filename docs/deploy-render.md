# Deploy do backend no Render (free tier)

O backend roda no Render como **Web Service com Docker**, a partir de `backend/Dockerfile`. O frontend continua na Vercel (`https://finamei-ads-mackenzie.vercel.app`).

## Como a imagem foi otimizada para 512 MB

O free tier do Render tem **512 MB de RAM** e pouca CPU. Por isso:

- **Build em duas etapas:** a imagem final tem só o JRE 21 (Alpine) e a aplicação, sem Maven nem código-fonte, e roda com um usuário sem privilégios.
- **JVM enxuta** (`JAVA_TOOL_OPTIONS` no Dockerfile):
  - heap limitado a 50% da RAM;
  - coletor de lixo Serial;
  - só o compilador C1, que usa menos memória e sobe mais rápido;
  - pilha menor por thread;
  - reinício automático se faltar memória.
- **Perfil `prod`** (`application-prod.yml`): até 20 threads no Tomcat (o padrão é 200) e até 5 conexões com o banco.

Medido localmente com o container limitado a 512 MB e meia CPU:

| Situação | Memória |
|---|---|
| Depois de subir (~15 s) | ~240 MB |
| Depois de 300 requisições concorrentes | ~255 MB |

## 1. Criar o banco PostgreSQL

1. No Render, clique em **New → Postgres**.
2. Escolha um nome (ex.: `finamei-db`), a **mesma região** que será usada no backend e o plano **Free**.
3. Depois de criado, em **Connections**, anote: **Hostname**, **Port**, **Database**, **Username** e **Password**.

> O Postgres gratuito do Render **expira depois de 30 dias**. Para a apresentação isso basta, mas guarde a data. Alternativas gratuitas sem prazo: Neon ou Supabase (use os dados de conexão deles nas mesmas variáveis abaixo).

## 2. Criar o Web Service

1. Clique em **New → Web Service** e conecte o repositório `finamei-ads-mackenzie`.
2. Preencha:

| Campo | Valor |
|---|---|
| Language | **Docker** |
| Branch | `main` |
| Region | a mesma do banco |
| Root Directory | `backend` |
| Dockerfile Path | `./Dockerfile` |
| Instance Type | **Free** |

3. Em **Advanced → Health Check Path**, informe `/actuator/health`.

## 3. Variáveis de ambiente

Em **Environment**, cadastre:

| Variável | Valor | Obrigatória |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<Hostname>:5432/<Database>` | sim |
| `DB_USER` | Username do banco | sim |
| `DB_PASSWORD` | Password do banco | sim |
| `JWT_SECRET` | texto aleatório com **pelo menos 32 caracteres** (veja abaixo) | sim |
| `CORS_ALLOWED_ORIGINS` | `https://finamei-ads-mackenzie.vercel.app` | não (já é o padrão do perfil `prod`) |
| `JWT_EXPIRATION_MINUTES` | `30` | não |
| `DAS_MONTHLY_AMOUNT` | valor mensal do DAS, ex.: `76.90` | não |

- **Não** cadastre `PORT`: o Render define sozinho, e a aplicação já lê essa variável.
- Para `DB_URL`, use o **Hostname interno** do banco (o curto, como `dpg-xxxx-a`). Ele só funciona com o banco e o backend na mesma região. Se usar o hostname externo, acrescente `?sslmode=require` ao final da URL.
- Para gerar o `JWT_SECRET`, rode no terminal: `openssl rand -base64 48`. Se não tiver o OpenSSL, use qualquer texto aleatório longo. **Nunca reutilize o segredo de desenvolvimento.** Trocar o segredo desconecta todos os usuários, que só precisam entrar de novo.
- Sem `JWT_SECRET`, `DB_URL`, `DB_USER` ou `DB_PASSWORD`, a aplicação **não sobe**, e o log mostra `Could not resolve placeholder '<VARIÁVEL>'`. Isso é proposital, para nunca rodar em produção com valores de desenvolvimento.

## 4. Publicar e conferir

1. Clique em **Create Web Service**. O primeiro build leva alguns minutos.
2. Nos **Logs**, procure por `Started FinaMeiApplication`. Na primeira subida, o Flyway cria todas as tabelas e as categorias padrão.
3. Abra `https://<seu-servico>.onrender.com/actuator/health`. O esperado é `{"status":"UP", ...}`.
4. A documentação da API fica em `https://<seu-servico>.onrender.com/swagger-ui.html`.

A cada push na `main`, o Render refaz o deploy automaticamente.

## 5. Apontar o frontend (Vercel) para o backend

1. Na Vercel, abra o projeto → **Settings → Environment Variables**.
2. Crie `VITE_API_URL` com o valor `https://<seu-servico>.onrender.com/api/v1`.
3. Faça um **Redeploy**. O Vite grava essa URL no momento do build, então só alterar a variável não basta.

## 6. Comportamento do free tier

- **O serviço dorme depois de 15 minutos sem acesso.** A primeira requisição depois disso acorda o backend e pode levar **até cerca de 1 minuto**. As seguintes são normais.
- **Antes de uma apresentação**, abra a URL do `/actuator/health` alguns minutos antes para acordar o backend.

## Problemas comuns

| Sintoma | Causa provável e solução |
|---|---|
| Log: `Could not resolve placeholder 'JWT_SECRET'` (ou `DB_URL`...) | Variável não cadastrada. Cadastre em **Environment** e faça o redeploy. |
| Log: `Connection refused` ou `UnknownHostException` no banco | Banco e backend em regiões diferentes com o hostname interno. Use a mesma região, ou o hostname externo com `?sslmode=require`. |
| Log: `password authentication failed` | `DB_USER` ou `DB_PASSWORD` errados. Copie de novo em **Connections**. |
| No navegador: erro de **CORS** | A origem precisa ser exatamente `https://finamei-ads-mackenzie.vercel.app` (com `https://` e sem `/` no final). Deploys de preview da Vercel têm outras URLs e não são liberados. |
| O frontend continua chamando `localhost` | `VITE_API_URL` não foi definida na Vercel, ou faltou o redeploy depois de definir. |
| Serviço reiniciando com `OutOfMemoryError` | Improvável com o uso esperado. Se acontecer, ajuste `JAVA_TOOL_OPTIONS` em **Environment** (ex.: `-XX:MaxRAMPercentage=45`). |

## Testar a imagem localmente

Com o banco local rodando (`docker compose up -d db`):

```bash
docker build -t finamei-api backend
docker run --rm -m 512m -p 8080:10000 \
  -e PORT=10000 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/finamei \
  -e DB_USER=finamei -e DB_PASSWORD=finamei \
  -e JWT_SECRET=um-segredo-local-com-pelo-menos-32-caracteres \
  finamei-api
```

Depois, abra `http://localhost:8080/actuator/health`.
