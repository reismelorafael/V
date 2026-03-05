package com.vectras.vm.setupwizard

enum class SetupProfileMode(val storageValue: String) {
    WIZARD("wizard"),
    DEBUGGER("debugger");

    companion object {
        @JvmStatic
        fun fromStorageValue(rawValue: String?): SetupProfileMode {
            return entries.firstOrNull { it.storageValue.equals(rawValue, ignoreCase = true) } ?: WIZARD
        }
    }
}
