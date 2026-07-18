package com.example.weatherrecommender.ui.util

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A sealed class that encapsulates string content.
 * Solves the issue of resolving string resources from ViewModels without leaking [Context].
 */
sealed class UiText {
    /** A hardcoded or dynamic string, useful for backend-provided messages. */
    data class DynamicString(val value: String) : UiText()
    
    /** A string resource reference, optionally with format arguments. */
    class StringResource(
        @param:StringRes val resId: Int,
        vararg val args: Any
    ) : UiText()

    /**
     * Resolves the string value securely within a Compose context.
     */
    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args)
        }
    }

    /**
     * Resolves the string value securely given an Android [Context].
     */
    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }
}
