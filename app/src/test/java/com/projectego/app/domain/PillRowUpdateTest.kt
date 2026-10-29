package com.projectego.app.domain

import com.projectego.app.domain.PillRowUpdate.Action
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The row-update policy: rebuild only when the structure moved, write numbers when
 * only the numbers did, do nothing when nothing did. This is what keeps a minute
 * turning over from rebuilding the panel's views inside a tap.
 */
class PillRowUpdateTest {

    private val apps = listOf("com.a", "com.b", "com.c")

    @Test
    fun `the first render builds the rows`() {
        assertEquals(
            Action.REBUILD,
            PillRowUpdate.action(emptyList(), apps, null, apps[0], emptyList(), listOf(3L, 2L, 1L))
        )
    }

    @Test
    fun `later numbers on the same apps are written, not rebuilt`() {
        assertEquals(
            Action.NUMBERS,
            PillRowUpdate.action(apps, apps, apps[0], apps[0], listOf(3L, 2L, 1L), listOf(4L, 2L, 1L))
        )
    }

    @Test
    fun `an unchanged render does nothing`() {
        assertEquals(
            Action.NOTHING,
            PillRowUpdate.action(apps, apps, apps[0], apps[0], listOf(3L, 2L, 1L), listOf(3L, 2L, 1L))
        )
    }

    @Test
    fun `a reorder is a structure change even when the apps are the same`() {
        // The list is the ranking: same set, different order, different rows.
        assertEquals(
            Action.REBUILD,
            PillRowUpdate.action(apps, listOf("com.b", "com.a", "com.c"), apps[0], apps[0],
                listOf(3L, 2L, 1L), listOf(2L, 3L, 1L))
        )
    }

    @Test
    fun `a different watched app is a structure change`() {
        assertEquals(
            Action.REBUILD,
            PillRowUpdate.action(apps, apps, apps[0], apps[1], listOf(3L, 2L, 1L), listOf(3L, 2L, 1L))
        )
    }

    @Test
    fun `an app leaving or arriving is a structure change`() {
        assertEquals(
            Action.REBUILD,
            PillRowUpdate.action(apps, listOf("com.a", "com.b"), apps[0], apps[0],
                listOf(3L, 2L, 1L), listOf(3L, 2L))
        )
        assertEquals(
            Action.REBUILD,
            PillRowUpdate.action(listOf("com.a"), listOf("com.a", "com.z"), apps[0], apps[0],
                listOf(3L), listOf(3L, 1L))
        )
    }
}
