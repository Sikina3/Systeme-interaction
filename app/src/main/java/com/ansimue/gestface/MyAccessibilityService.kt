package com.ansimue.gestface

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class MyAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: MyAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional: detect current package/active window if needed
    }

    override fun onInterrupt() {}

    // Global actions
    fun goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun switchApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    // Scroll by synthetic swipe gestures (coordinates tuned for many phones)
    fun scrollDown() {
        // swipe up -> scroll down
        val path = Path().apply {
            moveTo(540f, 1600f)
            lineTo(540f, 600f)
        }
        dispatchSwipe(path)
    }

    fun scrollUp() {
        // swipe down -> scroll up
        val path = Path().apply {
            moveTo(540f, 600f)
            lineTo(540f, 1600f)
        }
        dispatchSwipe(path)
    }

    fun performClick(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipe(path: Path) {
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
