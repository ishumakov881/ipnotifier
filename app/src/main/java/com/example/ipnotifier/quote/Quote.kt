package com.example.ipnotifier.quote

data class Quote(
    val text: String,
    val author: String?,
    val source: String? = null,
) {
    fun formatLine(): String = buildString {
        append('"')
        append(text.trim())
        append('"')
        author?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
    }
}
