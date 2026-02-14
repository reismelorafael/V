# Segurança Operacional

## Princípios
- **Fail-safe não bloqueante:** observabilidade não pode travar execução.
- **Menor privilégio:** evitar permissões legadas em Android moderno.
- **Contenção de recurso:** quotas de memória/linhas/bytes em logs.

## Mitigações implementadas
| Vetor | Mitigação |
|---|---|
| Deadlock stdout/stderr | Drenagem paralela (`ProcessOutputDrainer`) |
| DoS local por flood de logs | Rate limit + drop contabilizado + modo degradado |
| Crescimento ilimitado de memória | Ring buffer com limites rígidos |
| Processo órfão/zumbi | Supervisor com timeout + TERM/KILL escalonado |
| Storage legado inseguro | Scoped Storage + SAF em Android 10+ |

## Logging seguro
- Logs extensivos são limitados por token bucket.
- Em saturação, o sistema troca para saída resumida (`DEGRADED`) e registra auditoria.

## Storage e permissões
- Android 10+ prioriza armazenamento interno e SAF.
- Android legado mantém fallback de `WRITE_EXTERNAL_STORAGE`.

## Política de uso de keystore (`vectras.jks`)

### Classificação e ambiente permitido
- `vectras.jks` é classificado como material sensível de assinatura **release**.
- Uso permitido somente em ambiente de CI controlado e cofre de segredos; armazenamento em Git é proibido.
- Em desenvolvimento local, utilize apenas chave de debug padrão.

### Acesso mínimo (least privilege)
- Acesso somente para mantenedores autorizados de release e conta de automação do CI.
- Segredos de assinatura devem ser injetados por variáveis protegidas (`VECTRAS_SIGNING_*`) e nunca hardcoded em código/Gradle/workflows.
- Princípio de necessidade de saber: restringir leitura/exportação da chave ao menor conjunto possível.

### Rotação
- Rotação obrigatória em periodicidade definida pela equipe de segurança/compliance ou imediatamente após suspeita de exposição.
- Toda rotação deve atualizar: cofre/segredos de CI, documentação operacional e trilha de auditoria da release.

### Incident response
- Em caso de exposição/suspeita: revogar chave comprometida, gerar novo keystore de release, atualizar segredos de CI e invalidar builds assinadas pela chave comprometida quando aplicável.
- Abrir incidente de segurança, registrar timeline, impacto e ações corretivas/preventivas.
- Executar varredura completa no histórico e no estado atual do repositório para remover materiais sensíveis remanescentes.

### Controle preventivo em CI
- O pipeline executa `tools/check_sensitive_artifacts.sh` para bloquear inclusão de `*.jks`, `*.keystore`, `*.p12`, `*.pfx` e padrões de credenciais.
- Exceções só são aceitas com justificativa explícita em `security/sensitive-artifacts-allowlist.txt` e documentação associada.

