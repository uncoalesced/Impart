// Engineered by uncoalesced
package com.uncoalesced.impart.domain.model

data class PanicDispatchReport(
    val sent: Int,
    val failed: List<String>
) {
    val allSent: Boolean get() = failed.isEmpty()
    val attempted: Int get() = sent + failed.size
}
