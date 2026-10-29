package com.projectego.app

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Static locks on the manifest's exposure boundary (SEC-04).
 *
 * These read the source manifest - the app declares everything there, and the
 * merged manifest only adds library components. Passing here is proof about the
 * declaration, not about runtime binder behaviour; that distinction is why the
 * checks are exact sets rather than "contains".
 */
class ManifestExposureTest {

    private val manifest: Element by lazy { parseManifest() }

    @Test
    fun `internal components are not exported`() {
        val internal = listOf(
            "service" to ".service.InfoPillService",
            "service" to ".service.GuardianWatchService",
            "activity" to ".ui.WelcomeActivity",
            "activity" to ".ui.LockActivity",
            "activity" to ".ui.CriticalAlertActivity",
            "activity" to ".ui.GuardianAlertActivity",
            "receiver" to ".service.PromiseWatchReceiver",
            "receiver" to ".service.QuestWatchReceiver",
            "receiver" to ".service.QuestDeadlineReceiver",
            "receiver" to ".service.AccessibilityWatchReceiver",
            "receiver" to ".service.BootReceiver"
        )
        internal.forEach { (tag, name) ->
            assertEquals(
                "<$tag> $name must stay private to the app",
                "false",
                exported(tag, name)
            )
        }
    }

    @Test
    fun `only the system-bound components are exported`() {
        val exported = mutableSetOf<String>()
        listOf("activity", "activity-alias", "service", "receiver").forEach { tag ->
            components(tag).forEach { element ->
                if (element.getAttribute("android:exported") == "true") {
                    exported += "$tag ${element.getAttribute("android:name")}"
                }
            }
        }
        assertEquals(
            "any new export needs its own security review",
            setOf(
                "activity .MainActivity",
                "service .service.InfoPillTile",
                "service .service.EgoAccessibilityService",
                "receiver .ui.widget.AliveStatsWidgetProvider"
            ),
            exported
        )
    }

    @Test
    fun `every declared component states exported explicitly`() {
        listOf("activity", "activity-alias", "service", "receiver").forEach { tag ->
            components(tag).forEach { element ->
                assertFalse(
                    "<$tag> ${element.getAttribute("android:name")} must declare android:exported",
                    element.getAttribute("android:exported").isBlank()
                )
            }
        }
    }

    @Test
    fun `the tile and the accessibility service keep their platform permissions`() {
        val tile = component("service", ".service.InfoPillTile")
        assertEquals("true", tile.getAttribute("android:exported"))
        assertEquals(
            "android.permission.BIND_QUICK_SETTINGS_TILE",
            tile.getAttribute("android:permission")
        )
        assertEquals(
            "the tile filter is what lets the system bind it",
            listOf("android.service.quicksettings.action.QS_TILE"),
            actions(tile)
        )

        val guard = component("service", ".service.EgoAccessibilityService")
        assertEquals("true", guard.getAttribute("android:exported"))
        assertEquals(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            guard.getAttribute("android:permission")
        )
        assertEquals(
            listOf("android.accessibilityservice.AccessibilityService"),
            actions(guard)
        )
        assertEquals(
            "the system needs the service metadata to declare the capability",
            1,
            guard.getElementsByTagName("meta-data").length
        )
    }

    @Test
    fun `the widget stays exported for the launcher and listens to nothing but updates`() {
        val widget = component("receiver", ".ui.widget.AliveStatsWidgetProvider")
        assertEquals("true", widget.getAttribute("android:exported"))
        assertEquals(listOf("android.appwidget.action.APPWIDGET_UPDATE"), actions(widget))
        assertEquals(
            "a widget without its provider info cannot be placed",
            1,
            widget.getElementsByTagName("meta-data").length
        )
    }

    @Test
    fun `MainActivity is a launcher and nothing else`() {
        val main = component("activity", ".MainActivity")
        assertEquals("true", main.getAttribute("android:exported"))
        val filters = main.getElementsByTagName("intent-filter")
        assertEquals("no second door into the app", 1, filters.length)
        assertEquals(listOf("android.intent.action.MAIN"), actions(main))
        assertEquals(listOf("android.intent.category.LAUNCHER"), categories(main))
        assertEquals(
            "no deep links: the app has no URL contract",
            0,
            main.getElementsByTagName("data").length
        )
    }

    @Test
    fun `BootReceiver answers only to boot`() {
        val boot = component("receiver", ".service.BootReceiver")
        assertEquals("false", boot.getAttribute("android:exported"))
        assertEquals(listOf("android.intent.action.BOOT_COMPLETED"), actions(boot))
        assertEquals(
            "one filter, so no second trigger can be added unnoticed",
            1,
            boot.getElementsByTagName("intent-filter").length
        )
    }

    private fun parseManifest(): Element {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("manifest not found from ${File(".").absolutePath}")
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun components(tag: String): List<Element> {
        val nodes = manifest.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun component(tag: String, name: String): Element {
        val element = components(tag).firstOrNull {
            it.getAttribute("android:name") == name
        }
        assertNotNull("no <$tag> named $name", element)
        return element!!
    }

    private fun exported(tag: String, name: String): String =
        component(tag, name).getAttribute("android:exported")

    private fun actions(component: Element): List<String> =
        namedChildren(component, "action")

    private fun categories(component: Element): List<String> =
        namedChildren(component, "category")

    private fun namedChildren(component: Element, tag: String): List<String> {
        val nodes = component.getElementsByTagName(tag)
        return (0 until nodes.length).map {
            (nodes.item(it) as Element).getAttribute("android:name")
        }
    }
}
