package dev.mariobox.app

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import dev.mariobox.engine.INesHeader
import java.io.File

/** A cartridge the app knows about: a copy in app storage plus what its header says. */
data class Cartridge(
    val file: File,
    val info: INesHeader.Info,
) {
    val name: String get() = file.nameWithoutExtension
    val path: String get() = file.absolutePath
    val sizeBytes: Long get() = file.length()
    val hasBattery: Boolean get() = info.valid && info.battery
}

/**
 * The library: ROM copies in `filesDir/roms` and their headers.
 *
 * Copies, not URIs, are what the core opens: a libretro core does its own file IO
 * (`need_fullpath`), and a SAF content URI is not a path. Copying once at import
 * also means a game still launches after the user moves or renames the download,
 * which is worth the doubled disk space for files measured in tens of kilobytes.
 * docs/PLAN.md §5.1 lists "open from source" as an escape hatch for large FDS
 * images; there is nothing to open yet, so the copy is unconditional.
 */
class Library(private val context: Context, private val prefs: Prefs) {

    val roms: List<Cartridge>
        get() = prefs.romsDir.listFiles()?.asSequence()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED }
            ?.map { Cartridge(it, header(it)) }
            ?.sortedByDescending { it.file.lastModified() }
            ?.toList()
            ?: emptyList()

    fun findByPath(path: String): Cartridge? {
        val f = File(path)
        if (!f.isFile) return null
        return Cartridge(f, header(f))
    }

    /**
     * Imports one document. Failures are returned, never thrown: a bad file in a
     * multi-select import must not lose the good ones.
     */
    fun import(uri: Uri): Result<Cartridge> = runCatching {
        val resolver = context.contentResolver
        val suggested = displayName(uri) ?: "cartridge.nes"
        val target = File(prefs.romsDir, safeFileName(suggested))
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    total += n
                }
                if (total < MIN_ROM_BYTES) {
                    throw IllegalArgumentException("file too small to be a ROM ($total bytes)")
                }
            }
        } ?: throw IllegalArgumentException("cannot open $uri")

        val info = header(target)
        if (!info.valid) {
            target.delete()
            throw IllegalArgumentException("${target.name} has no iNES header")
        }
        Cartridge(target, info)
    }

    /** Removes the copy only: progress files are kept unless [deleteSaves] says otherwise. */
    fun remove(cartridge: Cartridge, deleteSaves: Boolean = false): Boolean {
        val ok = cartridge.file.delete()
        if (ok) thumbnailFor(cartridge).delete()
        if (deleteSaves && ok) prefs.saveDirFor(cartridge.file).deleteRecursively()
        return ok
    }

    /**
     * Cover art for a cartridge: a `cover.png` next to the ROM, captured from the
     * running game. Missing files are the normal case for a freshly imported ROM.
     */
    fun thumbnailFor(cartridge: Cartridge): File =
        File(cartridge.file.parentFile, cartridge.file.nameWithoutExtension + ".cover.png")

    fun loadThumbnail(cartridge: Cartridge): android.graphics.Bitmap? {
        val f = thumbnailFor(cartridge)
        if (!f.isFile) return null
        return runCatching {
            android.graphics.BitmapFactory.decodeFile(f.absolutePath)
        }.getOrNull()
    }

    /**
     * Persists the given frame as the cartridge cover art. Called from the game
     * screen (auto, a few seconds in, and on quick-save) so every game the user
     * actually plays ends up with real artwork instead of an emoji placeholder.
     */
    fun saveThumbnail(cartridge: Cartridge, bitmap: android.graphics.Bitmap) {
        runCatching {
            thumbnailFor(cartridge).outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, out)
            }
        }
    }

    private fun header(file: File): INesHeader.Info = try {
        file.inputStream().use { input ->
            val head = ByteArray(16)
            var got = 0
            while (got < head.size) {
                val n = input.read(head, got, head.size - got)
                if (n < 0) break
                got += n
            }
            INesHeader.parse(if (got == head.size) head else head.copyOfRange(0, got.coerceAtLeast(0)))
        }
    } catch (t: Throwable) {
        INesHeader.parse(ByteArray(0))
    }

    private fun displayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else null
        } catch (t: Throwable) {
            null
        } finally {
            cursor?.close()
        }
    }

    /**
     * A file name from SAF is user data, not a path: strip separators and dots so a
     * document called `../../../../etc/hosts` cannot escape the ROMs directory.
     */
    internal fun safeFileName(suggested: String): String {
        val cleaned = buildString(suggested.length) {
            for (ch in suggested) {
                val ok = ch.isLetterOrDigit() || ch in ". _-()[]+"
                if (ok) append(ch) else append('_')
            }
        }.trim('.', ' ')
        val withExt = when {
            cleaned.isBlank() -> "cartridge.nes"
            cleaned.contains('.') -> cleaned
            else -> "$cleaned.nes"
        }
        return withExt.take(96)
    }

    companion object {
        /** Extensions our cores accept, as the ABI reports them plus common aliases. */
        val SUPPORTED = setOf("nes", "unf", "unif", "nez", "fds", "nsf")
        private const val MIN_ROM_BYTES = 16 + 1
    }
}
