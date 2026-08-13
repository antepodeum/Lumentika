package com.antepod.app

import com.antepod.lumentika.HeadlessFrameScheduler
import com.antepod.lumentika.PlatformServices
import com.antepod.lumentika.UiRoot
import com.antepod.lumentika.components.*
import com.antepod.lumentika.geometry.Size
import com.antepod.lumentika.platform.UiEnvironment
import com.antepod.lumentika.reactive.state
import com.antepod.lumentika.render.RenderBackend
import com.antepod.lumentika.render.PaintArtifact

fun main() {
    val frames = HeadlessFrameScheduler()
    val artifacts = arrayOfNulls<PaintArtifact>(1)
    val root = UiRoot(UiEnvironment(Size(640f, 480f)), PlatformServices(frames), object : RenderBackend {
        override fun replay(artifact: PaintArtifact) { artifacts[0] = artifact }
    })
    val checked = state(false)
    root.scope.column {
        text("Lumentika core")
        button("Toggle") { checked.value = !checked.value }
        checkbox(checked)
        slider(state(0.5f))
        textField()
    }
    root.requestFrame()
    root.frame(1_000_000L)
    check(artifacts[0] != null)
    println("Lumentika headless frame committed: generation=${artifacts[0]!!.generation}, requests=${frames.requests}")
    root.close()
}
