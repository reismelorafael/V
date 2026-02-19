.text
.globl rmr_lowlevel_mix_step_x86_64
.type rmr_lowlevel_mix_step_x86_64,@function
rmr_lowlevel_mix_step_x86_64:
    movl %edi, %eax
    roll $5, %eax
    xorl %esi, %eax
    addl $0x9E3779B9, %eax
    ret
