package com.vectras.vm.core;

public final class LowLevelVector {
    private LowLevelVector() {
    }

    public static int pack16x2(int x, int y) {
        return (x & 0xFFFF) | ((y & 0xFFFF) << 16);
    }

    public static int x(int packed) {
        return (short) (packed & 0xFFFF);
    }

    public static int y(int packed) {
        return (short) ((packed >>> 16) & 0xFFFF);
    }

    public static int mix(int packedA, int packedB, int salt) {
        int a0 = x(packedA);
        int a1 = y(packedA);
        int b0 = x(packedB);
        int b1 = y(packedB);
        return LowLevelBridge.fold32(a0 + salt, a1 - salt, b0 ^ salt, b1);
    }
}
