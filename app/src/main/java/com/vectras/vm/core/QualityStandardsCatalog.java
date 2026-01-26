package com.vectras.vm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * QualityStandardsCatalog provides a vetted list of standards references
 * used by professional analysis reports. The list includes ISO 8000/9001,
 * RFCs, NIST publications, W3C recommendations, and additional frameworks.
 */
public final class QualityStandardsCatalog {

    private static final List<String> DEFAULT_STANDARDS;

    static {
        List<String> standards = new ArrayList<>();

        // ISO/IEC core standards
        standards.add("ISO 8000 (Data Quality)");
        standards.add("ISO 9001 (Quality Management Systems)");
        standards.add("ISO/IEC 25010 (Software Quality Model)");
        standards.add("ISO/IEC 27001 (Information Security)");
        standards.add("ISO/IEC 27002 (Security Controls)");
        standards.add("ISO/IEC 27005 (Risk Management)");
        standards.add("ISO/IEC 29148 (Requirements Engineering)");
        standards.add("ISO/IEC 12207 (Software Lifecycle)");
        standards.add("ISO/IEC 15288 (Systems Lifecycle)");
        standards.add("ISO/IEC 15504 (SPICE)");
        standards.add("ISO/IEC 20000-1 (IT Service Management)");
        standards.add("ISO 31000 (Risk Management)");
        standards.add("ISO 22301 (Business Continuity)");
        standards.add("ISO 9241-210 (Human-Centred Design)");
        standards.add("ISO/IEC 38500 (IT Governance)");

        // IEEE & ACM
        standards.add("IEEE 829 (Test Documentation)");
        standards.add("IEEE 1012 (Software Verification & Validation)");
        standards.add("IEEE 29119 (Software Testing)");
        standards.add("ACM Code of Ethics");

        // NIST publications
        standards.add("NIST SP 800-53 (Security & Privacy Controls)");
        standards.add("NIST SP 800-37 (Risk Management Framework)");
        standards.add("NIST SP 800-30 (Risk Assessment)");
        standards.add("NIST SP 800-171 (Controlled Unclassified Info)");
        standards.add("NIST SP 800-160 (Systems Security Engineering)");
        standards.add("NIST SP 800-190 (Container Security)");
        standards.add("NIST SP 800-57 (Key Management)");
        standards.add("NIST SP 800-61 (Incident Handling)");

        // RFC references
        standards.add("RFC 2119 (Key Words for RFCs)");
        standards.add("RFC 8174 (Update to RFC 2119)");
        standards.add("RFC 2616 (HTTP/1.1 - Legacy)");
        standards.add("RFC 7230 (HTTP/1.1 Message Syntax)");
        standards.add("RFC 7231 (HTTP/1.1 Semantics)");
        standards.add("RFC 7617 (HTTP Basic Auth)");
        standards.add("RFC 8446 (TLS 1.3)");
        standards.add("RFC 7519 (JWT)");
        standards.add("RFC 3986 (URI Syntax)");
        standards.add("RFC 3339 (Date/Time Format)");
        standards.add("RFC 5869 (HKDF)");
        standards.add("RFC 4122 (UUID)");
        standards.add("RFC 5280 (PKI Certificates)");

        // W3C recommendations
        standards.add("W3C HTML5 Recommendation");
        standards.add("W3C Web Content Accessibility Guidelines (WCAG) 2.2");
        standards.add("W3C CSS2.1 Recommendation");
        standards.add("W3C DOM Standard");
        standards.add("W3C WebDriver");
        standards.add("W3C WebRTC");
        standards.add("W3C WebAssembly");
        standards.add("W3C Secure Contexts");
        standards.add("W3C Content Security Policy (CSP) Level 3");

        // Additional frameworks and standards
        standards.add("OWASP ASVS");
        standards.add("OWASP Top 10");
        standards.add("CIS Controls v8");
        standards.add("CIS Benchmarks");
        standards.add("MITRE ATT&CK");
        standards.add("PCI DSS");
        standards.add("SOC 2 Trust Services Criteria");
        standards.add("GDPR");
        standards.add("LGPD");
        standards.add("HIPAA");
        standards.add("FIPS 140-3");
        standards.add("COBIT 2019");
        standards.add("ITIL 4");
        standards.add("SEI CERT Secure Coding");
        standards.add("SLSA (Supply-chain Levels for Software Artifacts)");
        standards.add("OpenSSF Scorecard");

        DEFAULT_STANDARDS = Collections.unmodifiableList(standards);
    }

    private QualityStandardsCatalog() {
    }

    public static List<String> getDefaultStandards() {
        return DEFAULT_STANDARDS;
    }
}
