package com.follow.clash.widget

import com.follow.clash.RunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWidgetModelTest {
    @Test
    fun `connected is only the started service`() {
        val connected = RunState.entries.filter {
            widgetPresentation(it).status == WidgetStatus.CONNECTED
        }

        assertEquals(listOf(RunState.STARTED), connected)
        assertEquals(WidgetTone.ACTIVE, widgetPresentation(RunState.STARTED).tone)
        assertEquals(WidgetAction.STOP, widgetClickAction(widgetPresentation(RunState.STARTED)))
    }

    @Test
    fun `stopped offers start and a transition offers no click`() {
        val stopped = widgetPresentation(RunState.STOPPED)
        assertEquals(WidgetStatus.STOPPED, stopped.status)
        assertEquals(WidgetTone.NEUTRAL, stopped.tone)
        assertEquals(WidgetAction.START, widgetClickAction(stopped))

        val starting = widgetPresentation(RunState.STARTING)
        assertEquals(WidgetStatus.STARTING, starting.status)
        assertEquals(WidgetAction.START, starting.button)
        assertFalse(starting.buttonEnabled)
        assertNull(widgetClickAction(starting))

        val stopping = widgetPresentation(RunState.STOPPING)
        assertEquals(WidgetStatus.STOPPING, stopping.status)
        assertEquals(WidgetAction.STOP, stopping.button)
        assertNull(widgetClickAction(stopping))
    }

    @Test
    fun `appearance reads locale and theme and drops everything else`() {
        val secret = "https://provider.example/sub?token=secret"
        val appearance = widgetAppearance(
            """
            {
              "appSettingProps": {"locale": "zh_CN"},
              "themeProps": {"themeMode": "dark", "primaryColor": 255},
              "currentProfileId": 7,
              "profiles": [{"label": "airport", "url": "$secret"}]
            }
            """.trimIndent(),
        )

        assertEquals(WidgetAppearance(languageTag = "zh-CN", nightMode = WidgetNightMode.DARK), appearance)
        assertFalse(appearance.toString().contains(secret))
        assertFalse(appearance.toString().contains("airport"))
    }

    @Test
    fun `missing or broken config follows the system`() {
        assertEquals(WidgetAppearance(), widgetAppearance(null))
        assertEquals(WidgetAppearance(), widgetAppearance(""))
        assertEquals(WidgetAppearance(), widgetAppearance("{"))
        assertEquals(WidgetAppearance(), widgetAppearance("null"))
        assertEquals(
            WidgetAppearance(languageTag = "ja"),
            widgetAppearance("""{"appSettingProps":{"locale":"ja"}}"""),
        )
        assertEquals(
            WidgetAppearance(nightMode = WidgetNightMode.LIGHT),
            widgetAppearance("""{"themeProps":{"themeMode":"light"}}"""),
        )
        assertEquals(
            WidgetAppearance(),
            widgetAppearance("""{"themeProps":{"themeMode":"system"},"appSettingProps":{"locale":"de"}}"""),
        )
    }

    @Test
    fun `night mode follows the app setting ahead of the system`() {
        assertFalse(resolvesNight(systemNight = true, WidgetNightMode.LIGHT))
        assertTrue(resolvesNight(systemNight = false, WidgetNightMode.DARK))
        assertTrue(resolvesNight(systemNight = true, WidgetNightMode.FOLLOW_SYSTEM))
        assertFalse(resolvesNight(systemNight = false, WidgetNightMode.FOLLOW_SYSTEM))
    }
}
