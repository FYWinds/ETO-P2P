package fyi.fyw.etolib.ui

import net.minecraft.util.StatCollector

/** Localized text for fixed interface copy, by lang key as given. */
object UiText {

    /**
     * The text of [key] in the game's language (the key itself where no language has it), with [args] filled in by
     * [String.format] when there are any.
     */
    fun tr(key: String, vararg args: Any?): String {
        val text = StatCollector.translateToLocal(key)
        return if (args.isEmpty()) text else String.format(text, *args)
    }
}
