package com.agentcommander.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * The agent's eyes and hands. It only ever acts when the Engine tells it to,
 * and the Engine only does that while the owner has an agent approved and running.
 */
class AgentAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
        val connected = MutableStateFlow(false)

        /** Screens an agent may never operate: this app and system permission / install / settings screens. */
        val PROTECTED = setOf(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )
    }

    private var nodes: List<AccessibilityNodeInfo> = emptyList()
    private var lastPkg = ""
    private var volUp = false
    private var volDown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        Engine.init(this)
        instance = this
        connected.value = true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        connected.value = false
        Engine.kill("Accessibility service was turned off")
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    // ------------------------------------------------------------ hardware kill switch

    /** Volume Up + Volume Down pressed together = kill everything. Works in any app, any screen. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (down) volUp = true else if (up) volUp = false
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (down) volDown = true else if (up) volDown = false
            }
        }
        if (volUp && volDown) {
            volUp = false
            volDown = false
            Engine.kill("Volume Up + Volume Down kill switch")
        }
        return false
    }

    // ------------------------------------------------------------ learn mode (watching the owner)

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || !Engine.recording) return
        try {
            handleRecording(e)
        } catch (_: Exception) {
        }
    }

    private fun handleRecording(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                val src = e.source
                val label = if (src != null) labelOf(src)
                else (e.contentDescription?.toString() ?: e.text.joinToString(" ")).ifBlank { "element" }
                Engine.recordStep(pkg, "click", label, "", takeShot = true)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val src = e.source
                val hidden = src?.isPassword == true
                val typed = if (hidden) "<hidden>" else e.text.joinToString("")
                val label = if (src != null) fieldLabel(src) else "text field"
                Engine.recordStep(pkg, "type", label, typed, takeShot = false, replaceSameTarget = true)
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != lastPkg) {
                    lastPkg = pkg
                    Engine.recordStep(pkg, "open_app", pkg, "", takeShot = true)
                }
            }
        }
    }

    // ------------------------------------------------------------ seeing

    fun currentPackage(): String? = rootInActiveWindow?.packageName?.toString()

    fun labelOf(n: AccessibilityNodeInfo): String {
        if (n.isEditable) return fieldLabel(n)
        val t = n.text?.toString()
        if (!t.isNullOrBlank() && !n.isPassword) return t.take(60)
        val d = n.contentDescription?.toString()
        if (!d.isNullOrBlank()) return d.take(60)
        val id = n.viewIdResourceName
        if (!id.isNullOrBlank()) return id.substringAfter(":id/")
        return n.className?.toString()?.substringAfterLast('.') ?: "element"
    }

    private fun fieldLabel(n: AccessibilityNodeInfo): String {
        val hint = n.hintText?.toString()
        if (!hint.isNullOrBlank()) return hint.take(60)
        val id = n.viewIdResourceName
        if (!id.isNullOrBlank()) return id.substringAfter(":id/")
        return n.className?.toString()?.substringAfterLast('.') ?: "text field"
    }

    /** A numbered text list of everything visible and interesting on screen. */
    fun describeScreen(): String {
        val root = rootInActiveWindow ?: run {
            nodes = emptyList()
            return "NO_WINDOW (screen is not readable right now)"
        }
        val list = ArrayList<AccessibilityNodeInfo>()
        val sb = StringBuilder()
        sb.append("app=").append(root.packageName).append('\n')

        fun walk(n: AccessibilityNodeInfo) {
            if (list.size >= 120) return
            val t = n.text?.toString()?.take(80) ?: ""
            val d = n.contentDescription?.toString()?.take(80) ?: ""
            val interesting = t.isNotEmpty() || d.isNotEmpty() || n.isClickable || n.isEditable || n.isScrollable
            if (interesting && n.isVisibleToUser) {
                val idx = list.size
                list.add(n)
                sb.append('#').append(idx).append(' ')
                    .append(n.className?.toString()?.substringAfterLast('.') ?: "View")
                if (n.isPassword) {
                    sb.append(" [PASSWORD FIELD]")
                } else {
                    if (n.isEditable) sb.append(" [input hint=\"").append(fieldLabel(n)).append("\"]")
                    if (t.isNotEmpty()) sb.append(" text=\"").append(t).append('"')
                }
                if (d.isNotEmpty()) sb.append(" desc=\"").append(d).append('"')
                if (n.isClickable) sb.append(" [clickable]")
                if (n.isScrollable) sb.append(" [scrollable]")
                sb.append('\n')
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i) ?: continue
                walk(c)
            }
        }
        walk(root)
        nodes = list
        return sb.toString()
    }

    /** Takes a screenshot (Android 11+) and returns a small JPEG, or null if not possible. */
    suspend fun screenshotJpeg(maxW: Int, quality: Int): ByteArray? {
        if (Build.VERSION.SDK_INT < 30) return null
        val bmp: Bitmap = withTimeoutOrNull(4000) {
            suspendCancellableCoroutine<Bitmap?> { cont ->
                try {
                    takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            try {
                                val hb = result.hardwareBuffer
                                val hw = Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                                val sw = hw?.copy(Bitmap.Config.ARGB_8888, false)
                                hb.close()
                                if (cont.isActive) cont.resume(sw)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resume(null)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(null)
                        }
                    })
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        } ?: return null

        val w = if (bmp.width > maxW) maxW else bmp.width
        val h = (bmp.height.toFloat() * w / bmp.width).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    // ------------------------------------------------------------ acting

    fun resolve(target: String): AccessibilityNodeInfo? {
        val t = target.trim()
        if (t.isEmpty()) return null
        if (t.startsWith("#")) {
            val i = t.drop(1).toIntOrNull() ?: return null
            return nodes.getOrNull(i)
        }
        return nodes.firstOrNull { labelOf(it).equals(t, ignoreCase = true) }
            ?: nodes.firstOrNull { labelOf(it).contains(t, ignoreCase = true) }
    }

    fun click(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        var hops = 0
        while (c != null && !c.isClickable && hops < 6) {
            c = c.parent
            hops++
        }
        return (c ?: n).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun setText(n: AccessibilityNodeInfo, text: String): Boolean {
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    fun scroll(down: Boolean): Boolean {
        val n = nodes.firstOrNull { it.isScrollable } ?: return false
        return n.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    fun openApp(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == packageName || pkg in PROTECTED) return false
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
