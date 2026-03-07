# Backend de Telemetria e Falhas (BLP + compatibilidade release com Firebase)

## Política oficial

A política oficial do módulo `app/` é:

- **BLP (Bitstack Local Pipeline)** é o caminho padrão para desenvolvimento local.
- **Debug não depende de Firebase** e pode compilar/rodar sem `app/google-services.json`.
- **perfRelease e release exigem configuração Firebase real**, com exceção controlada apenas para validação interna via flag Gradle.

Essa política evita ambiguidade: BLP é o padrão de desenvolvimento, mas o pipeline de release ainda protege compatibilidade de telemetria de produção.

## Matriz por variante

| Variante | Requisito de Firebase | Regra prática |
|---|---|---|
| `debug` | Opcional | Sem `google-services.json`, build local continua usando fallback sem Firebase. |
| `perfRelease` | Obrigatório (ou exceção controlada) | Falha sem JSON real; para validação interna, usar `-PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true`. |
| `release` | Obrigatório (ou exceção controlada) | Falha sem JSON real; para validação interna, usar `-PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true`. |

## Regras validadas no Gradle

A task `validateFirebaseReleaseConfig` em `app/build.gradle` aplica as seguintes regras para `perfRelease/release`:

1. Falha se `app/google-services.json` estiver ausente.
2. Falha se o JSON for inválido.
3. Falha se `project_info.project_id` estiver vazio.
4. Falha se `project_id` contiver `placeholder`.
5. Permite exceção **somente** com `-PALLOW_PLACEHOLDER_FIREBASE_FOR_RELEASE=true` (uso interno/controlado).

## CI/CD (segredo para produção)

`app/google-services.json` de produção **não deve ser versionado**. Injete via segredo (ex.: base64) no pipeline:

```bash
echo "$GOOGLE_SERVICES_JSON_B64" | base64 --decode > app/google-services.json
```

Para pipelines de release/perfRelease, a recomendação oficial é sempre usar JSON real do projeto de produção.
