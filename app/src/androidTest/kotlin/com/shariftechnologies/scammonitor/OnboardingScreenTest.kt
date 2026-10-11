package com.shariftechnologies.scammonitor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shariftechnologies.scammonitor.ui.onboarding.OnboardingScreen
import com.shariftechnologies.scammonitor.ui.theme.ScamMonitorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun explainsLocalProcessingAndOffersSettingsShortcut() {
        compose.setContent { ScamMonitorTheme { OnboardingScreen(onDone = {}) } }
        compose.onNodeWithText("Before you start").assertIsDisplayed()
        compose.onNodeWithText("Open notification access settings").assertIsDisplayed()
        compose.onNodeWithText("Recheck access").assertIsDisplayed()
    }
}
