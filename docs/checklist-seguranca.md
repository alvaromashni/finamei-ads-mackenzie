# Checklist de segurança (ONF03, ONF04, ONF05)

Revisão final dos requisitos de segurança (issue #37). Cada item traz a evidência e o resultado:

- **OK**: já atendido, com evidência.
- **Corrigido neste PR**: problema encontrado e resolvido aqui.
- **Pendente**: lacuna conhecida, com justificativa.

Os testes citados ficam em `backend/src/test/java/br/com/finamei/`. O teste consolidado de acesso indevido é `security/DataIsolationTest.java`.

## 1. Transporte seguro

| Item | Evidência | Resultado |
|---|---|---|
| Backend servido por HTTPS no Render | O Render emite certificado e redireciona HTTP para HTTPS no proxy (`docs/deploy-render.md`). Conferir no deploy: `curl -sI http://<servico>.onrender.com/actuator/health` deve devolver 301 para `https://`. | OK (configuração da plataforma, não verificável no repositório) |
| Frontend servido por HTTPS na Vercel | Domínio `https://finamei-ads-mackenzie.vercel.app`; a Vercel força HTTPS. | OK (plataforma) |
| Frontend chama o backend por HTTPS em produção | `frontend/src/lib/http.ts`: `http://localhost:8080` só é usado em `npm run dev`; o build usa `https://finamei-ads-mackenzie.onrender.com/api/v1`. | OK |
| Aplicação reconhece HTTPS atrás do proxy | `application-prod.yml`: `server.forward-headers-strategy: native`. Sem isso o Tomcat via o tráfego do proxy como HTTP e o Spring Security não enviava o cabeçalho HSTS. Teste: `config/ProductionConfigurationTest`. | Corrigido neste PR |
| CORS restrito | `SecurityConfig.corsConfigurationSource`: origens vêm de `app.cors.allowed-origins`; só os métodos GET/POST/PUT/PATCH/DELETE/OPTIONS e os cabeçalhos `Authorization` e `Content-Type`; sem `*`. Produção: só `https://finamei-ads-mackenzie.vercel.app` (`application-prod.yml`). Testes: `SecurityFilterChainTest` (preflight de origem permitida e de origem não permitida), `ProductionConfigurationTest.corsDeProducaoAceitaSomenteOrigemHttps`. | OK |
| Nenhum endpoint depende de HTTP puro | Não há porta HTTP extra, redirecionamento manual nem URL `http://` no código de produção (`grep -rn "http://" backend/src/main frontend/src`: só `localhost` de desenvolvimento e `.env.example`). | OK |
| Swagger UI e `/v3/api-docs` públicos em produção | `SecurityConfig` libera esses caminhos. Expõe só o contrato, nenhum dado. | Pendente: aceito para o projeto acadêmico; para endurecer, desligar com `springdoc.api-docs.enabled=false` no perfil `prod` |

## 2. JWT e sessão (ONF03, ONF04)

| Item | Evidência | Resultado |
|---|---|---|
| Assinatura HMAC verificada | `JwtService.parseClaims` usa `verifyWith(key)`. Testes: `JwtServiceTest`, `DataIsolationTest.tokenComAssinaturaInvalidaRecebe401`. | OK |
| Algoritmo não manipulável | O jjwt 0.13 rejeita tokens `alg=none` e só aceita `parseSignedClaims`. Teste: `DataIsolationTest.tokenSemAssinaturaAlgNoneRecebe401`. | OK |
| Payload adulterado é recusado | `DataIsolationTest.tokenComPayloadAdulteradoRecebe401` (troca `sub` mantendo a assinatura). | OK |
| Segredo vem de variável de ambiente | `application.yml`: `${JWT_SECRET:...}`. | OK |
| Sem default inseguro em produção | `application-prod.yml`: `secret: ${JWT_SECRET}` sem valor padrão; a aplicação não sobe sem a variável. O default de desenvolvimento contém "do-not-use-in-production". `ProductionConfigurationTest` impede que alguém reintroduza um default. | OK |
| Chave com tamanho mínimo | `Keys.hmacShaKeyFor` lança `WeakKeyException` para menos de 32 bytes; a aplicação não sobe com segredo fraco. | OK |
| Expiração de 30 min | `app.jwt.expiration-minutes: ${JWT_EXPIRATION_MINUTES:30}`; teste `ProductionConfigurationTest.expiracaoDoTokenPadraoEhDe30Minutos`. | OK |
| Token expirado rejeitado (401) | `JwtServiceTest.deveRejeitarTokenExpirado`; `DataIsolationTest.tokenExpiradoRecebe401`. | OK |
| Teste de expiração realmente testa a expiração | `SecurityFilterChainTest.deveRejeitarAcessoComTokenExpirado` cria o token expirado com um segredo diferente do da aplicação, então passa por assinatura inválida, não por expiração. O novo `DataIsolationTest.tokenExpiradoRecebe401` usa o segredo real. | Corrigido neste PR (teste novo; o antigo foi mantido) |
| Requisição sem token → 401 | `SecurityFilterChainTest`; `DataIsolationTest.requisicaoSemTokenRecebe401EmTodosOsModulos` (transações, categorias, DAS, contador, perfil, faturamento). | OK |
| Sessão absoluta, não deslizante | A janela de 30 min conta do login (sem refresh token), conforme `docs/tech-stack.md`. O frontend derruba a sessão ao expirar (`AuthProvider`). | Pendente: decisão do MVP; inatividade estrita exigiria reemissão de token |
| Token continua válido após desativar o usuário | `JwtAuthenticationFilter` não consulta o banco; um token emitido vale até expirar (no máximo 30 min). O sistema não tem fluxo de desativação de conta. | Pendente: baixo risco, dependeria de consulta por requisição |
| Token no navegador | Fica em `sessionStorage` (`AuthProvider`): some ao fechar a aba, mas é acessível a XSS. Não há `dangerouslySetInnerHTML`. | OK com ressalva: cookie `HttpOnly` seria mais forte, mudança grande fora do escopo |

## 3. Senhas e login

| Item | Evidência | Resultado |
|---|---|---|
| Hash BCrypt, custo 10 | `SecurityConfig.passwordEncoder` (`BCRYPT_STRENGTH = 10`). Senha nunca armazenada nem devolvida: `RegisterUserControllerTest` verifica `$.password` e hash ausentes. | OK |
| Política de senha no cadastro | `RegisterUserRequest`: mínimo 8 caracteres, ao menos uma letra e um número. Testes em `RegisterUserControllerTest`. | OK |
| Bloqueio após 5 falhas (RN09) | `LoginService`; `LoginLockoutControllerTest`, `LoginServiceTest` (PR #85). 423 com `Retry-After`. | OK |
| Resposta de login não revela se o e-mail existe | Mesma sequência 401/423 para e-mail existente ou não (`LoginLockoutControllerTest`). | OK |

## 4. Isolamento de dados (ONF05)

O dono do recurso sempre vem do token (`@AuthenticationPrincipal UUID`), nunca do corpo ou do caminho. Os repositórios filtram por `userId` (`findByIdAndUserIdAndDeletedFalse`, `findByIdAndUserId`, `findByIdAndMeiIdAndRevokedAtIsNull`). Recurso de outro usuário responde 404, igual a um id inexistente.

| Recurso | Cenário | Teste | Resultado |
|---|---|---|---|
| Lançamentos | B edita/exclui lançamento de A → 404, dado intacto | `DataIsolationTest.usuarioBNaoAlteraNemExcluiLancamentoDoUsuarioA`; `TransactionEditDeleteFilterTest.naoDeveEditarLancamentoDeOutroUsuarioNemExcluido` | OK |
| Lançamentos | B lista lançamentos, saldo e faturamento de A → vazio/zero | `DataIsolationTest.usuarioBNaoVeLancamentosNemSaldoDoUsuarioA` | OK |
| Lançamentos | B lança com categoria de A → 400 (categoria inexistente para B), nada é criado | `DataIsolationTest.usuarioBNaoLancaUsandoCategoriaDoUsuarioA` | OK |
| Categorias | B renomeia/inativa categoria de A → 404 | `DataIsolationTest.usuarioBNaoRenomeiaNemInativaCategoriaDoUsuarioA`; `CategoryControllerTest.naoDevePermitirAlterarCategoriaDeOutroUsuario` | OK |
| Categorias | B não vê categorias de A | `DataIsolationTest.listaDeCategoriasDoUsuarioBNaoTrazCategoriasDoUsuarioA`; `CategoryControllerTest.naoDeveMostrarCategoriasDeOutroUsuarioNemOsModelosPadrao` | OK |
| DAS | B paga/desfaz pagamento de guia de A → 404, guia intacta | `DataIsolationTest.usuarioBNaoPagaNemDesfazPagamentoDeGuiaDoUsuarioA`; `DasControllerTest.naoDevePermitirAlterarGuiaDeOutroUsuario` e `...DesfazerPagamentoDeOutroUsuario` | OK |
| DAS | B não vê guias de A | `DataIsolationTest.listaDeGuiasDoUsuarioBNaoTrazGuiasDoUsuarioA` | OK |
| Perfil | `/users/me` não aceita id; devolve só o autenticado, sem hash | `DataIsolationTest.perfilRetornaSempreOUsuarioAutenticado`; `ProfileControllerTest` | OK |
| Acesso do contador | B revoga/lista acesso concedido por A → 404 / lista vazia | `DataIsolationTest.usuarioBNaoRevogaNemVeAcessoConcedidoPeloUsuarioA`; `AccountantAccessControllerTest.naoDeveRevogarAcessoDeOutroMeiNemRevogarDuasVezes` | OK |
| Acesso do contador | Contador não concede/lista/revoga acessos (403); com vínculo ativo, as consultas comuns não devolvem dados do MEI | `DataIsolationTest.contadorNaoGerenciaAcessosNemEscreveNemLeDadosDoMei` | OK |
| Acesso do contador | Revogação vale imediatamente | `AccountantAccessControllerTest.revogacaoBloqueiaOAcessoDoContadorImediatamente` | OK |
| Leitura de dados do cliente pelo contador | `AccountantAccessService.hasActiveAccess` existe, mas os endpoints de consulta do contador (#33) ainda não existem; quando existirem, devem checar o vínculo e responder 404 sem ele. | Pendente: depende da #33 |

## 5. Contador somente leitura (ONF05, #84)

| Item | Evidência | Resultado |
|---|---|---|
| POST/PUT/PATCH/DELETE em transações, categorias e DAS exigem MEI ou ADMIN | `SecurityConfig` (`FINANCIAL_PATHS`); `AccountantReadOnlyAuthorizationTest` (403 `ACCESS_DENIED` em cada método e módulo) | OK |
| Gestão de acessos só para MEI | `SecurityConfig` (`/api/v1/accountant-access/**` → `hasRole("MEI")`); `AccountantAccessControllerTest.contadorNaoPodeConcederListarNemRevogarAcesso` | OK |
| MEI não é bloqueado pela regra | `AccountantReadOnlyAuthorizationTest.mei_naoEBloqueadoPelaRegraDeSomenteLeitura` | OK |
| O papel do usuário vem do token, que é assinado | `JwtAuthenticationFilter` lê `role` do JWT; adulteração é detectada pela assinatura. | OK |

## 6. Segredos não versionados

| Item | Evidência | Resultado |
|---|---|---|
| Arquivos de ambiente ignorados | `.gitignore` ignora `.env` e `.env.*`, exceto `.env.example`. `git ls-files` lista apenas `.env.example` (raiz, `backend/`, `frontend/`). | OK |
| Busca no histórico | `git log -p --all` filtrando `secret|password|jwt|api key|token`: só aparecem nomes de campos, código de teste (segredos de teste explícitos como `test-secret-key-...`) e o default de desenvolvimento do `application.yml`, que é marcado "do-not-use-in-production" e não é usado em produção. Nenhuma chave real, senha de banco real ou token. | OK |
| Valores de exemplo | `.env.example` usa `change-this-to-a-random-secret...` e `finamei/finamei` para o PostgreSQL local (docker-compose). Não são segredos de produção. | OK; atenção para não copiar o exemplo para produção (o `docs/deploy-render.md` manda gerar um segredo novo) |
| CI sem segredos no workflow | `.github/workflows/*.yml` não contêm credenciais. | OK |

## 7. Dados sensíveis em logs

| Item | Evidência | Resultado |
|---|---|---|
| O código não escreve logs de aplicação | `grep -rniE "log\.|logger|System\.out|printStackTrace" backend/src/main` não retorna nada. Logo, senha, hash e token nunca são logados pelo código. | OK |
| Níveis de log | Nenhum `logging.level.*` e nenhum `spring.jpa.show-sql` em `application.yml` / `application-prod.yml` (o padrão do Spring Boot é INFO, sem SQL nem parâmetros). Teste: `ProductionConfigurationTest.nenhumPerfilLigaLogDeSqlOuAjustaNiveisDeLog`. | OK |
| Exceções | `ApiExceptionHandler` devolve mensagens fixas e não inclui a exceção nem a pilha. Erros não tratados caem no `/error` do Spring Boot, que por padrão não inclui mensagem nem stacktrace. | OK |
| Respostas não vazam hash | `UserResponse` não tem senha/hash; verificado em `RegisterUserControllerTest`, `AccountantAccessControllerTest` e `DataIsolationTest`. | OK |
| Saúde da aplicação | `management.endpoint.health.show-details: never` em produção; só `health` e `info` expostos. | OK |
| Trilha de auditoria | `audit_logs` registra ação, tipo e id do recurso e um texto curto; não guarda senha nem token (ONF10). | OK |

## Resumo

- **Corrigido neste PR**: `server.forward-headers-strategy: native` no perfil de produção (HSTS e reconhecimento de HTTPS atrás do proxy); teste de expiração que realmente exercita a expiração; testes automatizados de configuração de produção e de acesso indevido consolidados.
- **Pendente**: Swagger público em produção; sessão absoluta de 30 min sem renovação; token não é invalidado ao desativar usuário; token em `sessionStorage` em vez de cookie `HttpOnly`; checagem de vínculo nas consultas do contador, que depende da #33.
