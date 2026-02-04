# POST_FIX_VALIDATION

Checklist de validação das correções aplicadas.

- QMP: loops de leitura limitados e parsing robusto para evitar travas em IO parcial.
- VNC: CopyRect reativado com implementação em buffers e uso no canvas.
- Config: cache/log com guards de null e senha VNC padrão removida.
- Setup: senha VNC aleatória persistida no setup.
- Manifest: exportações reduzidas para componentes não essenciais.
- Terminal: correção de estilos por coluna para wide/combining chars.
- Docs: índices e estado do projeto registrados.

Arquivos de referência:
- `app/src/main/java/com/vectras/qemu/utils/QmpClient.java`
- `app/src/main/java/android/androidVNC/FullBufferBitmapData.java`
- `app/src/main/java/android/androidVNC/LargeBitmapData.java`
- `app/src/main/java/android/androidVNC/VncCanvas.java`
- `app/src/main/java/com/vectras/qemu/Config.java`
- `app/src/main/java/com/vectras/vm/setupwizard/SetupWizard2Activity.java`
- `app/src/main/AndroidManifest.xml`
- `terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java`
- `DOC_INDEX.md`
- `PROJECT_STATE.md`
- `THIRD_PARTY_NOTICES.md`
