package com.ssafy.eolssu.data.user

/** Public release documents; `current` follows the server's version in effect, so new versions need no app change. Opening these never sends an account token. */
enum class PublishedLegalDocument(val url: String) {
    Service("https://eolssu.site/api/terms/documents/current/service"),
    Privacy("https://eolssu.site/api/terms/documents/current/privacy"),
    Deletion("https://eolssu.site/api/terms/documents/account-deletion"),
}
