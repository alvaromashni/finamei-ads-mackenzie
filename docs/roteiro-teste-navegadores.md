# Roteiro de teste nos navegadores (ONF08)

A ONF08 pede que o FinaMEI funcione **nas duas versões mais recentes do Chrome, do Firefox, do Edge e do Safari**, com layout responsivo **a partir de 360 px de largura**. Os testes automáticos (`npm run test`) rodam num navegador simulado e não substituem este roteiro: cada pessoa segue os passos no navegador de verdade e marca o resultado na tabela do final.

Tempo estimado: 20 a 30 minutos por navegador.

O resultado do passe de responsividade feito em todas as telas (360 px, tablet e desktop) está em [`responsividade.md`](responsividade.md).

## 1. Preparar o ambiente

Siga o [README](../README.md) para subir tudo na sua máquina:

```bash
docker compose up -d db          # banco
cd backend && ./mvnw spring-boot:run    # API em http://localhost:8080 (Windows: mvnw.cmd)
cd frontend && npm install && npm run dev   # site em http://localhost:5173
```

- Use uma **janela anônima/privada** em cada navegador, para começar sem sessão salva.
- Anote a **versão** do navegador (menu Ajuda → Sobre).
- O Safari só existe em aparelhos da Apple: quem tiver **Mac** testa nele do mesmo jeito que nos outros navegadores.

### Como ver em 360 px

Abra as ferramentas de desenvolvedor (F12; no Safari, ative antes em Ajustes → Avançado → "Mostrar recursos para desenvolvedores") e ligue o **modo responsivo** (Ctrl+Shift+M no Chrome/Edge/Firefox; Cmd+Opt+R no Safari). Escolha largura **360** e altura **740**.

Em cada tela, teste **duas larguras**: 360 px e a janela maximizada.

## 2. O que conferir em toda tela

- [ ] Nada fica cortado e **não aparece barra de rolagem para os lados**.
- [ ] Textos com acento aparecem certos (ç, ã, é) e os valores em reais aparecem como `R$ 1.234,56`.
- [ ] A aba do navegador mostra o nome da tela (ex.: "Painel · FinaMEI").
- [ ] Só com o **teclado**: `Tab` passa por todos os campos e botões na ordem em que aparecem, e dá para ver onde está o foco (contorno verde). `Enter` aciona botões e links.
- [ ] Ao apertar `Tab` logo que a tela abre, aparece **"Pular para o conteúdo"**.

## 3. Roteiro por tela

A coluna **Precisa de** diz o que tem que existir no servidor para o teste funcionar. Enquanto um endereço não existir, a tela mostra a mensagem de erro amigável: isso **também** é um resultado válido para conferir o layout do erro.

| # | Tela | Passos | Resultado esperado | Precisa de |
|---|---|---|---|---|
| 1 | Cadastro (`/cadastro`) | Clique em "Criar conta" sem preencher. Depois preencha com senha `abc` e confirmação diferente. Por fim, preencha tudo certo. | Mensagens em português embaixo de cada campo; depois de criar, entra direto no painel. | Já funciona |
| 2 | Login (`/login`) | Clique em "Sair". Entre com senha errada; depois com a certa. | "E-mail ou senha inválidos." e a senha é apagada; com a certa, abre o painel. | Já funciona |
| 3 | Menu | Em 360 px, toque em "Menu", escolha uma tela. Em tela grande, confira que os links aparecem sem o botão. | O menu abre, leva para a tela e fecha sozinho. | Já funciona |
| 4 | Painel (`/painel`) | Abra o painel. Clique em "+ Nova receita". | Cartões de saldo, barra de faturamento com a cor da faixa; o botão abre o formulário já em "Receita". | Resumo do mês e `/revenue/summary` |
| 5 | Lançamentos (`/lancamentos`) | Registre uma receita (valor `1.250,50`, data de hoje). Edite, depois exclua. Filtre por tipo "Despesas". | O lançamento aparece na lista; edição e exclusão pedem confirmação; o filtro mostra só despesas. | Endereços de lançamentos e categorias |
| 6 | Campo de data | Em Lançamentos e no DAS, abra o seletor de data. | O calendário do navegador abre; datas futuras não são aceitas. **Atenção especial no Safari**, que tem seletor diferente. | — |
| 7 | DAS (`/das`) | Marque um mês como pago; tente uma data futura; desfaça o pagamento. | Situação muda para "Pago"; data futura é recusada; "Desfazer" pede confirmação. | Endereços do DAS |
| 8 | Relatórios (`/relatorios`) | Troque entre Mensal e Anual; clique em "Exportar PDF" e "Exportar planilha". | O arquivo é baixado com nome do período. **Confira o download em cada navegador.** | Endereços de relatórios |
| 9 | Categorias (`/categorias`) | Crie, renomeie e inative uma categoria. | Lista atualiza; categoria inativa mostra "Inativa". | Endereços de categorias |
| 10 | Acesso do contador (`/contador`) | Libere acesso para um e-mail de contador; corte o acesso. | Aparece na lista; cortar pede confirmação. | Endereços de acesso do contador |
| 11 | Meu perfil (`/perfil`) | Altere o nome e salve; recarregue a página. | "Perfil atualizado com sucesso." e o nome continua alterado. | `/users/me` |
| 12 | Área do contador (`/clientes`) | Entre com uma conta de contador; abra os relatórios de um cliente. | Menu só com "Meus clientes" e "Meu perfil"; relatório sem botões de edição. | Conta de contador e endereços do contador |
| 13 | Sessão expirada | Com a sessão aberta, apague o item `finamei.session` em Ferramentas → Aplicação/Armazenamento → Session Storage e recarregue. | Volta para o login. | Já funciona |

## 4. Registrar o resultado

Copie a tabela abaixo para o PR ou para a issue de fechamento da Sprint 5 e preencha: ✅ funcionou, ❌ problema (descreva embaixo), ➖ não deu para testar (ex.: endereço ainda não existe).

| # | Chrome (versão ___) | Firefox (versão ___) | Edge (versão ___) | Safari (versão ___) |
|---|---|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |
| 6 | | | | |
| 7 | | | | |
| 8 | | | | |
| 9 | | | | |
| 10 | | | | |
| 11 | | | | |
| 12 | | | | |
| 13 | | | | |

### Como descrever um problema

Para cada ❌, anote: **navegador e versão**, **largura** (360 px ou tela grande), **tela e passo**, **o que aconteceu** e, se possível, um **print**. Exemplo: "Firefox 131, 360 px, Lançamentos passo 5: o botão 'Salvar alterações' fica cortado à direita."
