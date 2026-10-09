package com.projectego.app.domain

import com.projectego.app.model.AppCategory
import com.projectego.app.model.FocusActivityType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The focus gate as a pure rule, with no Android runtime.
 *
 * These tests hold the two contracts the user's screen depends on: a curated
 * app is let in even when screen-time defaults paint it as entertainment, and
 * messaging stays open in every activity because a blocked message is a
 * message the person never sees.
 */
class FocusAppPolicyTest {

    @Test
    fun `a curated app passes even when classified as entertainment`() {
        assertTrue(
            FocusAppPolicy.decide(
                FocusAppPolicy.getApprovedPackages(FocusActivityType.STUDY),
                AppCategory.ENTERTAINMENT,
                "com.notion.id"
            )
        )
    }

    @Test
    fun `messaging stays open in every focus activity`() {
        for (activityType in FocusActivityType.values()) {
            assertTrue(
                FocusAppPolicy.decide(
                    FocusAppPolicy.getApprovedPackages(activityType),
                    AppCategory.TRACKED,
                    "com.whatsapp"
                )
            )
        }
    }

    @Test
    fun `entertainment outside the curated list stays closed`() {
        assertFalse(
            FocusAppPolicy.decide(
                FocusAppPolicy.getApprovedPackages(FocusActivityType.STUDY),
                AppCategory.ENTERTAINMENT,
                "com.instagram.android"
            )
        )
    }

    @Test
    fun `essential apps pass in every focus activity`() {
        for (activityType in FocusActivityType.values()) {
            assertTrue(
                FocusAppPolicy.decide(
                    FocusAppPolicy.getApprovedPackages(activityType),
                    AppCategory.ESSENTIAL,
                    "com.android.settings"
                )
            )
        }
    }
}
