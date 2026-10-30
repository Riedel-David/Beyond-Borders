package com.riedel.beyondborders.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Display text produced outside a composable (pure functions, ViewModels). It carries the
 * resource id and arguments instead of a resolved String, so the host tests can compare it
 * without a Context and the language is applied where the text is drawn, not where it is built.
 */
sealed interface UiText {
    data class Plain(val value: String) : UiText
    class Res(@StringRes val id: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?) = other is Res && id == other.id && args.contentEquals(other.args)
        override fun hashCode() = 31 * id + args.contentHashCode()
        override fun toString() = "Res($id, ${args.toList()})"
    }
    class Plural(@PluralsRes val id: Int, val count: Int, vararg val args: Any) : UiText {
        override fun equals(other: Any?) =
            other is Plural && id == other.id && count == other.count && args.contentEquals(other.args)
        override fun hashCode() = 31 * (31 * id + count) + args.contentHashCode()
        override fun toString() = "Plural($id, $count, ${args.toList()})"
    }

    fun asString(context: Context): String = when (this) {
        is Plain -> value
        is Res -> context.getString(id, *args.map { it.resolve(context) }.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.map { it.resolve(context) }.toTypedArray())
    }
}

// A UiText nested as an argument ("3 of {Europe}") is resolved too.
private fun Any.resolve(context: Context): Any = if (this is UiText) asString(context) else this

@Composable
fun UiText.asString(): String = asString(LocalContext.current)
