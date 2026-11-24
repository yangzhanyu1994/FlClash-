package com.follow.clash.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetModelTest {
    @Test
    fun `narrow widgets stay compact and a wide short widget stays thin`() {
        assertEquals(HomeWidgetSize.COMPACT, homeWidgetSize(140, 320))
        assertEquals(HomeWidgetSize.THIN, homeWidgetSize(300, 70))
        assertEquals(HomeWidgetSize.WIDE, homeWidgetSize(320, 160))
        assertEquals(HomeWidgetSize.LARGE, homeWidgetSize(360, 360))
        assertFalse(homeWidgetFields(HomeWidgetSize.COMPACT).contains("session"))
        assertFalse(homeWidgetFields(HomeWidgetSize.COMPACT).contains("ip"))
        assertFalse(homeWidgetFields(HomeWidgetSize.WIDE).contains("session"))
        assertTrue(homeWidgetFields(HomeWidgetSize.WIDE).contains("ip"))
        assertTrue(homeWidgetFields(HomeWidgetSize.LARGE).contains("session"))
        assertTrue(homeWidgetFields(HomeWidgetSize.THIN).contains("rates"))
    }

    @Test
    fun `opening the widget does not start or stop the service`() {
        assertFalse(homeWidgetStartsService(HOME_WIDGET_OPEN_ACTION))
        assertTrue(homeWidgetStartsService("com.follow.clash.action.START"))
        assertTrue(homeWidgetStartsService("com.follow.clash.action.STOP"))
        assertTrue(homeWidgetStartsService("com.follow.clash.action.TOGGLE"))
        assertTrue(
            homeWidgetStartsService("com.follow.clash.intent.action.VPN_START_REQUESTED"),
        )
        assertTrue(homeWidgetStartsService("com.follow.clash.intent.action.VPN_REVOKED"))
    }

    @Test
    fun `a stored address is not a displayed field`() {
        val snapshot = parseHomeWidgetSnapshot(
            """
            {"updatedAt":10,"run":"running","status":"Running","node":"Node A","ip":"203.0.113.10"}
            """.trimIndent(),
        )
        assertEquals("Node A", snapshot.node)
        assertFalse(snapshot.displayedText().contains("203.0.113.10"))
    }

    @Test
    fun `rates clear when the process stops publishing`() {
        val fresh = HomeWidgetSnapshot(
            updatedAt = 1_000,
            run = "running",
            status = "Running",
            node = "Node A",
            staleLabel = "Data expired",
            staleNode = "Last: Node A",
            upRate = "50.6 KB/s",
            downRate = "2.1 MB/s",
            ratesLive = true,
            blank = "—",
        )
        val quiet = presentHomeWidget(fresh, 1_000 + HOME_WIDGET_RATE_STALE_MS)
        assertEquals("—", quiet.upRate)
        assertEquals("—", quiet.downRate)
        assertFalse(quiet.ratesLive)
        assertEquals("Running", quiet.status)

        val stale = presentHomeWidget(fresh, 1_000 + HOME_WIDGET_STATE_STALE_MS)
        assertEquals("Data expired", stale.status)
        assertEquals("Last: Node A", stale.node)
        assertEquals("—", stale.upRate)

        val stopped = presentHomeWidget(fresh.copy(run = "stopped", status = "Not running"), 1_100)
        assertEquals("—", stopped.upRate)
        assertEquals("Not running", stopped.status)
        assertNull(homeWidgetRefreshDelay(stopped, 1_100))
        assertEquals(HOME_WIDGET_RATE_STALE_MS, homeWidgetRefreshDelay(fresh, 1_000))
    }

    @Test
    fun `a broken snapshot does not invent a running service`() {
        val snapshot = parseHomeWidgetSnapshot("not json")
        assertEquals("unknown", snapshot.run)
        assertEquals("—", presentHomeWidget(snapshot, 10).upRate)
    }
}
