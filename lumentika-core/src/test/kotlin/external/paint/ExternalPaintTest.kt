package external.paint

import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.PaintArtifact
import com.antepod.lumentika.render.RenderBackend
import com.antepod.lumentika.render.RenderRuntime
import com.antepod.lumentika.runtime.BackendPaintCommand
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.PaintCommand
import com.antepod.lumentika.style.Paint
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.StyleRuntime
import com.antepod.lumentika.style.style
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

private data class ExternalCommand(val key: String, val bounds: Rect) : BackendPaintCommand

private data class ExternalPaint(val key: String) : Paint {
    override fun backendCommand(bounds: Rect): BackendPaintCommand = ExternalCommand(key, bounds)
}

class ExternalPaintTest {
    @Test
    fun `external paint resolves records and replays backend command`() {
        val paint = ExternalPaint("external")
        val root = Element("external").apply { geometry = Rect(0f, 0f, 20f, 10f) }
        val styles = StyleRuntime()
        styles.attach(root, state(style { background = paint }))
        assertSame(paint, styles.resolve(root).first[Properties.Background])

        val runtime = RenderRuntime(root) { styles.resolve(it).first }
        val artifact = runtime.commit().paint
        val command =
            artifact.chunks.single().commands.filterIsInstance<PaintCommand.Backend>().single()
        assertEquals(ExternalCommand("external", Rect(0f, 0f, 20f, 10f)), command.extension)

        var received: ExternalCommand? = null
        runtime.replay(
            object : RenderBackend {
                override fun replay(artifact: PaintArtifact) {
                    received =
                        artifact.chunks
                            .flatMap { it.commands }
                            .filterIsInstance<PaintCommand.Backend>()
                            .map { it.extension }
                            .filterIsInstance<ExternalCommand>()
                            .single()
                }
            }
        )
        assertEquals("external", received?.key)
        root.close()
    }
}
