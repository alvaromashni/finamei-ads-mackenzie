# Cobertura de testes do backend (ONF11)

O JaCoCo mede a cobertura dos testes do backend e o build **falha** se ela ficar abaixo do mínimo.

## Regra

Configurada em `backend/pom.xml` (`jacoco-maven-plugin`, execução `check` na fase `verify`):

| Contador | Escopo | Mínimo |
|---|---|---|
| Linhas | conjunto todo (`BUNDLE`) | 60% |
| Instruções | conjunto todo (`BUNDLE`) | 60% |

Medição na criação da regra: cerca de 98% das linhas (845 de 860), bem acima do mínimo, que funciona como proteção contra regressão.

## Exclusões

Só uma, aplicada ao relatório e à verificação:

- `br/com/finamei/FinaMeiApplication`: contém apenas o `main()` que inicia o Spring, sem lógica própria.

DTOs/records e a migration Java `V5__MakeCategoriesPerUser` **não** são excluídos: os DTOs são exercitados pelos testes de API e a migration tem teste próprio (`CategoriesPerUserMigrationTest`).

## Como ver

```bash
cd backend
./mvnw verify
# relatório HTML: backend/target/site/jacoco/index.html
```

No CI (`.github/workflows/backend-ci.yml`), o relatório HTML é publicado como artefato `jacoco-report` e o percentual total e por pacote aparece no resumo do job. Nenhum serviço externo é usado.

## Determinismo

Os testes de tempo usam `Clock` fixo (`@TestBean` ou `MutableClock`), sem `sleep` e sem rede; o banco é H2 em memória.
