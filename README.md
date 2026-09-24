# FinaMEI

Projeto de Práticas Profissionais do curso de ADS — Mackenzie, 2026.

Monorepo com uma API REST em Java 21 + Spring Boot 4 e uma SPA em React + TypeScript + Vite.

## Pré-requisitos

- Java 21;
- Docker e Docker Compose;
- Node.js 24 ou versão LTS compatível;
- npm.

## Banco de dados

Na raiz do projeto:

```bash
docker compose up -d db
```

O PostgreSQL ficará disponível em `localhost:5432`. Os valores padrão estão no `docker-compose.yml` e podem ser sobrescritos copiando `.env.example` para `.env`.

## Backend

```bash
cd backend
./mvnw spring-boot:run
```

No Windows, use `mvnw.cmd spring-boot:run`. A API inicia em `http://localhost:8080`, com health check em `/actuator/health` e Swagger UI em `/swagger-ui.html`.

Para executar os testes:

```bash
./mvnw verify
```

## Frontend

Copie `frontend/.env.example` para `frontend/.env` se precisar alterar a URL da API.

```bash
cd frontend
npm install
npm run dev
```

A aplicação inicia em `http://localhost:5173`.

Verificações disponíveis:

```bash
npm run lint
npm run test
npm run build
npm run format:check
```

Consulte [context.MD](context.MD), [docs/tech-stack.md](docs/tech-stack.md) e [docs/roadmap.md](docs/roadmap.md) antes de implementar funcionalidades.
