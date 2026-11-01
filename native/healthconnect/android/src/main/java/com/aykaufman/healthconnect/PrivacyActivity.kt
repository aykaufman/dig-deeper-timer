package com.aykaufman.healthconnect

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class PrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            setPadding(pad, pad * 2, pad, pad)
            textSize = 16f
            text = "Dig Deeper and Health Connect\n\n" +
                "Dig Deeper writes each finished workout (as strength training, with its estimated calories) " +
                "and the weights you log to Health Connect, and reads your weight from Health Connect so " +
                "entries from a scale or other apps show up in your body stats.\n\n" +
                "This data stays on your phone. Dig Deeper has no servers and never sends your health data anywhere.\n\n" +
                "You can turn this off at any time in Health Connect's settings."
        }
        setContentView(ScrollView(this).apply { addView(text) })
    }
}
