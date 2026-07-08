package com.forgeflail.core.runner

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.LifecycleListener
import com.badlogic.gdx.Preferences
import com.badlogic.gdx.utils.Clipboard
import com.forgeflail.core.economy.EconomyManager
import com.forgeflail.core.save.SaveManager
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Headless simulation of [RunnerSystem]: the sim is pure JVM (seeded RNG, no
 * GL), so runs are driven frame-by-frame with a greedy auto-pilot. Covers the
 * power-gem boost (pickup -> faster + shielded -> fair expiry) and the
 * pause/resume/quit flow.
 */
class RunnerSystemBoostTest {

    private lateinit var economy: EconomyManager
    private lateinit var runner: RunnerSystem
    private val events = Recorder()

    private class Recorder : RunnerSystem.Listener {
        var boostsStarted = 0
        var boostsEnded = 0
        var barriersSmashed = 0
        var deaths = 0
        override fun onCoinCollected(runCoins: Int) {}
        override fun onPlayerDied(distanceMeters: Int, runCoins: Int) { deaths++ }
        override fun onLevelCompleted(level: LevelSpec) {}
        override fun onBoostStarted() { boostsStarted++ }
        override fun onBoostEnded() { boostsEnded++ }
        override fun onBarrierSmashed(lane: Int) { barriersSmashed++ }
    }

    @BeforeTest
    fun setUp() {
        Gdx.app = HeadlessApp()
        economy = EconomyManager(SaveManager())
        runner = RunnerSystem(economy, seed = 42L)
        runner.listener = events
    }

    /** One 60 fps frame with a greedy auto-pilot that steers to a safe lane. */
    private fun step(chaseGems: Boolean = true, ramIntoBarriers: Boolean = false) {
        if (runner.state == RunnerSystem.State.RUNNING) {
            val target = when {
                ramIntoBarriers -> nearestBarrierLane() ?: safeLane()
                chaseGems -> gemLane() ?: safeLane()
                else -> safeLane()
            }
            while (runner.laneIndex < target) runner.moveRight()
            while (runner.laneIndex > target) runner.moveLeft()
        }
        runner.update(1f / 60f)
    }

    private fun laneIsSafe(lane: Int): Boolean {
        for (b in runner.barriers) {
            if (b.active && b.lane == lane && b.z > -5f && b.z < 1.5f) return false
        }
        return true
    }

    private fun safeLane(): Int {
        if (laneIsSafe(runner.laneIndex)) return runner.laneIndex
        for (lane in 0..2) if (laneIsSafe(lane)) return lane
        return runner.laneIndex // spawner guarantees this can't happen
    }

    private fun gemLane(): Int? {
        for (g in runner.powerGems) {
            if (g.active && g.z > -40f && laneIsSafe(g.lane)) return g.lane
        }
        return null
    }

    private fun nearestBarrierLane(): Int? {
        var best: RunnerSystem.Entity? = null
        for (b in runner.barriers) {
            if (b.active && b.z < 0f && (best == null || b.z > best.z)) best = b
        }
        return best?.lane
    }

    @Test
    fun `power gem triggers boost, shield smashes barriers, boost ends fairly`() {
        runner.startRun(LevelCatalog.LEVELS[0])

        // Run with the auto-pilot until a power gem is collected.
        var frames = 0
        while (events.boostsStarted == 0 && frames < 60 * 120) {
            step()
            frames++
        }
        assertEquals(1, events.boostsStarted, "a power gem should spawn and be collectable")
        assertTrue(runner.isBoosted)
        assertEquals(runner.speed * RunnerSystem.BOOST_SPEED_MULTIPLIER, runner.scrollSpeed)

        // While boosted, deliberately ram every barrier: the shield must smash
        // through (no death) until the boost distance is spent.
        while (runner.isBoosted && frames < 60 * 240) {
            step(ramIntoBarriers = true)
            frames++
        }
        assertFalse(runner.isBoosted)
        assertEquals(1, events.boostsEnded)
        assertEquals(0, events.deaths, "shielded runs must never die on a barrier")
        assertTrue(events.barriersSmashed > 0, "ramming while boosted should smash barriers")
        assertEquals(runner.speed, runner.scrollSpeed)

        // Fair expiry: nothing lethal may sit closer than the clear window.
        for (b in runner.barriers) {
            assertFalse(b.active && b.z > RunnerSystem.BOOST_END_CLEAR_Z,
                "boost expiry must clear barriers the player can't react to")
        }
    }

    @Test
    fun `pause freezes the run and quit banks earnings`() {
        runner.startRun(LevelCatalog.LEVELS[0])
        repeat(60 * 5) { step(chaseGems = false) }
        assertEquals(RunnerSystem.State.RUNNING, runner.state)
        val distanceAtPause = runner.distanceMeters

        runner.pauseRun()
        assertEquals(RunnerSystem.State.PAUSED, runner.state)
        repeat(60) { runner.update(1f / 60f) } // ticks while paused are no-ops
        assertEquals(distanceAtPause, runner.distanceMeters)

        runner.resumeRun()
        assertEquals(RunnerSystem.State.RUNNING, runner.state)
        repeat(60) { step(chaseGems = false) }
        assertTrue(runner.distanceMeters > distanceAtPause)

        val goldBefore = economy.gold
        val gemsBefore = economy.gems
        val coins = runner.runCoins
        val meters = runner.distanceMeters
        runner.pauseRun()
        runner.quitRun()
        assertEquals(RunnerSystem.State.READY, runner.state)
        assertEquals(goldBefore + coins * RunnerSystem.COIN_GOLD_VALUE, economy.gold)
        assertEquals(gemsBefore + meters / RunnerSystem.METERS_PER_GEM, economy.gems)
    }

    /** Minimal Gdx.app stand-in: in-memory preferences, everything else unused. */
    private class HeadlessApp : Application {
        private val prefs = MemoryPrefs()
        override fun getApplicationListener(): ApplicationListener = throw UnsupportedOperationException()
        override fun getGraphics() = throw UnsupportedOperationException()
        override fun getAudio() = throw UnsupportedOperationException()
        override fun getInput() = throw UnsupportedOperationException()
        override fun getFiles() = throw UnsupportedOperationException()
        override fun getNet() = throw UnsupportedOperationException()
        override fun log(tag: String?, message: String?) {}
        override fun log(tag: String?, message: String?, exception: Throwable?) {}
        override fun error(tag: String?, message: String?) {}
        override fun error(tag: String?, message: String?, exception: Throwable?) {}
        override fun debug(tag: String?, message: String?) {}
        override fun debug(tag: String?, message: String?, exception: Throwable?) {}
        override fun setLogLevel(logLevel: Int) {}
        override fun getLogLevel(): Int = 0
        override fun setApplicationLogger(applicationLogger: com.badlogic.gdx.ApplicationLogger?) {}
        override fun getApplicationLogger(): com.badlogic.gdx.ApplicationLogger = throw UnsupportedOperationException()
        override fun getType(): Application.ApplicationType = Application.ApplicationType.HeadlessDesktop
        override fun getVersion(): Int = 0
        override fun getJavaHeap(): Long = 0
        override fun getNativeHeap(): Long = 0
        override fun getPreferences(name: String?): Preferences = prefs
        override fun getClipboard(): Clipboard = throw UnsupportedOperationException()
        override fun postRunnable(runnable: Runnable?) { runnable?.run() }
        override fun exit() {}
        override fun addLifecycleListener(listener: LifecycleListener?) {}
        override fun removeLifecycleListener(listener: LifecycleListener?) {}
    }

    private class MemoryPrefs : Preferences {
        private val map = HashMap<String, Any>()
        override fun putBoolean(key: String, value: Boolean): Preferences { map[key] = value; return this }
        override fun putInteger(key: String, value: Int): Preferences { map[key] = value; return this }
        override fun putLong(key: String, value: Long): Preferences { map[key] = value; return this }
        override fun putFloat(key: String, value: Float): Preferences { map[key] = value; return this }
        override fun putString(key: String, value: String): Preferences { map[key] = value; return this }
        override fun put(vals: MutableMap<String, *>): Preferences {
            for ((k, v) in vals) map[k] = v as Any
            return this
        }
        override fun getBoolean(key: String): Boolean = map[key] as? Boolean ?: false
        override fun getInteger(key: String): Int = map[key] as? Int ?: 0
        override fun getLong(key: String): Long = map[key] as? Long ?: 0L
        override fun getFloat(key: String): Float = map[key] as? Float ?: 0f
        override fun getString(key: String): String = map[key] as? String ?: ""
        override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun getInteger(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getString(key: String, defValue: String): String = map[key] as? String ?: defValue
        override fun get(): MutableMap<String, *> = HashMap(map)
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun clear() = map.clear()
        override fun remove(key: String) { map.remove(key) }
        override fun flush() {}
    }
}
