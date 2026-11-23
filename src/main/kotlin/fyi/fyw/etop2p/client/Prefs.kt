package fyi.fyw.etop2p.client

import cpw.mods.fml.common.Loader
import java.io.File
import net.minecraftforge.common.config.Configuration

/** How the player left the screen, kept between games in `config/etop2p-client.cfg`. */
object Prefs {

    private val file = Configuration(File(Loader.instance().configDir, "etop2p-client.cfg"))

    var view: View = read("view", View.GROUPED)
        set(value) {
            field = value
            write("view", value)
        }

    var sort: Sort = read("sort", Sort.NAME)
        set(value) {
            field = value
            write("sort", value)
        }

    private inline fun <reified E : Enum<E>> read(name: String, fallback: E): E {
        val text = file.get("screen", name, fallback.name).string
        return enumValues<E>().firstOrNull { it.name == text } ?: fallback
    }

    private fun write(name: String, value: Enum<*>) {
        file.get("screen", name, value.name).set(value.name)
        file.save()
    }
}
