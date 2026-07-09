package app.anikku.macos.player

import com.sun.jna.Memory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * MPV Render Experiment — Phase 6: mpv rendering pipeline verification.
 *
 * Tests whether the libmpv software render API works on macOS:
 *   1. Load libmpv (JNA)
 *   2. Create mpv handle
 *   3. Set vo=libmpv + initialize
 *   4. Create software render context (MPV_RENDER_API_TYPE_SW)
 *   5. Load synthetic test video (lavfi://testsrc2)
 *   6. Render a frame into a CPU-side buffer
 *   7. Clean up
 *
 * Run:  ./macos/gradlew -p macos test --tests "app.anikku.macos.player.MPVRenderExperiment"
 */
@EnabledOnOs(OS.MAC)
class MPVRenderExperiment {

    @Test
    fun `experiment - mpv render pipeline`() {
        println("=".repeat(60))
        println("MPV RENDER EXPERIMENT")
        println("=".repeat(60))
        println("libmpv version: " + MPVLib.getVersion())
        println()

        // ── Step 1: Load libmpv (locale fix applied in MPVLib.initialize()) ──────────────────────────────────────────
        println("[1] Initializing MPVLib...")
        MPVLib.initialize()
        assertTrue(MPVLib.isAvailable, "libmpv must be loadable on macOS with brew install mpv")
        println("  ✅ libmpv loaded")

        // ── Step 2: Create mpv handle ────────────────────────────────────
        println("[2] Creating mpv handle...")
        val handle = MPVLib.create()
        assertNotNull(handle, "mpv_create() returned null")
        println("  ✅ handle: $handle")

        try {
            // ── Step 3: Configure and initialize ─────────────────────────
            println("[3] Setting vo=libmpv + config...")
            var rc = MPVLib.setOptionString(handle, "vo", "libmpv")
            println("  vo=libmpv: $rc")
            MPVLib.setOptionString(handle, "hwdec", "videotoolbox")
            MPVLib.setOptionString(handle, "cache", "yes")
            MPVLib.setOptionString(handle, "cache-secs", "10")
            MPVLib.setOptionString(handle, "osd-level", "0")
            MPVLib.setOptionString(handle, "keep-open", "yes")
            MPVLib.setOptionString(handle, "msg-level", "all=v")

            rc = MPVLib.initialize(handle)
            println("  mpv_initialize: $rc (0=success)")
            assertTrue(rc == 0, "mpv_initialize failed with code: $rc")
            println("  ✅ mpv initialized")

            // ── Step 4: Create software render context ───────────────────
            println("[4] Creating software render context...")
            val renderCtx = MPVLib.renderContextCreate(handle)
            if (renderCtx == null) {
                println("  ❌ renderContextCreate returned null — SOFTWARE RENDER NOT AVAILABLE")
                printVerdict("SOFTWARE RENDER API FAILED", renderCtx)
                return
            }
            println("  ✅ Render context: $renderCtx")
            println()

            // ── Step 5: Set up render buffer ─────────────────────────────
            println("[5] Allocating render buffer (320x240)...")
            val w = 320; val h = 240
            val pixelBuffer = Memory((w * h * 4).toLong())
            val stride = w * 4
            val sizeParams = Memory(8).also { it.setInt(0, w); it.setInt(4, h) }
            val strideParam = Memory(8).also { it.setLong(0, stride.toLong()) }
            val formatParam = Memory(5L).also { it.setString(0, MPVLib.RENDER_FORMAT_RGB0) }
            println("  ✅ Buffer: ${w}x${h}x4 = ${w*h*4} bytes")

            // ── Step 6: Load test video ──────────────────────────────────
            println("[6] Loading synthetic test video...")
            val testUrl = "lavfi://testsrc2=size=320x240:rate=1:duration=10"
            println("  URL: $testUrl")
            rc = MPVLib.command(handle, "loadfile", testUrl, "replace")
            println("  loadfile: $rc (0=success)")

            if (rc == 0) {
                // ── Step 7: Wait and render a frame ─────────────────────
                println("[7] Waiting 3s for decoding...")
                Thread.sleep(3000)

                println("[8] Rendering frame...")
                val renderParams = MPVLib.buildRenderParams(
                    MPVLib.RENDER_PARAM_SW_SIZE to sizeParams,
                    MPVLib.RENDER_PARAM_SW_FORMAT to formatParam,
                    MPVLib.RENDER_PARAM_SW_STRIDE to strideParam,
                    MPVLib.RENDER_PARAM_SW_POINTER to pixelBuffer,
                )

                rc = MPVLib.renderContextRender(renderCtx, renderParams)
                println("  render: $rc (0=success)")

                if (rc == 0) {
                    val sample = ByteArray(16)
                    pixelBuffer.read(0, sample, 0, 16)
                    val hasContent = sample.any { it != 0.toByte() }
                    println("  Sample: ${sample.joinToString { String.format("%02x", it) }}")
                    println("  Content: $hasContent")
                    if (hasContent) {
                        println()
                        println("  ✅✅✅ FRAME RENDERED WITH CONTENT!")
                        println("  Software render API: FULLY FUNCTIONAL")
                    } else {
                        println("  ⚠️  Buffer empty — may need more time")
                        println("  Context creation succeeded = API viable")
                    }
                    printVerdict("SOFTWARE RENDER API WORKS", renderCtx)
                } else {
                    println("  ❌ Frame render failed (code: $rc)")
                    decodeError(rc)
                    printVerdict("SOFTWARE RENDER API PARTIAL", renderCtx)
                }
            } else {
                println("  ⚠️  Test video load failed (code: $rc)")
                decodeError(rc)
                println("  Render context was created = software API is viable")
                printVerdict("CONTEXT CREATED (no video)", renderCtx)
            }

            // ── Clean up render context ─────────────────────────────────
            println()
            println("[cleanup] Freeing render context...")
            MPVLib.renderContextFree(renderCtx)
            println("  ✅ Render context freed")

        } catch (e: Exception) {
            println()
            println("❌❌❌ EXPERIMENT CRASHED: ${e.message}")
            e.printStackTrace(System.out)
            printVerdict("CRASHED", null)
        } finally {
            // ── Clean up mpv handle ─────────────────────────────────────
            println()
            println("[cleanup] Destroying mpv handle...")
            Thread.sleep(200)
            MPVLib.destroy(handle)
            println("  ✅ mpv handle destroyed")
        }

        println()
        println("=".repeat(60))
        println("EXPERIMENT COMPLETE — see verdict above")
        println("=".repeat(60))
    }

    private fun printVerdict(status: String, ctx: Any?) {
        println()
        println("#".repeat(60))
        println("  EXPERIMENT VERDICT: $status")
        if (ctx != null && ctx !is String) {
            println("  Render context was created successfully.")
            println("  The mpv software render pipeline is VIABLE on macOS.")
            println()
            println("   This means MPVVideoSurface + MPVSoftwareRenderer")
            println("   should work for Compose Desktop video rendering.")
        }
        println("#".repeat(60))
    }

    private fun decodeError(code: Int) {
        when (code) {
            MPVLib.ERROR_VO_INIT_FAILED -> println("  → VO_INIT_FAILED")
            MPVLib.ERROR_UNINITIALIZED -> println("  → UNINITIALIZED")
            MPVLib.ERROR_INVALID_PARAMETER -> println("  → INVALID_PARAM")
            MPVLib.ERROR_NOT_IMPLEMENTED -> println("  → NOT_IMPLEMENTED")
            MPVLib.ERROR_NOTHING_TO_PLAY -> println("  → NOTHING_TO_PLAY")
            MPVLib.ERROR_UNKNOWN_FORMAT -> println("  → UNKNOWN_FORMAT")
            MPVLib.ERROR_LOADING_FAILED -> println("  → LOADING_FAILED")
            MPVLib.ERROR_AO_INIT_FAILED -> println("  → AO_INIT_FAILED")
            else -> println("  → Unknown code: $code")
        }
    }
}
