# bug/core/

Núcleo técnico de evidências e protótipos low-level (C/ASM/Java) associados às análises de bugs.

## Escopo
- Consolidar fontes de referência para bridge JNI, detecção de hardware e rotinas de aceleração.
- Preservar trilha entre arquivos ativos, espelhos de documentação e legado/evidência.

## Navegação
- [FILES_MAP.md](FILES_MAP.md)
- [NUCLEUS_README.md](NUCLEUS_README.md)

## Política de canonicidade (obrigatória)
- `engine/rmr` é o **único** source of truth para código de produção do RMR.
- `bug/core` é **sandbox de investigação** (PoC, reproduções, hipóteses e evidências).
- Arquivos `bug/core/*.c` com nomes iguais aos de `engine/rmr/src/*.c` **não** são linkados no build oficial e não podem receber mudanças diretas de produto.

## Checklist de promoção `bug/core` ➜ `engine/rmr/src`
- [ ] Confirmar que existe bug/requisito formal (issue, log, ou teste reprodutível).
- [ ] Fazer diff por arquivo pareado e listar mudanças por função.
- [ ] Validar compatibilidade de API/ABI em `engine/rmr/include`.
- [ ] Promover **somente** trechos válidos (sem copiar arquivo inteiro por padrão).
- [ ] Rodar build/testes do engine após promoção.
- [ ] Atualizar documentação em `engine/rmr/README.md` com o resultado da promoção.

## Estado atual dos pares canônico/rascunho
- `rmr_policy_kernel.c`: divergência estrutural completa; sandbox usa API/modelo antigo. Sem promoção automática.
- `rmr_math_fabric.c`: interfaces distintas; rascunho não compatível com o contrato atual do engine. Sem promoção automática.
- `bitraf.c`: apenas interseção parcial de símbolos; manter como laboratório até haver patch funcional isolado.
- `rmr_hw_detect.c`: pipelines distintos de detecção; manter em sandbox para pesquisa.
- `rmr_unified_kernel.c`: base funcional semelhante, porém canônico já contém extensões e validações adicionais; sem promoção em bloco.
