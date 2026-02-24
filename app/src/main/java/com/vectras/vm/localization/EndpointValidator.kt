package com.vectras.vm.localization

import java.net.URL

object EndpointValidator {
    fun validateLanguageModuleEndpoint(url: String): String? {
        return try {
            val parsed = URL(url)
            val isHttps = parsed.protocol.equals("https", ignoreCase = true)
            val hasHost = parsed.host.isNotBlank()
            val hasExpectedPath = parsed.path.endsWith(".json", ignoreCase = true)
            if (isHttps && hasHost && hasExpectedPath) {
                parsed.toString()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
