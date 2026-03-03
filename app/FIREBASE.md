# Backend de Telemetria e Falhas (substituto do Firebase)

## Decisão arquitetural

O módulo `app/` **não usa mais Firebase** (Analytics, Crashlytics, Messaging).
A estratégia oficial passa a ser um pipeline autoral local, chamado **Bitstack Local Pipeline (BLP)**,
com coleta determinística no dispositivo e exportação controlada via artefatos locais.

Para build local, o arquivo `app/google-services.json` é opcional em **debug** (fallback explícito sem Firebase) e obrigatório em variantes **release**.

- Evitar acoplamento com serviços externos para build/execução.
- Permitir operação offline e previsível.
- Manter trilha técnica para diagnóstico usando componentes já existentes no projeto.

## Como funciona

### Option 2: Use Placeholder (Apenas para debug/local)

> Referências de arquitetura operacional e auditoria: `docs/ARCHITECTURE.md`.

## Impacto no build

Salve como `app/google-services.json` para builds locais/debug sem um projeto Firebase real.

## Migração (Firebase → BLP)

- Remover procedimentos de provisionamento Firebase dos guias de onboarding.
- Manter apenas fluxo de build Android/Gradle padrão.
- Centralizar rastreabilidade em documentação de arquitetura e operação local.

## Checklist rápido

1. Substitua `project_id` e `storage_bucket` do exemplo por valores reais do seu projeto Firebase (não use `vectras-vm-placeholder`).
2. Use o `google-services.json` real baixado do Firebase Console para o app Android correto.

### Exemplo real (sem `*-placeholder`)

```json
{
  "project_info": {
    "project_number": "123456789012",
    "project_id": "vectras-vm-prod",
    "storage_bucket": "vectras-vm-prod.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:123456789012:android:abcdef1234567890abcd12",
        "android_client_info": {
          "package_name": "com.vectras.vm"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "AIzaSyRealProjectKeyExample123456789"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
```

### Checklist de validação

- [ ] Arquivo presente em `app/google-services.json`.
- [ ] `package_name` do JSON compatível com o package da aplicação (`com.vectras.vm`).
- [ ] Executar Sync Gradle após substituir o arquivo.

### Aviso de risco funcional

Se `vectras-vm-placeholder` ou qualquer placeholder for mantido, Analytics, Crashlytics e Messaging ficam inoperantes.


## CI/CD (segredo obrigatório para produção)

`app/google-services.json` de produção **não deve ser versionado**. O pipeline deve injetar o arquivo via segredo (base64), por exemplo:

```bash
echo "$GOOGLE_SERVICES_JSON_B64" | base64 --decode > app/google-services.json
```

Regras do pipeline:

- Release/perfRelease falham se `project_id` contiver `placeholder`.
- Debug pode usar fallback placeholder para compilar sem Firebase ativo.
- Em CI de release, use sempre o JSON real do Firebase via secret.
