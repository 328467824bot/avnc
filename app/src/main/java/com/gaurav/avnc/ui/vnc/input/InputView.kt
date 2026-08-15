/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc.input

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import com.gaurav.avnc.viewmodel.VncViewModel

/**
 * This is a simple, transparent view to handle input events.
 * It acts as an edit box to handle key events.
 */
class InputView(context: Context?, attrs: AttributeSet? = null) : View(context, attrs) {

    private var viewModel: VncViewModel? = null
    private var inputHandler: InputHandler? = null

    /**
     * Input connection used for intercepting key events
     */
    inner class InputConnection : BaseInputConnection(this, false) {
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            return inputHandler?.onKeyEvent(event) == true || super.sendKeyEvent(event)
        }

        /**
         * When "send text via clipboard" is enabled, committed text is delivered to
         * the server through clipboard + Ctrl+V (see Messenger.sendClipboardPaste)
         * instead of as individual key events. This is far more reliable for CJK
         * text, because key events depend on the server's keymap containing the
         * matching X KeySym, which is usually not the case for CJK characters.
         */
        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            val str = text.toString()
            val delivery = viewModel?.pref?.input?.textInputDelivery
            if (str.isNotEmpty() && (delivery == "ctrl_v" || delivery == "shift_insert")) {
                viewModel?.messenger?.sendClipboardPaste(str, delivery == "shift_insert")
                return true
            }
            return super.commitText(text, newCursorPosition)
        }

        /**
         * In clipboard mode, composing (preview) text must never be flushed to the
         * server — only committed text is sent (via commitText). Otherwise the
         * default BaseInputConnection.finishComposingText() would send the
         * uncommitted composing text (e.g. intermediate pinyin letters) as key events.
         */
        override fun finishComposingText(): Boolean {
            val delivery = viewModel?.pref?.input?.textInputDelivery
            if (delivery == "ctrl_v" || delivery == "shift_insert")
                return true
            return super.finishComposingText()
        }
    }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setWillNotDraw(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
    }

    /**
     * Should be called from [com.gaurav.avnc.ui.vnc.VncActivity.onCreate].
     */
    fun initialize(viewModel: VncViewModel, inputHandler: InputHandler) {
        this.viewModel = viewModel
        this.inputHandler = inputHandler

        // Hide local cursor if requested and supported
        if (Build.VERSION.SDK_INT >= 24 && viewModel.pref.input.hideLocalCursor)
            pointerIcon = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = outAttrs.imeOptions or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN
        return InputConnection()
    }

    override fun onCheckIsTextEditor(): Boolean {
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return inputHandler?.onTouchEvent(event) == true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return inputHandler?.onGenericMotionEvent(event) == true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        return inputHandler?.onHoverEvent(event) == true
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        return inputHandler?.onCapturedPointerEvent(event) == true
    }
}