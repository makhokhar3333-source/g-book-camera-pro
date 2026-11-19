package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name and package configuration are correct`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("G-Book Camera", context.getString(R.string.app_name))
        assertEquals("com.gbookcamera.app", context.packageName)
    }

    @Test
    fun `notification gate launches cleanly and advances to welcome screen on allow`() {
        composeRule.onNodeWithTag("notif_gate_title").assertIsDisplayed()
        composeRule.onNodeWithTag("allow_notifications_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("welcome_greeting_text").assertIsDisplayed()
        composeRule.onNodeWithTag("editing_button").assertIsDisplayed()
    }
}
