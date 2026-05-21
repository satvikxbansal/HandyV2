package com.handy.core.action

/**
 * Package-level app policy for tap-for-me and native dispatch. The
 * runtime passes the per-user denylist from DataStore via
 * [userDenylistProvider]; this core type owns the static sensitive-app list.
 */
class ActionAppPolicy(
    private val userDenylistProvider: () -> Set<String> = { emptySet() },
) {
    fun isDenylisted(packageName: String?): Boolean {
        val normalized = normalize(packageName) ?: return false
        return normalized in STATIC_DENYLIST_NORMALIZED ||
            normalized in userDenylistProvider().mapNotNull(::normalize).toSet()
    }

    companion object {
        val STATIC_DENYLIST: Set<String> = setOf(
            // UPI / wallets.
            "com.google.android.apps.nbu.paisa.user",
            "net.one97.paytm",
            "com.phonepe.app",
            "in.org.npci.upiapp",
            "com.upi.axispay",
            "com.mobikwik_new",
            "com.freecharge.android",
            "com.dreamplug.androidapp",
            // Indian banking apps.
            "com.sbi.SBIFreedomPlus",
            "com.csam.icici.bank.imobile",
            "com.icicibank.imobile",
            "com.hdfcbank.hdfcquickbank",
            "com.axis.mobile",
            "com.kotak811",
            "com.idbibank.mobilebanking",
            "com.unionbank.electricity",
            "com.fss.indus",
            // Global banking examples.
            "com.bankofamerica.digitalwallet",
            "com.chase.sig.android",
            "com.wf.wellsfargomobile",
            "com.citi.citimobile",
            // Password managers.
            "com.bitwarden",
            "com.x8bit.bitwarden",
            "com.lastpass.lpandroid",
            "com.agilebits.onepassword",
            "com.onepassword.android",
            "com.dashlane",
            "com.enpass.app",
            "com.roboform.android",
            "com.google.android.apps.passwordmanager",
        )

        private val STATIC_DENYLIST_NORMALIZED: Set<String> =
            STATIC_DENYLIST.mapNotNull(::normalize).toSet()

        private fun normalize(packageName: String?): String? =
            packageName
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
    }
}
