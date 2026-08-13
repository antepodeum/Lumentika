package com.antepod.lumentika.render

import com.antepod.lumentika.geometry.Matrix3
import com.antepod.lumentika.geometry.Point
import com.antepod.lumentika.geometry.Rect
import com.antepod.lumentika.runtime.Element
import com.antepod.lumentika.runtime.HitRegionSource
import com.antepod.lumentika.runtime.PaintCommand
import com.antepod.lumentika.runtime.PaintRecorder
import com.antepod.lumentika.runtime.SceneContent
import com.antepod.lumentika.style.PointerEvents
import com.antepod.lumentika.style.Properties
import com.antepod.lumentika.style.ResolvedStyle
import com.antepod.lumentika.style.Visibility

/** Stable index into one retained paint-property tree. */
@JvmInline public value class PropertyNodeId(public val value: Int)

/** Transform entry in a retained paint-property tree. */
public data class TransformNode(
    val id: PropertyNodeId,
    val parent: PropertyNodeId?,
    val matrix: Matrix3,
)

/** Clip entry in a retained paint-property tree. */
public data class ClipNode(val id: PropertyNodeId, val parent: PropertyNodeId?, val rect: Rect)

/** Opacity, blur, and path-draw entry in a retained property tree. */
public data class EffectNode(
    val id: PropertyNodeId,
    val parent: PropertyNodeId?,
    val opacity: Float,
    val blurRadius: Float = 0f,
    val drawLength: Float? = null,
    val drawProgress: Float = 1f,
)

/** Scroll-offset entry in a retained property tree. */
public data class ScrollNode(val id: PropertyNodeId, val parent: PropertyNodeId?, val offset: Point)

/** Stacking-context entry controlling retained paint order. */
public data class StackingContextNode(
    val id: PropertyNodeId,
    val parent: PropertyNodeId?,
    val zIndex: Int,
)

/** Property-tree references active for one paint chunk. */
public data class PaintPropertyState(
    val transform: PropertyNodeId,
    val clip: PropertyNodeId,
    val effect: PropertyNodeId,
    val scroll: PropertyNodeId,
    val stacking: PropertyNodeId,
)

/** Immutable retained transform, clip, effect, scroll, and stacking trees. */
public data class PropertyTrees(
    val transforms: List<TransformNode>,
    val clips: List<ClipNode>,
    val effects: List<EffectNode>,
    val scrolls: List<ScrollNode>,
    val stackingContexts: List<StackingContextNode>,
)

/** Ordered paint commands recorded for one element and property state. */
public data class PaintChunk(
    val element: Element,
    val properties: PaintPropertyState,
    val commands: List<PaintCommand>,
    val paintOrder: Int,
    val topLayer: Boolean,
)

/** Immutable retained output replayed by a platform [RenderBackend]. */
public data class PaintArtifact(
    val generation: Long,
    val trees: PropertyTrees,
    val chunks: List<PaintChunk>,
)

/** Committed geometry and transform data used for one hit-test candidate. */
public data class HitTestEntry(
    val element: Element,
    val localBounds: Rect,
    val rootTransform: Matrix3,
    val clip: Rect,
    val paintOrder: Int,
    val topLayer: Boolean,
    val customRegion: HitRegionSource? = null,
    val scene: SceneContent? = null,
)

/** Adapter-owned scene object selected through a core element. */
public data class SceneRaycastHit(val element: Element, val sceneObject: Any)

/** Immutable front-to-back hit-test projection for a committed frame. */
public data class HitTestArtifact(val generation: Long, val entries: List<HitTestEntry>) {
    public fun hitTest(point: Point): Element? =
        entries.asReversed().firstNotNullOfOrNull { entry ->
            if (!entry.clip.contains(point)) return@firstNotNullOfOrNull null
            val local =
                entry.rootTransform.inverse()?.transform(point) ?: return@firstNotNullOfOrNull null
            entry.element.takeIf {
                entry.customRegion?.hitTest(local, entry.localBounds)
                    ?: entry.localBounds.contains(local)
            }
        }

    public fun raycast(point: Point): SceneRaycastHit? =
        entries.asReversed().firstNotNullOfOrNull { entry ->
            if (!entry.clip.contains(point)) return@firstNotNullOfOrNull null
            val local =
                entry.rootTransform.inverse()?.transform(point) ?: return@firstNotNullOfOrNull null
            if (!(entry.customRegion?.hitTest(local, entry.localBounds) ?: false)) {
                return@firstNotNullOfOrNull null
            }
            entry.scene?.raycast(local)?.let { SceneRaycastHit(entry.element, it) }
        }
}

/** Replays a committed [PaintArtifact] through a platform graphics API. */
public fun interface RenderBackend {
    public fun replay(artifact: PaintArtifact)
}

/** Persistent render modifiers configured for an element. */
public data class RenderProperties(
    val transform: Matrix3 = Matrix3.IDENTITY,
    val clip: Rect? = null,
    val scrollOffset: Point = Point(0f, 0f),
    val topLayer: Boolean = false,
)

/** Transient visual values sampled by structural animation. */
public data class MotionRenderProperties(
    val transform: Matrix3 = Matrix3.IDENTITY,
    val opacity: Float = 1f,
    val clip: Rect? = null,
    val blurRadius: Float = 0f,
    val drawLength: Float? = null,
    val drawProgress: Float = 1f,
) {
    init {
        require(opacity.isFinite() && opacity in 0f..1f)
        require(blurRadius.isFinite() && blurRadius >= 0f)
        require(drawLength == null || drawLength.isFinite() && drawLength >= 0f)
        require(drawProgress.isFinite() && drawProgress in 0f..1f)
    }
}

/** Paint and hit-test artifacts committed from the same render generation. */
public data class RenderCommit(val paint: PaintArtifact, val hitTest: HitTestArtifact)

/** Work category invalidated by a render-tree change. */
public enum class RenderInvalidation {
    PROPERTY,
    PAINT,
    ORDER,
}

/** Builds, caches, commits, and replays retained render and hit-test artifacts. */
public class RenderRuntime(
    private val root: Element,
    private val resolveStyle: (Element) -> ResolvedStyle,
) {
    private val properties = mutableMapOf<Element, RenderProperties>()
    private val motionProperties = mutableMapOf<Element, MotionRenderProperties>()
    private val paintCache = mutableMapOf<Element, Pair<Any?, List<PaintCommand>>>()
    private val invalidations = mutableMapOf<Element, MutableSet<RenderInvalidation>>()
    private var generation = 0L
    public var recordCount: Long = 0
        private set

    public var propertyInvalidationCount: Long = 0
        private set

    public var paintInvalidationCount: Long = 0
        private set

    public var orderInvalidationCount: Long = 0
        private set

    public var committed: RenderCommit = emptyCommit()
        private set

    public fun configure(element: Element, value: RenderProperties) {
        val previous = properties[element]
        properties[element] = value
        if (
            previous == null ||
                previous.transform != value.transform ||
                previous.clip != value.clip ||
                previous.scrollOffset != value.scrollOffset
        ) {
            invalidate(element, RenderInvalidation.PROPERTY)
        }
        if (previous?.topLayer != null && previous.topLayer != value.topLayer) {
            invalidate(element, RenderInvalidation.ORDER)
        }
    }

    public fun configureMotion(element: Element, value: MotionRenderProperties?) {
        if (value == null || value == MotionRenderProperties()) motionProperties.remove(element)
        else motionProperties[element] = value
        invalidate(element, RenderInvalidation.PROPERTY)
    }

    public fun invalidate(element: Element, vararg classes: RenderInvalidation) {
        invalidations.getOrPut(element, ::mutableSetOf).addAll(classes)
    }

    public fun commit(): RenderCommit {
        invalidations.forEach { (element, classes) ->
            if (RenderInvalidation.PROPERTY in classes) propertyInvalidationCount++
            if (RenderInvalidation.PAINT in classes) {
                paintInvalidationCount++
                paintCache.remove(element)
            }
            if (RenderInvalidation.ORDER in classes) orderInvalidationCount++
        }
        invalidations.clear()
        val builder = Builder()
        walk(root, ParentState(), builder)
        generation++
        val ordered =
            builder.chunks
                .sortedWith(compareBy<PaintChunk> { !it.topLayer }.thenBy { it.paintOrder })
                .let { chunks -> chunks.filterNot { it.topLayer } + chunks.filter { it.topLayer } }
        val paint = PaintArtifact(generation, builder.trees(), ordered)
        val hit =
            HitTestArtifact(
                generation,
                builder.hitEntries
                    .sortedWith(compareBy<HitTestEntry> { !it.topLayer }.thenBy { it.paintOrder })
                    .let { entries ->
                        entries.filterNot { it.topLayer } + entries.filter { it.topLayer }
                    },
            )
        committed = RenderCommit(paint, hit)
        return committed
    }

    public fun replay(backend: RenderBackend) {
        backend.replay(committed.paint)
    }

    public fun rootToLocal(element: Element, point: Point): Point? =
        committed.hitTest.entries
            .firstOrNull { it.element === element }
            ?.rootTransform
            ?.inverse()
            ?.transform(point)

    public fun localToRoot(element: Element, point: Point): Point? =
        committed.hitTest.entries
            .firstOrNull { it.element === element }
            ?.rootTransform
            ?.transform(point)

    private fun walk(element: Element, parent: ParentState, builder: Builder) {
        val style = resolveStyle(element)
        if (
            style[Properties.Visibility] == Visibility.HIDDEN ||
                element.geometry.width <= 0f ||
                element.geometry.height <= 0f
        )
            return
        val config = properties[element] ?: RenderProperties()
        val motion = motionProperties[element] ?: MotionRenderProperties()
        val translation =
            Matrix3.translation(
                element.geometry.x - (element.parent?.geometry?.x ?: 0f),
                element.geometry.y - (element.parent?.geometry?.y ?: 0f),
            )
        val transform = parent.transform * translation * config.transform * motion.transform
        val rootBounds =
            transformedBounds(
                transform,
                Rect(0f, 0f, element.geometry.width, element.geometry.height),
            )
        val entersTopLayer = config.topLayer && !parent.topLayer
        val inheritedClip = if (entersTopLayer) ParentState.INFINITE_CLIP else parent.clip
        val localClip =
            when {
                config.clip != null && motion.clip != null -> config.clip.intersect(motion.clip)
                config.clip != null -> config.clip
                else -> motion.clip
            }
        val clip =
            (localClip?.let { transformedBounds(transform, it) } ?: inheritedClip).intersect(
                inheritedClip
            ) ?: Rect(0f, 0f, 0f, 0f)
        val transformId = builder.transform(parent.transformId, transform)
        val clipId = builder.clip(if (entersTopLayer) null else parent.clipId, clip)
        val effectId =
            builder.effect(
                parent.effectId,
                style[Properties.Opacity] * motion.opacity,
                motion.blurRadius,
                motion.drawLength,
                motion.drawProgress,
            )
        val scrollId = builder.scroll(parent.scrollId, config.scrollOffset)
        val stackId = builder.stack(parent.stackId, style[Properties.ZIndex])
        val order = builder.nextOrder++
        val topLayer = parent.topLayer || config.topLayer
        val propertyState = PaintPropertyState(transformId, clipId, effectId, scrollId, stackId)
        val content = element.content
        val paintStyle = PaintStyleKey(style.paint.background, style.inherited.color)
        if (content != null || paintStyle.background != null) {
            val key: Any? = content to paintStyle
            val cached = paintCache[element]
            val commands =
                if (cached?.first == key) cached!!.second
                else
                    mutableListOf<PaintCommand>().also { list ->
                        val bounds = Rect(0f, 0f, element.geometry.width, element.geometry.height)
                        paintStyle.background?.let { list += PaintCommand.Fill(bounds, it) }
                        content?.record(
                            object : PaintRecorder {
                                override fun record(command: PaintCommand) {
                                    list +=
                                        if (command is PaintCommand.DrawText)
                                            command.copy(paint = paintStyle.foreground)
                                        else command
                                }
                            },
                            bounds,
                        )
                        paintCache[element] = key to list
                        recordCount++
                    }
            builder.chunks += PaintChunk(element, propertyState, commands, order, topLayer)
        }
        if (style[Properties.PointerEvents] != PointerEvents.NONE)
            builder.hitEntries +=
                HitTestEntry(
                    element,
                    Rect(0f, 0f, element.geometry.width, element.geometry.height),
                    transform,
                    clip,
                    order,
                    topLayer,
                    element.content as? HitRegionSource,
                    element.content as? SceneContent,
                )
        val childTransform =
            transform * Matrix3.translation(-config.scrollOffset.x, -config.scrollOffset.y)
        val childState =
            ParentState(
                childTransform,
                clip,
                transformId,
                clipId,
                effectId,
                scrollId,
                stackId,
                topLayer,
            )
        element.children
            .sortedBy { resolveStyle(it)[Properties.ZIndex] }
            .forEach { walk(it, childState, builder) }
    }

    private fun transformedBounds(matrix: Matrix3, rect: Rect): Rect {
        val points =
            listOf(
                    Point(rect.x, rect.y),
                    Point(rect.right, rect.y),
                    Point(rect.right, rect.bottom),
                    Point(rect.x, rect.bottom),
                )
                .map(matrix::transform)
        return Rect(
            points.minOf { it.x },
            points.minOf { it.y },
            points.maxOf { it.x } - points.minOf { it.x },
            points.maxOf { it.y } - points.minOf { it.y },
        )
    }

    private data class ParentState(
        val transform: Matrix3 = Matrix3.IDENTITY,
        val clip: Rect = INFINITE_CLIP,
        val transformId: PropertyNodeId? = null,
        val clipId: PropertyNodeId? = null,
        val effectId: PropertyNodeId? = null,
        val scrollId: PropertyNodeId? = null,
        val stackId: PropertyNodeId? = null,
        val topLayer: Boolean = false,
    ) {
        companion object {
            val INFINITE_CLIP =
                Rect(
                    -Float.MAX_VALUE / 4,
                    -Float.MAX_VALUE / 4,
                    Float.MAX_VALUE / 2,
                    Float.MAX_VALUE / 2,
                )
        }
    }

    private data class PaintStyleKey(
        val background: com.antepod.lumentika.style.Paint?,
        val foreground: com.antepod.lumentika.style.Paint,
    )

    private class Builder {
        val transforms = mutableListOf<TransformNode>()
        val clips = mutableListOf<ClipNode>()
        val effects = mutableListOf<EffectNode>()
        val scrolls = mutableListOf<ScrollNode>()
        val stacks = mutableListOf<StackingContextNode>()
        val chunks = mutableListOf<PaintChunk>()
        val hitEntries = mutableListOf<HitTestEntry>()
        var nextOrder = 0

        fun transform(parent: PropertyNodeId?, value: Matrix3) =
            PropertyNodeId(transforms.size).also { transforms += TransformNode(it, parent, value) }

        fun clip(parent: PropertyNodeId?, value: Rect) =
            PropertyNodeId(clips.size).also { clips += ClipNode(it, parent, value) }

        fun effect(
            parent: PropertyNodeId?,
            opacity: Float,
            blurRadius: Float,
            drawLength: Float?,
            drawProgress: Float,
        ) =
            PropertyNodeId(effects.size).also {
                effects += EffectNode(it, parent, opacity, blurRadius, drawLength, drawProgress)
            }

        fun scroll(parent: PropertyNodeId?, value: Point) =
            PropertyNodeId(scrolls.size).also { scrolls += ScrollNode(it, parent, value) }

        fun stack(parent: PropertyNodeId?, value: Int) =
            PropertyNodeId(stacks.size).also { stacks += StackingContextNode(it, parent, value) }

        fun trees() = PropertyTrees(transforms, clips, effects, scrolls, stacks)
    }

    private companion object {
        fun emptyCommit(): RenderCommit =
            RenderCommit(
                PaintArtifact(
                    0,
                    PropertyTrees(emptyList(), emptyList(), emptyList(), emptyList(), emptyList()),
                    emptyList(),
                ),
                HitTestArtifact(0, emptyList()),
            )
    }
}
