# Tech Stack — FinaMEI

Stack definida para o monorepo do FinaMEI, priorizando simplicidade e zero dependência de APIs de terceiros (nenhum provedor de e-mail, pagamento, ou serviço fiscal externo — tudo roda localmente/self-hosted).

## Estrutura do monorepo

```
finamei-ads-mackenzie/
├── backend/                  # API REST — Java 21 + Spring Boot (Maven)
│   ├── src/main/java/...
│   ├── src/main/resources/
│   │   └── db/migration/     # scripts Flyway (V1__..., V2__...)
│   └── src/test/java/...
├── frontend/                  # SPA — TypeScript + React + Vite
│   ├── src/
│   └── ...
├── docs/                       # material de apoio (ADRs, prints, notas)
├── .github/workflows/          # CI (build + testes + lint)
├── docker-compose.yml           # PostgreSQL para desenvolvimento local
├── tech-stack.md
├── roadmap.md
└── README.md
```

Um repositório único, dois times (back e front), integrados por um contrato de API (OpenAPI) exposto pelo próprio backend — sem repositório de "shared types" ou codegen, para manter simples.

## Back-end

| Camada | Escolha | Por quê |
|---|---|---|
| Linguagem/Runtime | Java 21 | Definido na documentação do projeto (LTS). |
| Framework | Spring Boot 4.x (última versão estável suportada) | Linha principal atual com suporte ativo. Usar Spring Web, Spring Data JPA e Spring Security. |
| Build | Maven | Definido na documentação do projeto. |
| Banco de dados | PostgreSQL 16 | Definido na documentação do projeto. |
| Migração de schema | Flyway | Versiona o schema junto do código, sem passo manual — essencial com 3 devs de back mexendo no banco em paralelo. |
| ORM | Hibernate via Spring Data JPA | Padrão do ecossistema Spring. |
| Autenticação | JWT (jjwt) + Spring Security | Token stateless, conforme ONF04. Access token curto (ex: 30 min, alinhado ao ONF04) — sem refresh token para manter simples no MVP. |
| Hash de senha | BCrypt (Spring Security `PasswordEncoder`, fator de custo 10) | Exigido por ONF03. |
| Validação | Jakarta Bean Validation (`@Valid`, `@NotNull`, `@DecimalMin` etc.) | Aplica RN06 (valor > 0, 2 casas decimais, data não futura, descrição ≤ 120 chars) direto nos DTOs. |
| Exportação de relatório | Apache POI (planilha) + OpenPDF (PDF) | Bibliotecas locais, sem chamada a serviço externo — atende OF16. |
| Testes | JUnit 5 + Mockito + Spring Boot Test | Cobertura mínima de 60% das regras de negócio (ONF11). |
| Banco em teste | H2 em memória (perfil `test`) | Evita depender de Postgres/Testcontainers na CI — mais simples, mais rápido. |
| Cobertura | JaCoCo | Gera relatório de cobertura para acompanhar a meta do ONF11. |
| Documentação de API | springdoc-openapi (Swagger UI) | Contrato vivo consumido pelo time de front sem precisar de codegen. |

**Organização em camadas** (já definida no cap. 8 da documentação): `Controller → Service → Repository → Domain`, um pacote por módulo de domínio (`auth`, `lancamento`, `categoria`, `faturamento`, `das`, `contador`, `usuario`).

## Front-end

| Camada | Escolha | Por quê |
|---|---|---|
| Linguagem | TypeScript | Tipagem estática, reduz bugs de integração com a API. |
| Build tool | Vite | Dev server rápido, zero-config para SPA React+TS. |
| Framework UI | React 18 | Definido na documentação do projeto. |
| Roteamento | React Router | Padrão para SPA em React. |
| Estado de servidor | TanStack Query | Cache, refetch e estados de loading/erro das chamadas à API sem escrever isso à mão. |
| Estado local/auth | Context API (`AuthContext`) | Só o token e o usuário logado — não precisa de Redux/Zustand para esse escopo. |
| Formulários | React Hook Form + Zod | Validação no front espelhando as regras do back (RN06, RN09), com pouco boilerplate. |
| HTTP client | `fetch` nativo com um wrapper fino | Evita adicionar Axios só por conveniência — nenhuma feature exclusiva dele é necessária aqui. |
| Estilo | Tailwind CSS | Estilização rápida e consistente sem manter um design system próprio. |
| Testes | Vitest + React Testing Library | Roda no mesmo mecanismo do Vite, sem config extra. |
| Lint/format | ESLint + Prettier | Padroniza os dois devs de front (e evita diffs de formatação em PR). |

Sem biblioteca de gráficos (Recharts etc.) no MVP: a barra de faturamento (RN02) é um componente simples de progresso em CSS. Se um relatório futuro precisar de gráfico, avaliar nesse momento — não antecipar.

## Infraestrutura de desenvolvimento

- **Docker Compose**: sobe só o PostgreSQL local (`db`), com volume persistente. Nada de orquestrar backend/frontend em container — cada um roda direto na máquina do dev (`mvn spring-boot:run` / `npm run dev`) para manter o ciclo de feedback rápido.
- **CI (GitHub Actions)**: um workflow por pasta (`backend-ci.yml`, `frontend-ci.yml`), disparado por path (`paths: backend/**` / `frontend/**`), rodando build + testes + lint em cada PR.
- **Versionamento**: Git + GitHub, conforme já definido na documentação. Convenção de branch: `feature/<of-ou-rn>-descricao` (ex: `feature/of05-registrar-receita`), PR obrigatório para `main` com pelo menos 1 review.
- **Quadro Kanban**: GitHub Projects, já definido na documentação.
- **Configuração por ambiente**: `application-dev.yml` / `application-test.yml` no backend; arquivo `.env` (não versionado) no frontend para a URL da API.

## Decisões explícitas de escopo (sem API de terceiros)

- **Redefinição de senha (OF03)**: fora do MVP e do plano inicial de 2 meses (decisão do time). Se for retomada depois, a abordagem deve continuar sem serviço de e-mail externo — por exemplo, gerar o link de redefinição e expô-lo em log/tela de desenvolvimento, sem enviar e-mail de verdade.
- **Exportação de relatórios (OF16)**: gerada localmente com Apache POI/OpenPDF, sem serviço externo de geração de documentos.
- **Limite anual do MEI (RN01)**: parâmetro configurável armazenado no próprio banco (tabela de configuração ou valor no `application.yml`), não consultado de nenhuma API da Receita Federal.
- **Vencimento do DAS (RN05)**: calculado em código (dia 20 do mês seguinte, ajustado para o próximo dia útil), sem integração com calendário de feriados externo — um `Set` de feriados nacionais fixos no ano corrente resolve o escopo do projeto.
