package com.flue.launcher.diagnostics

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// org.json 在纯 JVM 单测里是 android.jar 的空壳，读到的是 null；借 Robolectric 换成真实实现。
@RunWith(RobolectricTestRunner::class)
class TransitionPerformanceTest {
    @Test fun delayedFramesAndStageWorkKeepTheirStartingScene() {
        val collector = TransitionPerformanceCollector(1L)
        collector.markScene(TransitionPerformance.Scene.OPEN, 10L)
        collector.markScene(TransitionPerformance.Scene.REVERSE, 100L)
        collector.endScene(TransitionPerformance.Scene.OPEN, 110L)
        collector.record(TransitionPerformance.Stage.LIST_CAPTURE, 20L, 3_000_000L)
        collector.recordFrame(20L, 20_000_000L, 16_000_000L, 2)
        collector.recordFrame(120L, 8_000_000L, 16_000_000L, 0)
        collector.recordFrame(0L, 1_000_000L, 16_000_000L, 0)
        // 用 org.json 读，别借 :agent 的 JSON 解析器——HOME 已经不依赖那个模块了。
        val scenes = JSONObject(collector.toJson()).getJSONObject("scenes")
        val open = scenes.getJSONObject("open")
        assertEquals(2L, open.getLong("dropped_callbacks"))
        assertEquals(100L, open.getLong("over_budget_percent"))
        assertEquals(1L, open.getJSONObject("stages").getJSONObject("list_capture").getLong("count"))
        assertEquals(
            1L,
            scenes.getJSONObject("reverse").getJSONObject("stages").getJSONObject("frame_total").getLong("count"),
        )
        assertEquals(
            0L,
            scenes.getJSONObject("idle").getJSONObject("stages").getJSONObject("frame_total").getLong("count"),
        )
    }

    @Test fun p95UsesAllSamplesAndResetsBetweenIntervals() {
        val accumulator = FrameIntervalAccumulator()
        repeat(95) { accumulator.record(10_000_000L, 16_000_000L, 0) }
        repeat(5) { accumulator.record(40_000_000L, 16_000_000L, 0) }
        val sample = accumulator.take(1000L)
        assertEquals(10.5, sample.p95Ms, .001)
        assertEquals(11.5, sample.averageMs, .001)
        assertEquals(5.0, sample.jankPercent, .001)
        assertEquals(0.0, accumulator.take(1000L).p95Ms, 0.0)
        accumulator.record(1_000_000_000L, 16_000_000L, 0)
        assertEquals(1000.0, accumulator.take(1000L).p95Ms, 0.0)
    }

    @Test fun disabledInstrumentationDoesNotStartTimingOrAllocateACollector() {
        TransitionPerformance.collector = null
        assertEquals(0L, TransitionPerformance.start())
        TransitionPerformance.finish(TransitionPerformance.Stage.LIST_BLUR, 0L)
        TransitionPerformance.markScene(TransitionPerformance.Scene.OPEN)
        assertNull(TransitionPerformance.collector)
    }
}
