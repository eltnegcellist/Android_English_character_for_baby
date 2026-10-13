package com.eltnegcellist.emma.ai

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class LiteInteractionSettingTest {
    @Test fun defaultOnButExplicitOffIsPreserved() {
        var value: Boolean? = null
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "putBoolean" -> {
                    assertEquals(LiteInteractionSetting.KEY, args!![0])
                    value = args[1] as Boolean
                    proxy
                }
                "apply" -> null
                "commit" -> true
                else -> error("Unexpected editor call: ${method.name}")
            }
        } as SharedPreferences.Editor
        val preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> {
                    assertEquals(LiteInteractionSetting.KEY, args!![0])
                    value ?: (args[1] as Boolean)
                }
                "edit" -> editor
                else -> error("Unexpected SharedPreferences call: ${method.name}")
            }
        } as SharedPreferences

        assertTrue("Not-set means Interaction ON", LiteInteractionSetting.isEnabled(preferences))
        LiteInteractionSetting.setEnabled(preferences, false)
        assertFalse("Explicit OFF survives a new read", LiteInteractionSetting.isEnabled(preferences))
        LiteInteractionSetting.setEnabled(preferences, true)
        assertTrue("User can turn Interaction back ON", LiteInteractionSetting.isEnabled(preferences))
    }
}
