# Firebase Configuration

This app uses Firebase services (Analytics, Crashlytics, Messaging).

## For Local Development

To build the app, you need a `google-services.json` file in this directory (`app/google-services.json`).

### Option 1: Use Your Own Firebase Project (Recommended for Development)

1. Create a Firebase project at https://console.firebase.google.com/
2. Add your Android app with package name `com.vectras.vm`
3. Download the `google-services.json` file
4. Place it in the `app/` directory

### Option 2: Use Placeholder (For Basic Builds Only)

If you don't need Firebase features, you can use this minimal placeholder:

```json
{
  "project_info": {
    "project_number": "000000000000",
    "project_id": "vectras-vm-placeholder",
    "storage_bucket": "vectras-vm-placeholder.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:000000000000:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.vectras.vm"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "AIzaSyDummyKeyForBuildPurposesOnly000000"
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

Save this as `app/google-services.json` to enable builds without a real Firebase project.

**Note**: Firebase features (analytics, crashlytics, messaging) will not work with the placeholder configuration.

## Produção / Ambiente Real

Ao sair do fallback `minimal placeholder`, siga estes passos objetivos:

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

## CI/CD

For CI builds, ensure the `google-services.json` file is available via:
- GitHub Secrets (base64 encoded)
- Secure file storage
- Or use the placeholder for basic compilation tests
