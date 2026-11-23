package fyi.fyw.etolib.text

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Whether shown text holds what the player typed in a search box. In a pack with NeverEnoughCharacters (the GTNH pinyin
 * search, mod id nech) Chinese text also answers to its pinyin, as NEI's search does there: "tyfdj" and "tongyong"
 * both find 通用发电机. Without that mod it is a plain substring test.
 */
object TextMatch {

    /** nech's `Match.contains(String, CharSequence)`, or null without the mod. */
    private val PINYIN: MethodHandle? = pinyin()

    /** [text] and [typed] as the caller compares them (both lowered, where it ignores case). */
    fun contains(text: String, typed: String): Boolean {
        if (text.contains(typed)) return true
        val pinyin = PINYIN ?: return false
        if (typed.isEmpty()) return false
        val sought: CharSequence = typed
        return try {
            pinyin.invoke(text, sought) as Boolean
        } catch (t: Throwable) {
            false
        }
    }

    private fun pinyin(): MethodHandle? = try {
        MethodHandles.publicLookup().findStatic(
            Class.forName("com.asdflj.nech.utils.Match"),
            "contains",
            MethodType.methodType(Boolean::class.javaPrimitiveType, String::class.java, CharSequence::class.java),
        )
    } catch (e: ReflectiveOperationException) {
        null
    } catch (e: LinkageError) {
        null
    }
}
