package com.vectras.vm.rafaelia;

/**
 * RafaeliaQemuProfile centralizes QEMU defaults aligned with the Rafaelia fork.
 * This keeps QEMU defaults consistent and makes it easier to evolve parameters
 * with qemu_rafaelia optimizations in one place.
 */
public final class RafaeliaQemuProfile {

    private RafaeliaQemuProfile() {
    }

    public static String defaultParams(boolean is64bit, String arch) {
        if (is64bit) {
            return switch (arch) {
                case "ARM64" ->
                        "-M virt,virtualization=true -cpu cortex-a76 -accel tcg,thread=multi -net nic,model=e1000 -net user -device nec-usb-xhci -device usb-kbd -device usb-mouse -device VGA";
                case "PPC" -> "-M mac99 -cpu g4 -accel tcg,thread=multi -smp 1";
                case "I386" ->
                        "-M pc -cpu coreduo,+popcnt -accel tcg,thread=multi -smp 4 -vga std -netdev user,id=usernet -device e1000,netdev=usernet  -usb -device usb-tablet";
                default ->
                        "-M pc -cpu core2duo,+popcnt -accel tcg,thread=multi -smp 4 -vga std -netdev user,id=usernet -device e1000,netdev=usernet  -usb -device usb-tablet";
            };
        }
        return switch (arch) {
            case "ARM64" ->
                    "-M virt -cpu cortex-a76 -net nic,model=e1000 -net user -device nec-usb-xhci -device usb-kbd -device usb-mouse -device VGA";
            case "PPC" -> "-M mac99 -cpu g4 -smp 1";
            case "I386" ->
                    "-M pc -cpu coreduo,+popcnt -smp 4 -vga std -netdev user,id=usernet -device e1000,netdev=usernet  -usb -device usb-tablet";
            default ->
                    "-M pc -cpu core2duo,+popcnt -smp 4 -vga std -netdev user,id=usernet -device e1000,netdev=usernet -usb -device usb-tablet";
        };
    }
}
