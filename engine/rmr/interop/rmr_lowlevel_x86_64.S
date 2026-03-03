.text
.globl rmr_lowlevel_mix_step_x86_64
.type rmr_lowlevel_mix_step_x86_64,@function
rmr_lowlevel_mix_step_x86_64:
    movl %esi, %eax
    roll $5, %eax
    xorl %edi, %eax
    addl $0x9E3779B9, %eax
    ret
.size rmr_lowlevel_mix_step_x86_64, .-rmr_lowlevel_mix_step_x86_64

.section .note.GNU-stack,"",@progbits
