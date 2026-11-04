# Passe de responsividade (ONF08)

Auditoria de layout de todas as telas do frontend em telas estreitas, tablet e desktop, feita para a issue #31. O roteiro manual por navegador continua em [`roteiro-teste-navegadores.md`](roteiro-teste-navegadores.md).

## Como foi validado

- O frontend rodou com `npm run dev`, e a API foi simulada no navegador com dados "difíceis": valores na casa dos milhões, nomes e e-mails longos, descrições de lançamento com mais de 90 caracteres, 20 lançamentos por página e as 12 guias do DAS em todos os status.
- Cada tela foi aberta num Chromium (Playwright) em quatro larguras: **320 px** (equivale a 400% de zoom numa tela de 1280 px), **360 px**, **768 px** (tablet) e **1280 px** (desktop). Em 320 e 360 px, a medição foi repetida com o menu aberto.
- Em cada combinação, um script mediu:
  - a rolagem horizontal da página (`scrollWidth` do documento maior que a largura da tela);
  - os elementos que passam da borda da tela;
  - os alvos de toque (links, botões, campos) com menos de 44 × 44 px.
- As capturas de tela foram conferidas uma a uma para avaliar hierarquia e leitura.

Telas cobertas: login, cadastro, painel, lançamentos, DAS, relatórios, categorias, acesso do contador, meu perfil, meus clientes e relatórios do cliente (área do contador).

## O que foi encontrado

| Problema | Onde | Situação |
|---|---|---|
| Rolagem horizontal | Nenhuma tela, em nenhuma largura (nem em 320 px) | Sem correção necessária |
| Campos e botões com 38–42 px de altura | Todas as telas com formulário ou ações | Corrigido |
| Links do menu com 36 px de altura | Menu principal (aberto pelo botão "Menu") | Corrigido |
| Links de texto com 17 px de altura | "Criar conta", "Entrar", "← Meus clientes" | Corrigido |
| Descrição do lançamento espremida pelo valor, quebrando uma palavra por linha | Lista de lançamentos em 360 px | Corrigido |

## O que mudou

- **`src/index.css`**: em telas com menos de 640 px **ou** com ponteiro de toque (`pointer: coarse`), botões, campos e selects passam a ter no mínimo 44 px de altura. O desktop com mouse não muda. A mesma condição virou a variante do Tailwind `touch:`.
- **Links**: o menu principal, a logo, as ações do painel, o link "Ver relatórios", os links de voltar da área do contador e os links de texto do login e do cadastro usam `touch:min-h-11` com alinhamento vertical, chegando também a 44 px.
- **Lista de lançamentos**: abaixo de 640 px, o valor vai para baixo da descrição em vez de dividir a linha com ela. A partir de 640 px, o layout continua lado a lado.
- **Teste** `src/app/responsive.test.tsx`: garante que os links principais mantêm as classes de área de toque e que o lançamento empilha em telas estreitas. O jsdom não aplica media queries, por isso a parte visual depende deste passe e do roteiro manual.

## Resultado depois das correções

Nas 46 combinações de tela e largura: nenhuma rolagem horizontal, nenhum elemento passando da borda e, em 320 e 360 px, nenhum alvo de toque abaixo de 44 px.
