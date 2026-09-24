# Contexto do projeto — FinaMEI

Este arquivo fornece o contexto essencial para pessoas e agentes de código que trabalhem neste repositório. Antes de alterar o projeto, consulte também [`README.md`](README.md), [`docs/tech-stack.md`](docs/tech-stack.md) e [`docs/roadmap.md`](docs/roadmap.md).

## 1. Visão do produto

O **FinaMEI** é uma aplicação acadêmica de gestão financeira para Microempreendedores Individuais (MEIs). O sistema deve permitir que o usuário acompanhe receitas, despesas, saldo, faturamento anual e situação das guias DAS, além de gerar relatórios e, em uma etapa posterior, conceder acesso de leitura a um contador.

O MVP deve entregar uma fatia vertical funcional:

- cadastro e autenticação de usuário;
- painel com saldo mensal e progresso do faturamento anual;
- registro e listagem de receitas e despesas;
- categorias financeiras padrão;
- visualização das guias DAS e registro de pagamento.

O projeto não depende de APIs de terceiros para e-mail, pagamentos, dados fiscais, calendário ou geração de documentos. As funcionalidades devem funcionar localmente ou com recursos mantidos pelo próprio projeto.

## 2. Estado atual

O repositório está em fase inicial e contém principalmente documentação. As pastas `backend/` e `frontend/` descritas abaixo representam a estrutura-alvo e devem ser criadas durante a inicialização das aplicações.

Não trate uma tecnologia citada neste documento como já instalada: confirme sempre nos arquivos de build (`pom.xml` e `package.json`).

## 3. Estrutura do monorepo

```text
finamei-ads-mackenzie/
├── backend/                  # API REST em Java/Spring Boot
│   ├── src/main/java/
│   ├── src/main/resources/
│   │   └── db/migration/     # migrations Flyway
│   └── src/test/java/
├── frontend/                 # SPA em React/TypeScript/Vite
│   └── src/
├── docs/                     # documentação, decisões e material acadêmico
├── .github/workflows/        # pipelines independentes de back e front
├── docker-compose.yml        # PostgreSQL para desenvolvimento local
├── context.MD                # este contexto
└── README.md                 # instruções de execução para desenvolvedores
```

O backend e o frontend são aplicações independentes dentro do mesmo repositório. Não criar um pacote de tipos compartilhados nem geração automática de cliente no MVP. A integração deve ser guiada pelo contrato OpenAPI exposto pelo backend.

## 4. Stack adotada

### Backend

- Java 21;
- Spring Boot 4.x, sempre na última versão estável suportada da linha;
- Maven;
- Spring Web, Spring Data JPA e Spring Security;
- PostgreSQL 16;
- Flyway para evolução do schema;
- Jakarta Bean Validation para validação de entrada;
- JWT com `jjwt` para autenticação stateless;
- BCrypt com custo 10 para senhas;
- springdoc-openapi/Swagger UI para documentação da API;
- JUnit 5, Mockito e Spring Boot Test;
- H2 no perfil de testes;
- JaCoCo para cobertura;
- Apache POI e OpenPDF quando a exportação de relatórios for implementada.

### Frontend

- TypeScript;
- React 18;
- Vite;
- React Router;
- TanStack Query para estado remoto;
- Context API somente para autenticação e estado global realmente simples;
- React Hook Form e Zod;
- `fetch` nativo encapsulado em um cliente HTTP fino;
- Tailwind CSS;
- Vitest e React Testing Library;
- ESLint e Prettier.

### Desenvolvimento e CI

- Docker Compose deve subir apenas o PostgreSQL;
- backend e frontend devem rodar diretamente na máquina do desenvolvedor;
- GitHub Actions deve ter workflows separados e filtrados por caminho;
- configurações e segredos locais não devem ser versionados.

## 5. Arquitetura do backend

Adotar um monólito modular organizado por domínio, seguindo o fluxo:

```text
Controller -> Service -> Repository -> Domain
```

Módulos previstos: `auth`, `usuario`, `lancamento`, `categoria`, `faturamento`, `das`, `relatorio` e `contador`.

Diretrizes:

- controllers tratam HTTP, validação de entrada e conversão de DTOs; não contêm regra de negócio;
- services concentram casos de uso, transações e regras de negócio;
- repositories encapsulam persistência e consultas;
- entidades JPA não devem ser expostas diretamente pela API;
- usar DTOs específicos para request e response;
- preferir injeção por construtor;
- demarcar transações na camada de serviço;
- usar `BigDecimal` para valores monetários, nunca `double` ou `float`;
- usar `LocalDate` para datas sem horário e tipos temporais com fuso quando o instante for relevante;
- centralizar tratamento de erros com `@RestControllerAdvice`;
- nunca confiar em `usuarioId` enviado pelo cliente para determinar a propriedade de um recurso; usar a identidade autenticada;
- evitar dependências entre módulos que criem ciclos.

### Banco de dados

- toda alteração de schema deve ser feita por uma nova migration em `backend/src/main/resources/db/migration`;
- migrations aplicadas nunca devem ser editadas; criar uma versão posterior para corrigir ou evoluir o schema;
- nomear migrations como `V<numero>__<descricao_em_snake_case>.sql`;
- constraints importantes também devem existir no banco, mesmo quando já validadas na aplicação;
- exclusão de lançamentos é lógica quando a regra RN08 estiver em vigor;
- dados iniciais, como categorias padrão, devem ser semeados por migration de forma idempotente quando aplicável.

## 6. Arquitetura do frontend

Organizar o código por funcionalidade/domínio, mantendo infraestrutura compartilhada separada. Estrutura sugerida:

```text
frontend/src/
├── app/                      # roteamento, providers e bootstrap
├── features/                 # auth, lancamentos, categorias, das, relatorios...
├── components/               # componentes reutilizáveis sem regra de domínio
├── lib/                      # cliente HTTP e utilitários
├── hooks/                    # hooks compartilhados
├── types/                    # tipos exclusivamente do frontend
└── styles/
```

Diretrizes:

- componentes de página coordenam a interface; regras e acesso à API ficam em hooks/services da feature;
- chamadas remotas e seu cache devem usar TanStack Query;
- formulários devem usar React Hook Form com schemas Zod;
- regras de validação do frontend melhoram a experiência, mas o backend continua sendo a autoridade;
- manter o token e o usuário autenticado no `AuthContext`; não introduzir uma store global sem necessidade demonstrada;
- toda tela deve representar claramente os estados de carregamento, vazio, erro e sucesso;
- mensagens de erro devem ser compreensíveis e não expor detalhes internos;
- garantir navegação por teclado, rótulos de formulário, foco visível e contraste adequado;
- projetar de forma responsiva a partir de 360 px de largura;
- evitar componentes grandes: extrair partes quando houver responsabilidade própria ou reutilização real.

## 7. Contrato da API

- a API deve usar JSON e rotas versionadas sob `/api/v1`;
- o backend é a fonte de verdade do contrato, publicado via OpenAPI/Swagger;
- mudanças incompatíveis devem ser discutidas entre backend e frontend antes da implementação;
- usar corretamente os códigos HTTP (`200`, `201`, `204`, `400`, `401`, `403`, `404`, `409` e `500` conforme o caso);
- adotar um formato consistente de erro, por exemplo:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Os dados informados são inválidos.",
  "fieldErrors": {
    "valor": "O valor deve ser maior que zero."
  },
  "timestamp": "2026-09-24T12:00:00Z"
}
```

- datas devem ser serializadas em ISO 8601;
- valores monetários devem ser números decimais com no máximo duas casas na entrada;
- listagens com potencial de crescimento devem ser paginadas;
- não registrar tokens, senhas ou dados sensíveis em logs.

## 8. Regras importantes do domínio

- lançamentos financeiros devem ter valor positivo, no máximo duas casas decimais, data não futura e descrição de até 120 caracteres;
- o faturamento anual considera receitas, não despesas;
- o limite anual do MEI deve ser configurável localmente, sem consulta a serviço externo;
- o vencimento do DAS ocorre no dia 20 do mês seguinte, com ajuste definido pela regra de negócio do projeto;
- uma categoria inativa não pode ser usada em novos lançamentos;
- cada usuário só pode acessar seus próprios dados, salvo acesso explícito e somente leitura concedido a contador;
- após cinco tentativas inválidas de login, aplicar o bloqueio definido no roadmap;
- redefinição de senha está fora do MVP;
- em caso de divergência, a documentação funcional em `docs/` prevalece sobre resumos deste arquivo.

## 9. Segurança e configuração

- senhas devem ser armazenadas somente como hash BCrypt;
- tokens JWT devem ter expiração curta (referência inicial: 30 minutos);
- endpoints privados devem ser protegidos no backend, independentemente das proteções de rota do frontend;
- validar autorização por recurso, não apenas o papel do usuário;
- CORS deve permitir apenas as origens necessárias por ambiente;
- segredos e credenciais devem vir de variáveis de ambiente;
- fornecer arquivos `.env.example` sem valores secretos quando surgirem novas configurações;
- usar `application-dev.yml` e `application-test.yml` sem credenciais reais;
- não adicionar integrações externas sem uma decisão explícita do time.

## 10. Testes e qualidade

### Backend

- testar regras de negócio com testes unitários de service;
- criar testes de integração para persistência, segurança e endpoints críticos;
- cobrir isolamento de dados entre usuários e cenários de autorização;
- manter a meta de pelo menos 60% de cobertura das regras de negócio;
- não escrever testes que dependam da ordem de execução.

### Frontend

- testar comportamento observável, evitando detalhes internos de implementação;
- priorizar login, rotas protegidas, formulários, lançamento financeiro, faturamento e tratamento de erro;
- usar consultas acessíveis da Testing Library (`getByRole`, `getByLabelText`) sempre que possível;
- simular a fronteira HTTP, não a implementação dos hooks internos.

Antes de concluir uma mudança, executar os comandos aplicáveis ao trecho alterado. Quando os projetos forem inicializados, a interface esperada é:

```bash
# infraestrutura
docker compose up -d db

# backend
cd backend
./mvnw test
./mvnw spring-boot:run

# frontend
cd frontend
npm install
npm run lint
npm run test
npm run build
npm run dev
```

No Windows, usar `mvnw.cmd` no lugar de `./mvnw`.

## 11. Convenções de colaboração

- branches: `feature/<of-ou-rn>-<descricao>`, por exemplo `feature/of05-registrar-receita`;
- usar nomes de domínio em português de forma consistente no código (`Lancamento`, `Categoria`, `Faturamento`), evitando misturar traduções para o mesmo conceito;
- nomes técnicos consolidados podem permanecer em inglês (`Controller`, `Service`, `Repository`, `Request`, `Response`);
- manter commits pequenos e focados;
- não misturar refatorações não relacionadas com uma funcionalidade;
- atualizar OpenAPI, testes e documentação junto com mudanças de comportamento;
- uma pull request para `main` exige ao menos uma revisão;
- preservar alterações locais existentes e confirmar antes de remover ou reescrever trabalho de outra pessoa.

## 12. Critério de pronto

Uma tarefa só está concluída quando, conforme aplicável:

- atende ao objetivo funcional e às regras de negócio relacionadas;
- valida entradas e autorização no backend;
- possui testes relevantes e eles passam;
- lint e build passam no projeto afetado;
- migrations são reproduzíveis em um banco vazio;
- estados de carregamento, vazio e erro foram tratados no frontend;
- a interface funciona em telas a partir de 360 px;
- o contrato OpenAPI e a documentação foram atualizados;
- nenhum segredo ou arquivo local foi incluído no Git.

## 13. Prioridades para decisões futuras

Ao escolher entre alternativas, priorizar nesta ordem:

1. correção das regras de negócio e segurança dos dados;
2. simplicidade adequada a um projeto acadêmico com prazo curto;
3. clareza e facilidade de manutenção pelo time;
4. cobertura por testes e observabilidade de erros;
5. desempenho compatível com o volume esperado;
6. flexibilidade para requisitos ainda não confirmados.

Evitar abstrações especulativas, microsserviços, bibliotecas sem necessidade concreta e funcionalidades fora do roadmap atual.
