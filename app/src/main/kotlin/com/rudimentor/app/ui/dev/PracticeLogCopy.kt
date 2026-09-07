package com.rudimentor.app.ui.dev

// Leave room for clipboard metadata and other in-flight Binder transactions.
internal const val MAX_LOG_CLIPBOARD_CHARS = 200_000

internal fun copyPracticeLog(
    text: String,
    copy: (String) -> Unit,
    onError: (Exception) -> Unit,
): String {
    if (text.length > MAX_LOG_CLIPBOARD_CHARS) {
        return "Log too large to copy. Use Share for the full file."
    }
    return try {
        copy(text)
        "Summary and events copied."
    } catch (error: Exception) {
        onError(error)
        "Could not copy the log. Use Share for the full file."
    }
}
