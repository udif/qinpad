package com.udifink.qinhpad

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

class QinPadIME : InputMethodService() {
    private var currentType = InputType.TYPE_CLASS_TEXT
    private var ic: InputConnection? = null
    private val kpTimeout = 500 //single-char input timeout
    private var cachedDigit = 10
    private var caps = false
    private var rotationMode = false
    private var rotIndex = 0
    private var lockFlag = 0 //input lock flag for long presses
    private var currentLayoutIndex = 0 //you can change the default here
    private val rotResetHandler = Handler()
    private var imm: InputMethodManager? = null

    //layoutIconsNormal, layoutIconsCaps and layouts must match each other
    private val layoutIcons = arrayOf(R.drawable.ime_latin_normal, R.drawable.ime_latin_caps, R.drawable.ime_hebrew_normal)
    class LayoutType(
        var Index:Int, // index into layouts. yes this is a hack, we'll fix it later
        var Caps:Boolean // does this layout requires a CAPS mode
    )
    private val layoutTypes = arrayOf(
        LayoutType(Index=1, Caps=false), // English, no caps
        LayoutType(Index=1, Caps=true),  // English, caps
        LayoutType(Index=0, Caps=false)  // Hebrew,  no caps
    )
    private val layouts = arrayOf(
        // We disable at the moment the cyrillic and eu keyboard until we set up a settings window
        // to selectively enable/disable languages
        //arrayOf( //cyrillic
        //    " +\n_$#()[]{}", ".,?!'\"1-~@/:\\",
        //    "абвг2ґ", "деёжз3є", "ийкл4ії",
        //    "мноп5", "рсту6", "фхцч7", "шщъы8",
        //    "ьэюя9"
        //),
        //arrayOf( //latin
        //    " +\n_$#()[]{}", ".,?!¿¡'\"1-~@/:\\", "abc2áäåāǎàçč",
        //    "def3éēěè", "ghi4ģíīǐì", "jkl5ķļ",
        //    "mno6ñņóõöøōǒò", "pqrs7ßš", "tuv8úüūǖǘǔǚùǜ",
        //    "wxyz9ýž"
        //),
        arrayOf( // Hebrew
            " +\n_$#()[]{}", ".,?!¿¡'\"1-~@/:\\", "דהו2", "אבג3",
            "םמןנ4", "יךכל5", "זחט6",
            "רשת7",  "ץצק8",  "סעףפ9"
        ),
        arrayOf( //latin, ABC only
            " +\n_$#()[]{}", ".,?!¿¡'\"1-~@/:\\", "abc2", "def3",
            "ghi4", "jkl5", "mno6",
            "pqrs7", "tuv8", "wxyz9"
        )
    )
    private var currentLayout: Array<String>? = null

    // method checking if the Google voice input is installed and returning its Id
    private fun voiceExists(imeManager: InputMethodManager): String? {
        val list = imeManager.inputMethodList
        for (el in list) {
            // return the id of the Google voice input input method
            // in this case "com.google.android.googlequicksearchbox"
            val id = el.id
            if (id.contains("com.google.android.voicesearch")) {
                return id
            }
        }
        return null
    }

    private fun resetRotator() {
        rotIndex = 0
        rotationMode = false
        cachedDigit = 10
    }

    private fun updateCurrentStatusIcon() {
        hideStatusIcon()
        showStatusIcon( layoutIcons[currentLayoutIndex])
    }

    private fun kkToDigit(keyCode: Int): Int {
        return when(keyCode) {
            KeyEvent.KEYCODE_0,
            KeyEvent.KEYCODE_1,
            KeyEvent.KEYCODE_2,
            KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4,
            KeyEvent.KEYCODE_5,
            KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8,
            KeyEvent.KEYCODE_9 ->
                keyCode - KeyEvent.KEYCODE_0
            else -> 10
        }
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        currentType = info.inputType
        if(currentType == 0 || currentType and EditorInfo.TYPE_MASK_CLASS == EditorInfo.TYPE_CLASS_PHONE)
            onFinishInput()
        else {
            ic = this.currentInputConnection
            caps = false
            resetRotator()
            val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            updateCurrentStatusIcon()
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        ic = null
        hideStatusIcon()
        requestHideSelf(0)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if(ic == null) {
            resetRotator()
            return false
        }
        val digit = kkToDigit(keyCode)
        var pound = false
        var star = false
        if(digit > 9) {
            when(keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DEL -> return handleBackspace(event)
                KeyEvent.KEYCODE_POUND -> pound = true
                KeyEvent.KEYCODE_STAR -> star = true
                else -> {
                    resetRotator()
                    return super.onKeyDown(keyCode, event)
                }
            }
        }

        when(currentType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> {
                if(pound)
                    ic!!.commitText(".", 1)
                else if(!star)
                    ic!!.commitText(Integer.toString(digit), 1)
                return true
            }
            else -> if(lockFlag == 0) {
                event.startTracking()
                return handleTextInput(digit, star, pound)
            } else
                return true
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        val digit = kkToDigit(keyCode)
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            // Start voice input
            // check if the  Google voice input exist first

            // Start voice input
            // check if the  Google voice input exist first
            val imm = this.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val voiceExists = voiceExists(imm)
            if (voiceExists != null) {
                switchInputMethod(voiceExists)
            }

        }
        if(digit > 9) {
            resetRotator()
            return false
        }
        ic!!.deleteSurroundingText(1, 0)
        ic!!.commitText(Integer.toString(digit), 1)
        resetRotator()
        lockFlag = 1
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        var res: Boolean
        if(keyCode == KeyEvent.KEYCODE_BACK && ic != null)
            res = true
        else {
            if(lockFlag == 1) lockFlag = 0
            res = super.onKeyUp(keyCode, event)
        }
        return res
    }

    private fun handleBackspace(ev: KeyEvent): Boolean {
        resetRotator()
        if(ic == null) {
            requestHideSelf(0)
            ic!!.sendKeyEvent(ev)
            return false
        }
        if(ic!!.getTextBeforeCursor(1, 0).isEmpty()) {
            ic!!.sendKeyEvent(ev)
            requestHideSelf(0)
            ic!!.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            ic!!.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            return false
        } else {
            ic!!.deleteSurroundingText(1, 0)
            return true
        }
    }

    private fun nextLang() {
        val maxInd = layoutTypes.size - 1
        currentLayoutIndex++
        if(currentLayoutIndex > maxInd) currentLayoutIndex = 0
        currentLayout = layouts[layoutTypes[currentLayoutIndex].Index]
        caps = layoutTypes[currentLayoutIndex].Caps
        updateCurrentStatusIcon()
        resetRotator()
    }

    private fun handleTextInput(digit: Int, star: Boolean, pound: Boolean): Boolean {
        if(star) {
            // Add here support for Google voice input
        } else if(pound) {
            nextLang()
        } else {
            var targetSequence: CharSequence
            currentLayout = layouts[layoutTypes[currentLayoutIndex].Index]
            val selection = currentLayout!![digit]

            rotResetHandler.removeCallbacksAndMessages(null)
            if(digit != cachedDigit) {
                resetRotator()
                cachedDigit = digit
            } else {
                rotationMode = true //mark that we're going to delete the next char
                rotIndex++
                if(rotIndex >= selection.length)
                    rotIndex = 0
            }
            rotResetHandler.postDelayed({ resetRotator() }, kpTimeout.toLong())

            targetSequence = selection.subSequence(rotIndex, rotIndex + 1)
            if(rotationMode)
                ic!!.deleteSurroundingText(1, 0)
            if(caps)
                targetSequence = targetSequence.toString().toUpperCase()
            ic!!.commitText(targetSequence, 1)
        }
        return true
    }
}
