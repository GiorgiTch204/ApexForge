package com.apexforge.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.apexforge.app.ui.components.ApexButton
import com.apexforge.app.ui.components.NumberInput
import com.apexforge.app.ui.theme.ApexTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun buttonInvokesCallback() {
        var clicks = 0
        rule.setContent { ApexTheme(darkTheme = true, hapticsEnabled = false) { ApexButton("Go", { clicks++ }) } }
        rule.onNodeWithText("Go").performClick()
        assertEquals(1, clicks)
    }

    @Test fun disabledButtonIgnoresClicks() {
        var clicks = 0
        rule.setContent { ApexTheme(darkTheme = true, hapticsEnabled = false) { ApexButton("Go", { clicks++ }, enabled = false) } }
        rule.onNodeWithText("Go").performClick()
        assertEquals(0, clicks)
    }

    @Test fun numberInputReportsTypedValue() {
        var value by mutableStateOf(0.0)
        rule.setContent {
            ApexTheme(darkTheme = true, hapticsEnabled = false) {
                NumberInput(value, { value = it }, decimal = true, contentDescription = "Weight")
            }
        }
        rule.onNodeWithContentDescription("Weight").performTextInput("82.5")
        assertTrue(kotlin.math.abs(value - 82.5) < 1e-9)
    }

    @Test fun numberInputRejectsLetters() {
        var value by mutableStateOf(0.0)
        rule.setContent {
            ApexTheme(darkTheme = true, hapticsEnabled = false) {
                NumberInput(value, { value = it }, contentDescription = "Reps")
            }
        }
        rule.onNodeWithContentDescription("Reps").performTextInput("abc")
        assertEquals(0.0, value, 0.0)
    }
}
