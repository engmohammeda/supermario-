package dev.mariobox.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import dev.mariobox.app.ui.PadAction
import dev.mariobox.app.ui.PadLayout
import dev.mariobox.engine.Gamepad
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mariobox.engine.Cheat
import dev.mariobox.engine.CoreOption
import dev.mariobox.engine.EngineListener
import dev.mariobox.engine.EngineOptions
import dev.mariobox.engine.EngineStats
import dev.mariobox.engine.MbEvent
import dev.mariobox.engine.MbStatus
import dev.mariobox.engine.MbEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One save-state slot as the UI needs it. */
data class Slot(
    val index: Int,
    val present: Boolean,
    val bytes: Int,
    val modified: Long,
    val thumbnail: Bitmap?,
)

/**
 * Owns the engine for the game on screen.
 *
 * Rules this class exists to enforce:
 *   • the engine is only touched from [viewModelScope] on [Dispatchers.IO] for the
 *     calls that block (`load`, `release`, save-state IO), and from the UI thread for
 *     the ones the host guarantees are short (input, pause, options);
 *   • nothing is cached that the host can answer for itself -- [stats], [cheats] and
 *     [options] read through, so the UI cannot show a lie after a rewind;
 *   • the save directory is derived from the cartridge name in one place, because a
 *     save state loaded into the wrong ROM is the worst kind of silent corruption
 *     (the host's MBSV header refuses it, and this class makes that a non-event).
 */
class EmulatorViewModel(app: Application) : AndroidViewModel(app), EngineListener {
    val prefs = Prefs(app)
    val library = Library(app, prefs)
    private val engine = MbEngine()

    var cartridge by mutableStateOf<Cartridge?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var stats by mutableStateOf(EngineStats.EMPTY)
        private set
    var toast by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    private var _paused by mutableStateOf(false)
    val paused: Boolean get() = _paused
    var cheats by mutableStateOf<List<Cheat>>(emptyList())
    var options by mutableStateOf<List<CoreOption>>(emptyList())

    // ------------------------------------------------------------------ surface
    /*
     * The SurfaceView is created, resized and destroyed by the view system, and none
     * of that is synchronised with `load()`. Keeping the last surface here and
     * replaying it after a successful start is what stops the first session from a
     * cold launch being a black screen: the surface usually arrives while the core is
     * still being dlopen'd.
     */
    private var pendingSurface: Any? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    fun attachSurface(surface: Any?) {
        pendingSurface = surface
        if (!engine.active) return
        val rc = engine.setSurface(surface)
        if (surface != null && rc != MbStatus.OK) {
            toast = s(R.string.surface_failed, engine.lastError())
        }
    }

    fun resizeSurface(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        if (engine.active) engine.setSurfaceSize(width, height)
    }

    fun detachSurface() {
        pendingSurface = null
        if (engine.active) engine.setSurface(null)
    }

    private fun replaySurface() {
        if (!engine.active) return
        if (surfaceWidth > 0 && surfaceHeight > 0) engine.setSurfaceSize(surfaceWidth, surfaceHeight)
        pendingSurface?.let { engine.setSurface(it) }
    }

    // ------------------------------------------------------------------ library
    /** Copies a SAF document into the library and hands the new cartridge to [onReady]. */
    fun import(uri: Uri, onReady: (Cartridge) -> Unit = {}) {
        if (busy) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            val res = library.import(uri)
            withContext(Dispatchers.Main) {
                busy = false
                res.onSuccess { c ->
                    toast = s(R.string.library_added, c.name)
                    onReady(c)
                }.onFailure { toast = s(R.string.library_failed_copy) }
            }
        }
    }

    fun start(c: Cartridge) {
        if (busy) return
        busy = true
        viewModelScope.launch(Dispatchers.IO) {
            // Releasing the old session first matters: the host flushes SRAM on stop,
            // and switching games without that flush loses a battery save.
            if (engine.active) {
                engine.stop()
                engine.release()
            }
            val opts = EngineOptions(
                rewindSeconds = prefs.rewindSeconds,
                audioEnabled = prefs.audioEnabled,
                sramEnabled = prefs.sramEnabled,
                runAhead = prefs.runAhead,
            )
            val result = engine.load(
                context = getApplication(),
                rom = c.file,
                saveDir = prefs.saveDirFor(c.file),
                options = opts,
                listener = this@EmulatorViewModel,
            )
            withContext(Dispatchers.Main) {
                busy = false
                result.onSuccess {
                    cartridge = c
                    prefs.lastRomPath = c.path
                    engine.applyRender(prefs.renderSettings())
                    engine.setVolume(prefs.volume)
                    applyCoreDefaults()
                    applyOptionOverrides()
                    _paused = engine.isPaused
                    replaySurface()
                    if (engine.cheats().isEmpty()) restoreCheats() else reloadCheatList()
                    options = engine.options()
                    // FCEUmm seeds its option table asynchronously during the first
                    // frames; re-assert colour + overrides after it settles so the
                    // authentic palette can never be replaced by the core's internal
                    // default mid-game.
                    assertCoreOptionsAgain()
                    captureCoverArt(c)
                }.onFailure { t ->
                    error = t.message ?: s(R.string.load_failed_generic)
                }
            }
        }
    }

    fun stopGame() {
        viewModelScope.launch(Dispatchers.IO) {
            if (engine.active) {
                if (prefs.autoSaveOnExit) quickSave(silent = true)
                engine.stop()
                engine.release()
            }
            withContext(Dispatchers.Main) { cartridge = null }
        }
    }

    override fun onCleared() {
        // The process may be going away: battery RAM is the one thing a user cannot
        // re-create, so flush it synchronously rather than hoping for a coroutine.
        if (engine.active) {
            runCatching { engine.flushBattery() }
            runCatching { engine.stop() }
            runCatching { engine.release() }
        }
        super.onCleared()
    }

    // ------------------------------------------------------------------ engine plumbing
    override fun onEngineEvent(id: Int, text: String) {
        // Called on the emulation thread. Only hop when the UI actually cares.
        when (id) {
            MbEvent.MESSAGE, MbEvent.ERROR, MbEvent.GAME_INFO -> viewModelScope.launch(Dispatchers.Main) {
                if (id == MbEvent.ERROR) error = text else toast = text
            }
        }
    }

    fun setPaused(p: Boolean) {
        if (!engine.active) return
        engine.setPaused(p)
        _paused = engine.isPaused
    }

    fun togglePause() = setPaused(!paused)

    // ------------------------------------------------------------------ input
    /*
     * Touch and hardware keys keep separate masks and the engine gets their union: a
     * gamepad A-button press must not be released by lifting an on-screen A, and vice
     * versa. That is the whole reason `setButtons` is not called directly here.
     */
    private var touchMask = 0
    private var keyMask = 0
    private var turboMask = 0

    private fun pushInput() {
        if (!engine.active) return
        engine.setButtons(touchMask or keyMask)
        // The host repeats the turbo bits itself, per frame, so the UI only says
        // which buttons are on turbo and at what rate.
        engine.setTurbo(turboMask, prefs.turboHz)
    }

    /** Returns true when the key belongs to the pad, so the Activity can consume it. */
    fun handleKey(keyCode: Int, down: Boolean): Boolean {
        // L1/R1 are modifiers, not pad bits: fast forward and rewind-while-held, per
        // docs/PLAN.md §5.3.
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                if (engine.active) engine.setFastForward(down)
                return true
            }
            android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                if (down) rewind(1)
                return true
            }
            else -> Unit
        }
        val bit = Gamepad.keyToBit(keyCode) ?: return false
        keyMask = Gamepad.applyKey(keyMask, bit, down)
        pushInput()
        return true
    }

    fun overlayPress(action: PadAction, down: Boolean) {
        if (!engine.active) return
        val turbo = PadLayout.turboBitFor(action)
        if (turbo != 0) {
            turboMask = if (down) turboMask or turbo else turboMask and turbo.inv()
            pushInput()
            return
        }
        val bit = PadLayout.bitFor(action)
        if (bit == 0) return
        touchMask = Gamepad.applyKey(touchMask, bit, down)
        pushInput()
    }

    fun decodeCheat(code: String) = engine.decodeCheat(code)

    fun cheatFromCore(index: Int) = engine.cheatFromCore(index)

    fun advanceFrame() {
        if (engine.active) engine.advanceFrame()
    }

    fun setButtons(mask: Int) {
        if (engine.active) engine.setButtons(mask)
    }

    fun setTurbo(mask: Int, hz: Int = prefs.turboHz) {
        if (engine.active) engine.setTurbo(mask, hz)
    }

    fun fastForward(on: Boolean) {
        if (engine.active) engine.setFastForward(on)
    }

    fun slowMotion(on: Boolean) {
        if (engine.active) engine.setSlowMotion(on)
    }

    fun rewind(frames: Int) {
        if (!engine.active) return
        val rc = engine.rewindStep(frames)
        if (rc != MbStatus.OK) toast = s(R.string.rewind_failed, MbStatus.describe(rc, null))
    }

    fun pollStats() {
        if (!engine.active) return
        stats = engine.stats()
        _paused = engine.isPaused
    }

    fun refreshPresentation() {
        if (!engine.active) return
        engine.applyRender(prefs.renderSettings())
        engine.setVolume(prefs.volume)
    }

    /** View zoom (0.25..4): part of the cheat "features" (تقريب / إبعاد). */
    fun setZoom(z: Float) {
        prefs.zoom = z
        refreshPresentation()
    }

    fun nudgeZoom(delta: Float) = setZoom(prefs.zoom + delta)

    /**
     * Grabs a frame a few seconds into play and stores it as the cartridge's cover
     * art in the library. By then the title screen is up, which is exactly the
     * "thumbnail that shows the game" the library wants. Runs once per session and
     * never overwrites a cover if the frame is not ready yet.
     */
    private fun captureCoverArt(cart: Cartridge) {
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(4000)
            if (!engine.active) return@launch
            // Keep the first cover captured for this cartridge.
            val coverFile = library.thumbnailFor(cart)
            if (coverFile.isFile) return@launch
            val bmp = engine.frameBitmap(maxWidth = 320) ?: return@launch
            library.saveThumbnail(cart, bmp)
        }
    }

    // ------------------------------------------------------------------ palette
    /**
     * Sets the core's colour palette and keeps the choice in preferences. FCEUmm
     * re-reads `fceumm_next_palette` every frame, so this is instant.
     */
    fun setPalette(key: String, apply: Boolean = true) {
        prefs.palette = key
        if (!apply || !engine.active) return
        val rc = engine.setOption(PALETTE_OPTION, key)
        if (rc == MbStatus.OK) refreshOptions()
        else toast = s(R.string.option_failed, MbStatus.describe(rc, engine.lastError()))
    }

    private fun applyCoreDefaults() {
        if (!engine.active) return
        // The authentic colours come from the core's Nintendo RGB PPU table, which
        // is what "ألوان اللعبة الأصلية" means in the settings screen.
        engine.setOption(PALETTE_OPTION, prefs.palette)
    }

    /**
     * Re-asserts the palette (and any user overrides) a moment after the core has
     * built its option table. FCEUmm reports its defaults during the first frames
     * and can overwrite a value set too early; applying twice costs nothing and
     * makes the colour choice deterministic.
     */
    private fun assertCoreOptionsAgain() {
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(900)
            if (!engine.active) return@launch
            applyCoreDefaults()
            applyOptionOverrides()
            withContext(Dispatchers.Main) {
                if (engine.active) options = engine.options()
            }
            kotlinx.coroutines.delay(2500)
            if (!engine.active) return@launch
            applyCoreDefaults()
        }
    }

    fun flushBattery() {
        if (!engine.active) return
        viewModelScope.launch(Dispatchers.IO) {
            val rc = engine.flushBattery()
            withContext(Dispatchers.Main) {
                toast = if (rc == MbStatus.OK) {
                    s(R.string.battery_flushed, stats.sramSize)
                } else {
                    s(R.string.battery_failed, MbStatus.describe(rc, engine.lastError()))
                }
            }
        }
    }

    // ------------------------------------------------------------------ save states
    private val slotCount = 10

    private fun slotFile(index: Int): File =
        File(engine.saveDirectory() ?: prefs.savesDir, slotName(index))

    private fun slotName(index: Int) = if (index == QUICK) "quick.mbs" else "slot%02d.mbs".format(index)

    fun slots(): List<Slot> {
        val dir = engine.saveDirectory() ?: return emptyList()
        return (0 until slotCount + 1).map { i ->
            val f = File(dir, slotName(i))
            val thumb = File(dir, slotName(i).replace(".mbs", ".png"))
            Slot(
                index = i,
                present = f.isFile,
                bytes = if (f.isFile) f.length().toInt() else 0,
                modified = if (f.isFile) f.lastModified() else 0L,
                thumbnail = if (thumb.isFile) runCatching {
                    android.graphics.BitmapFactory.decodeFile(thumb.absolutePath)
                }.getOrNull() else null,
            )
        }
    }

    fun saveToSlot(index: Int) {
        if (!engine.active) return
        viewModelScope.launch(Dispatchers.IO) {
            val f = slotFile(index)
            val rc = engine.saveState(f.absolutePath)
            val bmp = engine.frameBitmap(maxWidth = 160)
            if (bmp != null) {
                // The picture goes beside the state, not inside it: the MBSV header
                // already carries a thumbnail written by the host, and this one is
                // only ever read by the UI, so a stray PNG can never corrupt a save.
                runCatching {
                    val png = File(f.absolutePath.substringBeforeLast('.') + ".png")
                    png.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            withContext(Dispatchers.Main) {
                toast = if (rc == MbStatus.OK) s(R.string.states_saved, slotName(index))
                else s(R.string.states_save_failed, MbStatus.describe(rc, engine.lastError()))
            }
        }
    }

    fun loadSlot(index: Int) {
        if (!engine.active) return
        val f = slotFile(index)
        if (!f.isFile) {
            toast = s(R.string.states_empty_slot, index + 1)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val rc = engine.loadState(f.absolutePath)
            withContext(Dispatchers.Main) {
                toast = if (rc == MbStatus.OK) s(R.string.states_loaded, slotName(index))
                else s(R.string.states_load_failed, MbStatus.describe(rc, engine.lastError()))
                _paused = engine.isPaused
            }
        }
    }

    fun quickSave(silent: Boolean = false) {
        if (!engine.active) return
        val rc = engine.saveState(slotFile(QUICK).absolutePath)
        if (!silent) toast = s(if (rc == MbStatus.OK) R.string.states_quick_saved else R.string.states_quick_failed)
    }

    fun quickLoad() {
        if (!engine.active) return
        loadSlot(QUICK)
    }

    // ------------------------------------------------------------------ cheats
    fun reloadCheatList() {
        cheats = engine.cheats()
    }

    /** Persists the list next to the saves so it survives a restart of the game. */
    private fun cheatFile(): File? = engine.saveDirectory()?.let { File(it, "cheats.txt") }

    fun addCheat(code: String, description: String): Boolean {
        if (!engine.active) return false
        val id = engine.addCheat(code.trim(), description.trim())
        if (id < 0) {
            toast = s(R.string.cheats_rejected, MbStatus.describe(-id, engine.lastError()))
            return false
        }
        reloadCheatList()
        persistCheats()
        return true
    }

    /**
     * Applies a curated cheat (one or more codes) to the running game and refreshes
     * the list. Comes from the built-in library / one-tap feature buttons.
     */
    fun applyCheatCodes(codes: List<String>, title: String, raw: Boolean = false) {
        if (!engine.active) {
            toast = s(R.string.cheats_need_game)
            return
        }
        val label = if (raw) "$title (RAM)" else title
        var added = 0
        for (code in codes) {
            if (engine.addCheat(code, label) >= 0) added++
        }
        reloadCheatList()
        if (added > 0) persistCheats()
        toast = if (added == codes.size) s(R.string.cheats_applied, title)
        else s(R.string.cheats_applied_partial, added, codes.size, title)
    }

    fun setCheatEnabled(index: Int, enabled: Boolean) {
        if (!engine.active) return
        val c = cheats.getOrNull(index) ?: return
        engine.setCheatEnabled(c.id, enabled)
        reloadCheatList()
        persistCheats()
    }

    fun removeCheat(index: Int) {
        if (!engine.active) return
        val c = cheats.getOrNull(index) ?: return
        engine.removeCheat(c.id)
        reloadCheatList()
        persistCheats()
    }

    private fun persistCheats() {
        val f = cheatFile() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val text = engine.cheats().joinToString("\n") {
                "${if (it.enabled) 1 else 0}|${it.code}|${it.description}"
            }
            runCatching { f.writeText(text) }
        }
    }

    /** Restores a game's saved cheat list after a load, without the user re-typing. */
    fun restoreCheats() {
        val f = cheatFile() ?: return
        if (!f.isFile) return
        if (engine.cheats().isNotEmpty()) return /* already live: do not double-add */
        viewModelScope.launch(Dispatchers.IO) {
            val lines = runCatching { f.readLines() }.getOrDefault(emptyList())
            for (line in lines) {
                val parts = line.split('|')
                if (parts.size < 2) continue
                engine.addCheat(parts[1], parts.getOrNull(2) ?: "", parts[0] == "1")
            }
            withContext(Dispatchers.Main) { reloadCheatList() }
        }
    }

    // ------------------------------------------------------------------ core options
    fun refreshOptions() {
        options = engine.options()
    }

    fun setOption(key: String, value: String) {
        if (!engine.active) return
        val rc = engine.setOption(key, value)
        if (rc != MbStatus.OK) {
            toast = s(R.string.option_failed, MbStatus.describe(rc, engine.lastError()))
            return
        }
        val overrides = prefs.coreOptionOverrides.toMutableMap()
        overrides[key] = value
        prefs.coreOptionOverrides = overrides
        refreshOptions()
        if (engine.needsRestart(key)) toast = s(R.string.settings_restarts)
    }

    private fun applyOptionOverrides() {
        val overrides = prefs.coreOptionOverrides
        if (overrides.isEmpty()) return
        for ((k, v) in overrides) engine.setOption(k, v)
    }

    fun powerCycle() {
        if (!engine.active) return
        viewModelScope.launch(Dispatchers.IO) {
            val rc = engine.powerCycle()
            withContext(Dispatchers.Main) {
                if (rc != MbStatus.OK) error = MbStatus.describe(rc, engine.lastError())
                applyCoreDefaults()
                applyOptionOverrides()
                // The host keeps its own cheat list across a power cycle, so only the
                // file's contents need re-adding when that list is somehow empty.
                if (engine.cheats().isEmpty()) restoreCheats() else reloadCheatList()
                options = engine.options()
            }
        }
    }

    /** ADR-0006: no user-facing literals in code, including from a ViewModel. */
    private fun s(res: Int, vararg args: Any): String =
        getApplication<Application>().getString(res, *args)

    companion object {
        /** Index of the quick save/load slot, past the 10 numbered ones. */
        const val QUICK = 10

        /** FCEUmm's palette option key. */
        const val PALETTE_OPTION = "fceumm_next_palette"
    }
}
