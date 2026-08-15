/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.session

import android.graphics.PointF
import android.util.Log
import com.gaurav.avnc.vnc.PointerButton
import com.gaurav.avnc.vnc.VncClient
import com.gaurav.avnc.vnc.XKeySym
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Allows sending different types of messages to remote server.
 */
class Messenger(private val client: VncClient) {

    /**************************************************************************
     * Sender thread
     **************************************************************************/
    private val sender = Executors.newSingleThreadExecutor()

    private fun execute(action: Runnable): Boolean {
        try {
            if (client.connected && !sender.isShutdown) {
                sender.execute(action)
                return true
            }
        } catch (e: Exception) {
            Log.w("Messenger", "Failed to enqueue action [isShutdown: ${sender.isShutdown}]: ${e.message}")
        }
        return false
    }

    fun shutdown() {
        runCatching {
            sender.shutdown()
            sender.awaitTermination(60, TimeUnit.SECONDS)
        }
        if (!sender.isTerminated)
            Log.w("Messenger", "Unable to shutdown messenger thread")
    }


    /**************************************************************************
     * Input events
     **************************************************************************/

    /**
     * Keeps track of current pointer button state.
     */
    private var pointerButtonMask: Int = 0

    private fun sendPointerEvent(mask: Int, p: PointF) {
        val x = p.x.toInt()
        val y = p.y.toInt()
        client.moveClientPointer(x, y)
        execute { client.sendPointerEvent(x, y, mask) }
    }

    fun sendPointerButtonDown(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask or button.bitMask
        sendPointerEvent(pointerButtonMask, p)
    }

    fun sendPointerButtonUp(button: PointerButton, p: PointF) {
        pointerButtonMask = pointerButtonMask and button.bitMask.inv()
        sendPointerEvent(pointerButtonMask, p)
    }

    fun sendPointerButtonRelease(p: PointF) {
        if (pointerButtonMask != 0) {
            pointerButtonMask = 0
            sendPointerEvent(pointerButtonMask, p)
        }
    }

    fun sendKey(keySym: Int, xtCode: Int, isDown: Boolean): Boolean {
        return execute { client.sendKeyEvent(keySym, xtCode, isDown) }
    }

    fun insertButtonUpDelay() {
        execute { runCatching { Thread.sleep(200) } }
    }

    /**************************************************************************
     * Misc
     **************************************************************************/

    fun sendClipboardText(text: String) {
        execute { client.sendCutText(text) }
    }

    /**
     * Sends given text to the remote server by placing it on the server's clipboard
     * and then triggering a paste (Ctrl+V or Shift+Insert).
     *
     * This is an alternative to delivering text as individual key events. Key events
     * rely on the server's keyboard layout containing the matching X KeySym, which
     * is almost never the case for CJK characters — so most characters get silently
     * dropped by the server. Clipboard-based delivery sidesteps the keysym mapping
     * completely and works reliably on servers which support clipboard + paste.
     * (TigerVNC owns both the PRIMARY and CLIPBOARD selections after ClientCutText,
     * so both Ctrl+V and Shift+Insert can paste the text.)
     *
     * Everything runs on the sender thread, in order, so the clipboard is updated
     * before the paste key events are sent.
     *
     * @param useShiftInsert  true -> paste with Shift+Insert (PRIMARY; good for terminals
     *                        like xterm), false -> paste with Ctrl+V (CLIPBOARD; GUI apps).
     */
    fun sendClipboardPaste(text: String, useShiftInsert: Boolean = false) {
        execute {
            client.sendCutText(text)
            runCatching { Thread.sleep(100) } // Give the server time to update its clipboard
            if (useShiftInsert) {
                client.sendKeyEvent(XKeySym.XK_Shift_L, 0, true)
                client.sendKeyEvent(XKeySym.XK_Insert, 0, true)
                client.sendKeyEvent(XKeySym.XK_Insert, 0, false)
                client.sendKeyEvent(XKeySym.XK_Shift_L, 0, false)
            } else {
                client.sendKeyEvent(XKeySym.XK_Control_L, 0, true)
                client.sendKeyEvent(XKeySym.XK_v, 0, true)
                client.sendKeyEvent(XKeySym.XK_v, 0, false)
                client.sendKeyEvent(XKeySym.XK_Control_L, 0, false)
            }
        }
    }

    fun setDesktopSize(width: Int, height: Int) {
        execute { client.setDesktopSize(width, height) }
    }

    fun refreshFrameBuffer() {
        execute { client.refreshFrameBuffer() }
    }

    fun setFrameBufferUpdatesPaused(pause: Boolean) {
        execute { client.setFrameBufferUpdatesPaused(pause) }
    }
}