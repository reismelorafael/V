# tools/apk

Scripts focados em gerar artefato APK release assinado com rastreabilidade.

## build_release_signed_local.sh

Pipeline local para:
1. validar Java/SDK e `local.properties`;
2. executar `:app:assembleRelease`;
3. verificar assinatura com `apksigner`;
4. registrar hash SHA-256 e metadados do APK.

### Uso

```bash
bash tools/apk/build_release_signed_local.sh
```

### Saídas

- APK: `app/build/outputs/apk/release/app-release.apk`
- Logs: `build/reports/apk-local/`
  - `gradle_assemble_release.log`
  - `apksigner_verify.log`
  - `apk_metadata.txt`


### Variáveis canônicas de assinatura (release)

- `VECTRAS_RELEASE_STORE_FILE`
- `VECTRAS_RELEASE_STORE_PASSWORD`
- `VECTRAS_RELEASE_KEY_ALIAS`
- `VECTRAS_RELEASE_KEY_PASSWORD`

Esses nomes são os canônicos da cadeia de build e mapeiam diretamente para as propriedades Gradle `android.injected.signing.*`.
