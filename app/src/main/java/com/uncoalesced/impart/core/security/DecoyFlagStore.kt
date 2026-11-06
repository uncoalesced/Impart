// Engineered by uncoalesced
package com.uncoalesced.impart.core.security

// The one thing the duress wipe leaves behind: a boolean that tells MainActivity to show the
// decoy and the SMS receiver to stay dark. It lives in a plain preferences file of its own,
// because the encrypted preferences, their keysets and their master key are all wiped.
interface DecoyFlagStore {
    fun isSet(): Boolean
    fun set()
}
