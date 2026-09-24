// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

// The documents shipped in the APK under assets/legal/, copied there at build time from the
// repository root. Bump CURRENT_VERSION when TERMS.md or PRIVACY.md changes in a way users
// should see: every install then asks for acceptance again on next launch.
enum class LegalDocument(val title: String, val assetPath: String, val format: Format) {
    TERMS("Terms of Service", "legal/TERMS.md", Format.MARKDOWN),
    PRIVACY("Privacy Policy", "legal/PRIVACY.md", Format.MARKDOWN),
    LICENSE("Open source license", "legal/LICENSE", Format.PLAIN);

    enum class Format { MARKDOWN, PLAIN }

    companion object {
        const val CURRENT_VERSION = 1
        const val CONTACT_EMAIL = "mail@uncoalesced.com"
        const val SOURCE_URL = "https://github.com/uncoalesced/Impart"
        const val ISSUES_URL = "$SOURCE_URL/issues"
    }
}

sealed interface LegalBlock {
    data class Heading(val level: Int, val text: String) : LegalBlock
    data class Paragraph(val text: String) : LegalBlock
    data class Bullet(val text: String) : LegalBlock
    data class Preformatted(val text: String) : LegalBlock
}
