package com.photoframe.studio

import com.photoframe.studio.data.EdgeRule
import com.photoframe.studio.data.FrameAdjustments
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameAdjustmentsTest {
    @Test fun pixelValuesAreClamped() {
        val result = FrameAdjustments(top = EdgeRule(-10, 50000), jpegQuality = 150).safe()
        assertEquals(0, result.top.cropPx)
        assertEquals(20000, result.top.extendPx)
        assertEquals(100, result.jpegQuality)
    }

    @Test fun proportionalValuesAreClamped() {
        val result = FrameAdjustments(left = EdgeRule(cropFraction = 1.5f, extendFraction = -0.2f)).safe()
        assertEquals(0.95f, result.left.cropFraction!!)
        assertEquals(0f, result.left.extendFraction!!)
    }
}
