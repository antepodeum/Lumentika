package com.antepod.lumentika

import com.antepod.lumentika.components.button
import com.antepod.lumentika.components.column
import com.antepod.lumentika.components.image
import com.antepod.lumentika.components.scroll
import com.antepod.lumentika.components.text
import com.antepod.lumentika.components.textField
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.gesture.ScrollState
import com.antepod.lumentika.input.EventType
import com.antepod.lumentika.input.KeyModifiers
import com.antepod.lumentika.input.LogicalKey
import com.antepod.lumentika.input.PointerType
import com.antepod.lumentika.platform.ClipboardService
import com.antepod.lumentika.platform.TransferContent
import com.antepod.lumentika.platform.TransferItem
import com.antepod.lumentika.platform.TransferSource
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.render.PaintArtifact
import com.antepod.lumentika.render.RenderBackend
import com.antepod.lumentika.runtime.ImageService
import com.antepod.lumentika.runtime.ImageSource
import com.antepod.lumentika.runtime.PaintCommand
import com.antepod.lumentika.style.px
import com.antepod.lumentika.style.style
import com.antepod.lumentika.text.AutofillArtifact
import com.antepod.lumentika.text.AutofillChangeSet
import com.antepod.lumentika.text.AutofillConfiguration
import com.antepod.lumentika.text.AutofillHint
import com.antepod.lumentika.text.AutofillService
import com.antepod.lumentika.text.TextEditingController
import com.antepod.lumentika.text.TextInputClient
import com.antepod.lumentika.text.TextInputConfiguration
import com.antepod.lumentika.text.TextInputService
import com.antepod.lumentika.text.TextInputSession
import com.antepod.lumentika.text.TextLayoutRequest
import com.antepod.lumentika.text.TextLayoutResult
import com.antepod.lumentika.text.TextLayoutService
import com.antepod.lumentika.text.TextLine
import com.antepod.lumentika.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformInputTest {
    @Test
    fun `text field integrates clipboard receive content and committed autofill geometry`() {
        var clipboardText: String? = null
        val clipboard =
            object : ClipboardService {
                override fun readText(): String? = clipboardText

                override fun writeText(text: String) {
                    clipboardText = text
                }
            }
        var autofillArtifact: AutofillArtifact? = null
        val autofill =
            object : AutofillService {
                override fun onArtifactCommitted(
                    artifact: AutofillArtifact,
                    changes: AutofillChangeSet,
                ) {
                    autofillArtifact = artifact
                }

                override fun requestAutofill(node: com.antepod.lumentika.text.AutofillNodeId) = Unit
            }
        val root =
            UiRoot(
                UiEnvironment(Size(200f, 100f)),
                PlatformServices(
                    HeadlessFrameScheduler(),
                    clipboard = clipboard,
                    autofill = autofill,
                ),
                HeadlessRenderBackend(),
            )
        val controller = TextEditingController()
        val field =
            root.scope.textField {
                this.controller = controller
                this.autofill = AutofillConfiguration(setOf(AutofillHint.USERNAME))
            }
        root.styles.attach(
            field.element,
            com.antepod.lumentika.reactive.state(
                style {
                    width = 120.px
                    height = 24.px
                }
            ),
        )
        root.requestFrame()
        root.frame(1)

        val node = autofillArtifact!!.nodes.single()
        assertEquals(field.element.geometry, node.bounds)
        assertTrue(root.applyAutofill(node.id, "alice"))
        assertEquals("alice", controller.value.text)

        val remaining =
            root.dispatchContent(
                Point(2f, 2f),
                TransferContent(
                    listOf(
                        TransferItem("text/plain", text = "!"),
                        TransferItem("image/png", bytes = byteArrayOf(1)),
                    ),
                    TransferSource.DRAG_DROP,
                ),
            )
        assertEquals("alice!", controller.value.text)
        assertEquals(listOf("image/png"), remaining.items.map { it.mimeType })

        root.dispatchPointer(
            PointerInput(PointerInputPhase.DOWN, 1, PointerType.MOUSE, Point(2f, 2f), 2)
        )
        root.dispatchKey(
            EventType.KEY_DOWN,
            LogicalKey.CHARACTER,
            "KeyA",
            3,
            text = "a",
            modifiers = KeyModifiers(control = true),
        )
        root.dispatchKey(
            EventType.KEY_DOWN,
            LogicalKey.CHARACTER,
            "KeyC",
            4,
            text = "c",
            modifiers = KeyModifiers(control = true),
        )
        assertEquals("alice!", clipboardText)
        root.close()
        assertTrue(root.autofill.commit().first.nodes.isEmpty())
    }

    @Test
    fun `committed hit test routes normalized pointer input into controls`() {
        var clicks = 0
        val root = headlessRoot(100f, 100f)
        val button =
            root.scope.button {
                value = "Go"
                onClick { clicks++ }
            }
        root.styles.attach(
            button.element,
            com.antepod.lumentika.reactive.state(
                style {
                    width = 100.px
                    height = 40.px
                }
            ),
        )
        root.requestFrame()
        root.frame(1)

        assertSame(button.element, root.hitTest(Point(10f, 10f)))
        assertTrue(
            root.dispatchPointer(
                PointerInput(PointerInputPhase.DOWN, 1, PointerType.MOUSE, Point(10f, 10f), 2)
            )
        )
        assertTrue(
            root.dispatchPointer(
                PointerInput(PointerInputPhase.UP, 1, PointerType.MOUSE, Point(10f, 10f), 3)
            )
        )
        assertEquals(1, clicks)
        root.close()
    }

    @Test
    fun `backend can inspect same committed paint generation exposed to platform`() {
        val scheduler = HeadlessFrameScheduler()
        var generation = -1L
        val root =
            UiRoot(
                UiEnvironment(Size(20f, 20f)),
                PlatformServices(scheduler),
                object : RenderBackend {
                    override fun replay(artifact: com.antepod.lumentika.render.PaintArtifact) {
                        generation = artifact.generation
                    }
                },
            )
        root.requestFrame()
        root.frame(9)
        assertEquals(generation, root.committedRender.paint.generation)
        assertEquals(generation, root.committedRender.hitTest.generation)
        assertFalse(root.dispatchWheel(Point(30f, 30f), 0f, 1f, 10))
        root.close()
    }

    @Test
    fun `prevented platform event does not activate gesture default`() {
        var clicks = 0
        val root = headlessRoot(100f, 100f)
        val button =
            root.scope.button {
                value = "Go"
                onClick { clicks++ }
            }
        root.styles.attach(
            button.element,
            com.antepod.lumentika.reactive.state(
                style {
                    width = 100.px
                    height = 40.px
                }
            ),
        )
        root.events.on(button.element, EventType.POINTER_DOWN) { it.preventDefault() }
        root.requestFrame()
        root.frame(1)
        assertFalse(
            root.dispatchPointer(
                PointerInput(PointerInputPhase.DOWN, 1, PointerType.TOUCH, Point(5f, 5f), 2)
            )
        )
        root.dispatchPointer(
            PointerInput(PointerInputPhase.UP, 1, PointerType.TOUCH, Point(5f, 5f), 3)
        )
        assertEquals(0, clicks)
        root.close()
    }

    @Test
    fun `platform text input layout and image services flow through nested components`() {
        val layoutService = RecordingLayoutService()
        val inputService = RecordingInputService()
        var artifact: PaintArtifact? = null
        val root =
            UiRoot(
                UiEnvironment(Size(200f, 100f)),
                PlatformServices(
                    HeadlessFrameScheduler(),
                    textInput = inputService,
                    textLayout = layoutService,
                    images = ImageService { Size(23f, 17f) },
                ),
                RenderBackend { artifact = it },
            )
        val controller = TextEditingController()
        lateinit var field: com.antepod.lumentika.components.ControlHandle
        var imageSize: Size? = null
        root.scope.column {
            text("platform text")
            image(ImageSource.Uri("asset:test")).also {
                imageSize = (it.content as com.antepod.lumentika.runtime.ImageContent).intrinsicSize
            }
            field = textField { this.controller = controller }
        }
        root.styles.attach(
            field.element,
            com.antepod.lumentika.reactive.state(
                style {
                    width = 100.px
                    height = 20.px
                }
            ),
        )
        root.requestFrame()
        root.frame(1)

        assertEquals(Size(23f, 17f), imageSize)
        val textCommands =
            artifact!!.chunks.flatMap { it.commands }.filterIsInstance<PaintCommand.DrawText>()
        assertTrue(textCommands.isNotEmpty())
        assertTrue(textCommands.all { it.layout is RecordingLayoutResult })
        assertTrue(layoutService.requests.any { it.text == "platform text" })

        root.dispatchPointer(
            PointerInput(
                PointerInputPhase.DOWN,
                1,
                PointerType.MOUSE,
                Point(field.element.geometry.x + 1f, field.element.geometry.y + 1f),
                2,
            )
        )
        assertEquals(1, inputService.starts)
        root.dispatchKey(EventType.KEY_DOWN, LogicalKey.CHARACTER, "KeyA", 3, text = "a")
        assertEquals("a", controller.value.text)
        assertEquals("a", inputService.lastValue?.text)
        root.close()
        assertEquals(1, inputService.closes)
    }

    @Test
    fun `wheel scroll updates descendant transform without layout or paint recording`() {
        val root = headlessRoot(100f, 100f)
        val state = ScrollState().also { it.maxY = 200f }
        lateinit var child: com.antepod.lumentika.runtime.Element
        val scroll =
            root.scope.scroll {
                this.state = state
                child = text("content")
            }
        root.styles.attach(
            scroll,
            com.antepod.lumentika.reactive.state(
                style {
                    width = 100.px
                    height = 100.px
                }
            ),
        )
        root.requestFrame()
        root.frame(1)
        val layouts = root.layoutComputeCount
        val records = root.renderRecordCount
        val before =
            root.committedRender.hitTest.entries.single { it.element === child }.rootTransform

        assertTrue(root.dispatchWheel(Point(5f, 5f), 0f, 20f, 2))
        root.frame(3)

        val after =
            root.committedRender.hitTest.entries.single { it.element === child }.rootTransform
        assertEquals(20f, state.y)
        assertEquals(layouts, root.layoutComputeCount)
        assertEquals(records, root.renderRecordCount)
        assertEquals(before.transform(Point(0f, 0f)).y - 20f, after.transform(Point(0f, 0f)).y)
        root.close()
    }

    private class RecordingLayoutService : TextLayoutService {
        val requests = mutableListOf<TextLayoutRequest>()

        override fun layout(request: TextLayoutRequest): TextLayoutResult {
            requests += request
            return RecordingLayoutResult(request.text)
        }
    }

    private data class RecordingLayoutResult(override val text: String) : TextLayoutResult {
        override val size = Size(text.length * 7f, 18f)
        override val lines =
            listOf(TextLine(TextRange(0, text.length), 14f, Rect(0f, 0f, size.width, 18f)))

        override fun offsetForPoint(point: Point): Int =
            (point.x / 7f).toInt().coerceIn(0, text.length)

        override fun caretRect(offset: Int) = Rect(offset * 7f, 0f, 1f, 18f)

        override fun selectionRects(range: TextRange) =
            listOf(Rect(range.start * 7f, 0f, (range.end - range.start) * 7f, 18f))
    }

    private class RecordingInputService : TextInputService {
        var starts = 0
        var closes = 0
        var lastValue: com.antepod.lumentika.text.TextEditingValue? = null

        override fun start(
            configuration: TextInputConfiguration,
            client: TextInputClient,
        ): TextInputSession {
            starts++
            return object : TextInputSession {
                override fun update(value: com.antepod.lumentika.text.TextEditingValue) {
                    lastValue = value
                }

                override fun show() = Unit

                override fun hide() = Unit

                override fun close() {
                    closes++
                }
            }
        }
    }
}
