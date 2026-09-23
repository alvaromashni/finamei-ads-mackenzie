# Roadmap de implementação — FinaMEI

Referências: `OFxx` = objetivo funcional, `RNxx` = regra de negócio, `ONFxx` = objetivo não funcional (ver documentação do projeto).

## Time

| Área | Integrante(s) |
|---|---|
| Back-end | Álvaro Luiz Sebolt Mashni, Hiury Sato, Henrique Apulcro do Nascimento |
| Front-end | Jennifer Herschel Maia, Murilo Dextro Comelli |

## Cronograma geral

- **Semana 1**: MVP (fatia vertical mínima e demonstrável).
- **Semanas 2 a 9** (~2 meses): 4 sprints de 2 semanas completando o restante do escopo.

Redefinição de senha (OF03) fica fora deste plano — ver "Backlog" no final.

---

## Semana 1 — MVP

Objetivo: um microempreendedor consegue logar, ver o painel com saldo e faturamento, registrar receita/despesa e conferir a situação do DAS. Sem edição/exclusão, sem contador, sem relatórios, sem categorias gerenciáveis (categorias padrão semeadas via migration).

### Dia 1 (todos)
- Alinhar o contrato de API das rotas do MVP (endpoints, payloads, códigos de erro) — 30–60 min, todo o time junto, antes de cada um seguir isolado.
- Back: inicializar projeto Spring Boot (Maven), `docker-compose.yml` com Postgres, primeira migration Flyway (tabelas `usuario`, `categoria`, `lancamento`).
- Front: inicializar projeto Vite + React + TS, Tailwind, estrutura de pastas, roteador com rotas placeholder (`/login`, `/painel`, `/lancamentos`, `/das`).

### Back-end (paralelo, dias 2–5)
- **Álvaro** — `auth`: entidade `Usuario`, cadastro (OF01, e-mail único), login com Spring Security + JWT (OF02, ONF04), hash BCrypt (ONF03). Pipeline de CI do backend.
- **Hiury** — `lancamento`/`categoria`: entidades `Lancamento` (+ `Receita`/`Despesa`), `Categoria`; endpoints de registrar receita e despesa (OF05, OF07) com validação RN06; categorias padrão via seed.
- **Henrique** — `faturamento`/`das`: `FaturamentoService` somando receitas do ano e classificando faixa (RN01–RN04, OF11–OF13); entidade `DAS` com guias do ano semeadas e endpoint para marcar mês como pago (RN05, OF14).

### Front-end (paralelo, dias 2–5)
- **Jennifer** — shell da aplicação (layout, navegação, `AuthContext`, rotas protegidas), tela de login (OF02) e painel inicial consumindo saldo + faturamento (OF11, RN02 com alerta de faixa).
- **Murilo** — tela de movimentações (lista simples, sem filtro ainda) e formulário de nova receita/despesa (OF05, OF07); tela de controle do DAS com ação de marcar como pago (OF14).

### Critério de pronto do MVP
- Login funcional de ponta a ponta.
- Painel mostra saldo do mês e barra de faturamento com faixa/alerta (RN02).
- Registrar receita e despesa persiste e atualiza saldo/faturamento.
- Tela de DAS lista os 12 meses e permite marcar pagamento.

---

## Sprint 2 — Semanas 2–3: consolidar o núcleo financeiro

- **Back (Hiury)**: editar e excluir lançamento com exclusão lógica (RN08), recalculando saldo/faturamento; validação de propriedade do registro (ONF05, HTTP 403).
- **Back (Henrique)**: gerenciar categorias — criar, editar, inativar (OF10, RN07); regra de categoria inativa não pode ser usada em novo lançamento.
- **Back (Álvaro)**: bloqueio de conta após 5 tentativas de login (RN09); consulta/alteração de perfil do usuário (OF04); expiração de sessão por inatividade (ONF04).
- **Front (Murilo)**: ações de editar/excluir na lista de movimentações; filtros por período, tipo, categoria e descrição (OF09); tela de gerenciar categorias.
- **Front (Jennifer)**: tela de perfil do usuário (OF04); tratamento de erros de formulário em linguagem simples, com destaque de campo (ONF01, ONF02).

Critério de pronto: fluxo de lançamentos fica completo (criar/editar/excluir/filtrar), com feedback de erro amigável e categorias administráveis.

## Sprint 3 — Semanas 4–5: relatórios e exportação

- **Back (Henrique)**: `RelatorioService` consolidando receitas, despesas, saldo e faturamento por período mensal/anual (OF15, RN03/RN04).
- **Back (Hiury)**: exportação em PDF (OpenPDF) e planilha (Apache POI) do relatório (OF16).
- **Back (Álvaro)**: registro de auditoria (criação/edição/exclusão com data, hora e usuário — ONF10) e revisão de performance das consultas mais usadas (índices, paginação) mirando ONF06/ONF07.
- **Front (Jennifer + Murilo)**: tela de relatórios com seleção de período e tipo, exibição consolidada e botões de exportação (OF15, OF16).
- **Front (ambos)**: passe de responsividade em todas as telas existentes, validando a partir de 360px de largura (ONF08).

Critério de pronto: um microempreendedor gera e exporta um relatório mensal/anual; todas as telas do MVP+sprint2 funcionam em mobile.

## Sprint 4 — Semanas 6–7: papéis de Contador e Administrador

- **Back (Álvaro)**: entidade `AcessoContador` — conceder/revogar acesso de leitura (RN11); autorização garantindo que o contador só acesse dados dos clientes vinculados e nunca escreva (ONF05).
- **Back (Hiury)**: endpoints de relatório em modo somente leitura para o contador, reaproveitando `RelatorioService` (OF17).
- **Back (Henrique)**: papel Administrador básico — listar/gerenciar contas e status de usuários (gestão de contas, conforme visão geral dos interessados).
- **Front (Murilo)**: tela do microempreendedor para conceder/revogar acesso de contador.
- **Front (Jennifer)**: área do contador — lista de clientes vinculados e tela de relatórios somente leitura (OF17).

Critério de pronto: um segundo usuário (contador) loga com conta própria e vê, em modo leitura, os relatórios dos clientes que autorizaram acesso.

## Sprint 5 — Semanas 8–9: hardening, testes e fechamento

- **Todo o back**: elevar cobertura de testes automatizados das regras de negócio até 60% (ONF11), com JaCoCo reportando no CI.
- **Álvaro**: checklist de segurança — TLS/HTTPS no ambiente de deploy, expiração de sessão, isolamento de dados por usuário (ONF03–ONF05).
- **Henrique**: teste de carga leve/manual para validar ONF06 (95% das operações < 2s com base de ~10.000 lançamentos) e ONF07 (relatório anual < 5s).
- **Hiury**: revisão de auditoria (ONF10) e consistência dos recálculos de saldo/faturamento após exclusão lógica (RN08).
- **Front (ambos)**: testes com Vitest/RTL nos formulários e fluxos críticos (login, registrar lançamento, faturamento); revisão final de usabilidade (ONF01/ONF02) e cross-browser (ONF08).
- **Todos**: ensaio da apresentação/demo final e atualização da documentação do projeto com o que foi efetivamente entregue.

Critério de pronto: sistema cobre OF01–OF17 (exceto OF03), regras de negócio RN01–RN11 (exceto RN10), e os objetivos não funcionais ONF01–ONF11 verificados manualmente ou por teste.

---

## Backlog (fora do plano de 2 meses)

- **OF03 / RN10 — Redefinição de senha por e-mail**: descoberta pelo time como fora de escopo por enquanto. Se retomada, manter a restrição de "sem API de terceiros": gerar o link de redefinição de uso único e expô-lo via log/tela de desenvolvimento em vez de enviar e-mail real, ou introduzir um SMTP local (ex: Mailpit) só nesse momento, sem comprometer o cronograma atual.
