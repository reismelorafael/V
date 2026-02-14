# Vectras VM – Legal, Licensing, and Compliance Guide

> **Nota / Note**: Este documento é informativo e não constitui aconselhamento jurídico. Para decisões legais, consulte assessoria especializada.

## Overview / Visão Geral

Este guia consolida as diretrizes de licenciamento, atribuição autoral, uso de marcas e conformidade para a documentação e o software do **Vectras VM**. Ele deve ser consultado antes de redistribuição, publicação acadêmica ou uso comercial.

## ✅ Quick Links / Links Rápidos

- **Licença principal**: [LICENSE](../LICENSE)
- **Aviso de atribuição**: [README.md](../README.md)
- **Guia de contribuição**: [docs/CONTRIBUTING.md](CONTRIBUTING.md)
- **Configuração Firebase / dados**: [app/FIREBASE.md](../app/FIREBASE.md)

---

## 1) Licença Principal / Primary License

- O projeto é distribuído sob **GNU GPL v2.0** (ver [LICENSE](../LICENSE)).
- Qualquer trabalho derivado deve manter a mesma licença e preservar atribuições obrigatórias.
- Ao redistribuir binários, o **código-fonte correspondente** deve ser disponibilizado conforme a GPL v2.0.

## 2) Direitos Autorais e Atribuição / Copyright & Attribution

- O repositório contém aviso explícito de atribuição ao projeto original e ao autor principal no [README.md](../README.md).
- Toda documentação deve manter:
  - **Créditos ao(s) autor(es) original(is)**.
  - **Referência ao repositório oficial**.
  - **Indicação clara de modificações** quando aplicável.

### Padrão recomendado de atribuição

> "Este trabalho deriva de Vectras VM (GPL v2.0). Alterações e melhorias foram realizadas por [seu nome/organização]."

## 3) Componentes de Terceiros / Third-Party Components

A aplicação utiliza dependências externas (por exemplo, bibliotecas Android/Gradle e serviços como Firebase). Para conformidade:

- Mantenha um inventário atualizado de dependências (Gradle/AndroidX, QEMU, bibliotecas nativas).
- Verifique as licenças de cada dependência e seus requisitos de atribuição.
- Caso seja necessário, publique um **THIRD_PARTY_NOTICES.md** com a lista de componentes e licenças.

> **Referências úteis**: arquivos `build.gradle`, `settings.gradle`, pastas `app/` e `gradle/`.

## 4) Marcas e Identidade Visual / Trademarks & Branding

- O nome **Vectras VM** e o logotipo são identificadores de marca do projeto.
- Não declare endosso oficial a menos que tenha autorização explícita dos mantenedores.
- Ao usar a marca em materiais públicos, cite a origem e preserve a atribuição.

## 5) Proteção de Dados e Privacidade / Data Protection & Privacy

Se o aplicativo ou serviços associados processarem dados pessoais:

- Defina uma **Política de Privacidade** clara e acessível.
- Verifique conformidade com **LGPD (Brasil)**, **GDPR (UE)** e leis locais aplicáveis.
- Documente o uso de serviços de terceiros (ex.: Firebase) e o tipo de dados coletados.
- Evite registrar dados pessoais em logs e relatórios públicos.

## 6) Publicação Acadêmica e Normas Técnicas

Para citações e publicações acadêmicas:

- Utilize as referências recomendadas em [docs/README.md](README.md) e [docs/BIBLIOGRAPHY.md](BIBLIOGRAPHY.md).
- Apresente claramente **versão**, **ano** e **URL** do repositório.
- Indique o tipo de licença (GPL-2.0) em materiais derivados.

## 7) Conformidade Operacional / Operational Compliance

Checklist recomendado antes de release:

- [ ] Licenças conferidas e compatíveis.
- [ ] Avisos de copyright e atribuição preservados.
- [ ] Política de privacidade disponível (se aplicável).
- [ ] Dependências externas revisadas e documentadas.
- [ ] Mudanças registradas no changelog/documentos.
- [ ] Keystores/credenciais fora do Git; segredos de release mantidos em cofre de CI.
- [ ] Política de rotação e resposta a incidente de chave validada para a release.

---

## Alterações e Controle de Versão

Este documento segue as normas definidas em [docs/DOCUMENTATION_STANDARDS.md](DOCUMENTATION_STANDARDS.md).

**Última atualização / Last updated**: 2026-01

---

© 2024-2026 Vectras VM Development Team — Licensed under GPL-2.0.


## 8) Política Legal de Chaves de Assinatura

- O keystore `vectras.jks` é tratado como credencial sensível de **assinatura release** e não deve ser distribuído em repositório público.
- A custódia deve ocorrer em cofre seguro/segredos de CI, com controle de acesso mínimo e rastreabilidade de uso.
- Rotação periódica e por incidente é mandatória para reduzir risco de comprometimento de cadeia de supply chain.
- Exceções para arquivos sensíveis devem ser formalmente documentadas e aprovadas antes de versionamento.
