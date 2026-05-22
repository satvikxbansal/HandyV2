package com.handy.app.overlay

import com.google.common.truth.Truth.assertThat
import com.handy.core.parsing.AssistantMarkupParser
import com.handy.core.screen.GroundingSnapshot
import com.handy.core.screen.IntRect
import com.handy.core.screen.TurnSource
import com.handy.core.tool.ToolContext
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolutionSource
import com.handy.runtime.accessibility.SemanticPointerResolver.ResolvedPointTarget
import org.junit.Test

class BuddyFlightLandingGeometryTest {

    @Test
    fun `lands above target when ime consumes bottom viewport`() {
        val viewport = FlightViewport(
            bounds = IntRect(0, 0, 1080, 2400),
            insets = FlightInsets(top = 72, bottom = 820),
        )
        val target = IntRect(420, 1488, 660, 1570)

        val landing = chooseBuddyLandingPosition(
            target = target,
            widgetW = 120,
            widgetH = 120,
            density = 3f,
            viewport = viewport,
        )

        assertThat(landing.kind).startsWith("bottom-above")
        assertThat(landing.visualBounds.bottom).isAtMost(viewport.safeBounds.bottom)
        assertThat(landing.visualBounds.overlaps(target)).isFalse()
    }

    @Test
    fun `honors top display cutout inset`() {
        val viewport = FlightViewport(
            bounds = IntRect(0, 0, 1080, 2400),
            insets = FlightInsets(top = 140, bottom = 90),
        )
        val target = IntRect(440, 150, 640, 230)

        val landing = chooseBuddyLandingPosition(
            target = target,
            widgetW = 128,
            widgetH = 128,
            density = 3f,
            viewport = viewport,
        )

        assertThat(landing.kind).startsWith("top-below")
        assertThat(landing.visualBounds.top).isAtLeast(viewport.safeBounds.top)
        assertThat(landing.visualBounds.overlaps(target)).isFalse()
    }

    @Test
    fun `top right cta lands below without covering tappable bounds`() {
        val viewport = FlightViewport(
            bounds = IntRect(0, 0, 1080, 2400),
            insets = FlightInsets(top = 72, bottom = 90),
        )
        val target = IntRect(930, 140, 1060, 230)

        val landing = chooseBuddyLandingPosition(
            target = target,
            widgetW = 150,
            widgetH = 150,
            density = 3f,
            viewport = viewport,
        )

        assertThat(landing.kind).startsWith("top-below")
        assertThat(landing.visualBounds.right).isAtMost(viewport.safeBounds.right)
        assertThat(landing.visualBounds.top).isGreaterThan(target.bottom)
        assertThat(landing.visualBounds.overlaps(target)).isFalse()
    }

    @Test
    fun `avoids vertical fold hinge`() {
        val hinge = IntRect(890, 0, 910, 2400)
        val viewport = FlightViewport(
            bounds = IntRect(0, 0, 1800, 2400),
            avoidBounds = listOf(hinge),
        )
        val target = IntRect(1200, 1000, 1320, 1120)

        val landing = chooseBuddyLandingPosition(
            target = target,
            widgetW = 160,
            widgetH = 160,
            density = 2.5f,
            viewport = viewport,
        )

        assertThat(landing.visualBounds.left).isAtLeast(hinge.right)
        assertThat(landing.visualBounds.overlaps(hinge)).isFalse()
        assertThat(landing.visualBounds.overlaps(target)).isFalse()
    }

    @Test
    fun `shrinks visual footprint to fit constrained safe bounds`() {
        val viewport = FlightViewport(
            bounds = IntRect(0, 0, 300, 260),
        )
        val target = IntRect(120, 100, 180, 160)

        val landing = chooseBuddyLandingPosition(
            target = target,
            widgetW = 420,
            widgetH = 320,
            density = 2f,
            viewport = viewport,
        )

        assertThat(landing.fitScale < 1f).isTrue()
        assertThat(landing.visualBounds.left).isAtLeast(viewport.safeBounds.left)
        assertThat(landing.visualBounds.top).isAtLeast(viewport.safeBounds.top)
        assertThat(landing.visualBounds.right).isAtMost(viewport.safeBounds.right)
        assertThat(landing.visualBounds.bottom).isAtMost(viewport.safeBounds.bottom)
    }

    @Test
    fun `tap target carries root and tree hashes from grounding snapshot`() {
        val target = buildTapTargetForResolved(
            spec = AssistantMarkupParser.SemanticPoint(markId = "m3"),
            resolved = ResolvedPointTarget(
                bounds = IntRect(0, 0, 120, 80),
                node = null,
                source = ResolutionSource.MARK_ID,
                confidence = 1f,
                candidateCount = 1,
                markId = "m3",
                role = "Button",
                text = "Continue",
            ),
            groundingSnapshot = GroundingSnapshot(
                requestId = "test",
                source = TurnSource.TEST,
                toolContext = ToolContext(packageName = "com.example", appLabel = "Example"),
                rootBoundsHash = "root-hash",
                treeHash = "tree-hash",
            ),
        )

        assertThat(target.snapshotHash).isEqualTo("root-hash")
        assertThat(target.treeHash).isEqualTo("tree-hash")
    }

    private fun IntRect.overlaps(other: IntRect): Boolean =
        left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
}
