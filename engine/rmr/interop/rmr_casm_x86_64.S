.text
.global rmr_casm_xor_fold32_x86_64
.type rmr_casm_xor_fold32_x86_64, @function
rmr_casm_xor_fold32_x86_64:
  test %rdi, %rdi
  je .ret_zero
  test %rsi, %rsi
  je .ret_zero

  xor %eax, %eax
  xor %r9, %r9

.loop32:
  mov %rsi, %rdx
  sub %r9, %rdx
  cmp $4, %rdx
  jb .tail

  mov (%rdi,%r9,1), %edx
  xor %edx, %eax
  add $4, %r9
  jmp .loop32

.tail:
  cmp %r9, %rsi
  je .done

  movzbq (%rdi,%r9,1), %rdx
  mov %r9d, %r8d
  and $3, %r8d
  shl $3, %r8d
  mov %r8b, %cl
  shl %cl, %edx
  xor %edx, %eax
  inc %r9
  jmp .tail

.done:
  ret

.ret_zero:
  xor %eax, %eax
  ret
.size rmr_casm_xor_fold32_x86_64, .-rmr_casm_xor_fold32_x86_64

.section .note.GNU-stack,"",@progbits
