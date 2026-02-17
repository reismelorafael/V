# Catálogo de Arquivos Raiz (Root) — Vectras-VM-Android

Este catálogo documenta os **arquivos soltos atualmente presentes no diretório raiz** do repositório. O objetivo é apoiar rastreabilidade, onboarding e auditoria documental.

---

## Escopo deste documento

- **Inclui** apenas arquivos com caminho `./<nome-do-arquivo>` (sem subdiretórios).
- **Não inclui** arquivos de `archive/`, `docs/` ou qualquer outro subdiretório no inventário principal de “arquivos raiz”.

---

## Arquivos reais na raiz (inventário atual)

## 1) `README.md`
- Documento principal de apresentação do projeto e navegação inicial.

## 2) `VECTRA_CORE.md`
- Documento técnico do Vectra Core MVP.

## 3) `LICENSE`
- Licença GPL v2.0 e termos de atribuição.

## 4) `build.gradle`
- Configuração Gradle de nível raiz (plugins, versões e propriedades globais).

## 5) `settings.gradle`
- Declara módulos incluídos no build.

## 6) `gradle.properties`
- Propriedades globais do Gradle/Android.

## 7) `gradlew`
- Wrapper Gradle para Unix-like.

## 8) `gradlew.bat`
- Wrapper Gradle para Windows.

## 9) `.gitignore`
- Regras de exclusão de artefatos e arquivos sensíveis.

## 10) `.firebaserc`
- Configuração do projeto padrão no Firebase CLI.

## 11) `PROJECT_STATE.md`
- Estado consolidado do projeto e marcos recentes.

## 12) `CHANGELOG.md`
- Histórico de mudanças por versão/período.

## 13) `DOC_INDEX.md`
- Índice de documentação do repositório.

## 14) `RELEASE_NOTES.md`
- Notas de release e highlights por entrega.

## 15) `THIRD_PARTY_NOTICES.md`
- Avisos e atribuições de componentes de terceiros.

## 16) `VECTRAS_MEGAPROMPT_DOCS.md`
- Documento de diretrizes/prompt documental do projeto.

## 17) `CMakeLists.txt`
- Configuração de build C/C++ (CMake) na raiz.

## 18) `Makefile`
- Alvos de automação/build auxiliares na raiz.

## 19) `local.properties.example`
- Exemplo de propriedades locais para configuração de ambiente.

---

## Itens históricos (fora da lista de arquivos raiz)

Os itens abaixo **não são arquivos soltos da raiz**; permanecem aqui apenas como referência histórica:

- `archive/root-history/ADVANCED_OPTIMIZATIONS.md`
- `archive/root-history/BENCHMARK_REFACTORING_SUMMARY.md`
- `archive/root-history/IMPLEMENTATION_SUMMARY.md`
- `archive/root-history/IMPLEMENTATION_COMPLETE.md`

### Nota histórica sobre assinatura
`vectras.jks` **não está presente na raiz atual**. Quando citado, deve ser tratado apenas como contexto histórico de assinatura, fora do inventário de arquivos raiz.

---

## Relação Consolidada (estado atual, não automática)

A relação abaixo descreve o encadeamento funcional de alto nível **com base em revisão manual periódica** (não é descoberta automática):

1. **Base institucional**: `README.md` e `LICENSE`.
2. **Build e configuração**: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `CMakeLists.txt`, `Makefile`, `local.properties.example`.
3. **Governança documental**: `DOC_INDEX.md`, `PROJECT_STATE.md`, `CHANGELOG.md`, `RELEASE_NOTES.md`, `THIRD_PARTY_NOTICES.md`, `VECTRAS_MEGAPROMPT_DOCS.md`, `VECTRA_CORE.md`.
4. **Integrações**: `.firebaserc` e regras operacionais em `.gitignore`.

> Importante: este catálogo é um artefato editorial; pode ficar defasado se a raiz mudar sem atualização correspondente.

---

## Regra de manutenção recomendada

Após mudanças estruturais no repositório, executar auditoria para comparar arquivos reais da raiz com o inventário documentado.

Exemplo de comando de auditoria rápida:

```bash
find . -maxdepth 1 -type f -printf '%P\n' | sort
```

Se houver divergência, atualizar este catálogo na mesma PR/commit da alteração estrutural.
