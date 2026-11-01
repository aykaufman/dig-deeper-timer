package com.aykaufman.digdeeper

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import kotlin.math.ceil

/**
 * Dig Deeper on the watch: a remote for the phone app. The phone runs the workout and sends the
 * current step here; buttons send actions back. Nothing is stored on the watch.
 */
class MainActivity : Activity(), DataClient.OnDataChangedListener {
    private val orange = Color.parseColor("#FF6A2B")
    private val green = Color.parseColor("#6BE8B6")
    private val dim = Color.parseColor("#2A241F")
    private val muted = Color.parseColor("#C9C0B5")

    private val handler = Handler(Looper.getMainLooper())
    private var state = JSONObject().put("mode", "idle")
    private var lastKey = ""
    private var lastBuzzSec = -1

    private lateinit var scroll: ScrollView
    private lateinit var box: LinearLayout
    private var timerView: TextView? = null

    private val ticker = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 250)
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val w = resources.displayMetrics.widthPixels
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFocusableInTouchMode = true
        }
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val side = (w * 0.12).toInt()
            setPadding(side, (w * 0.14).toInt(), side, (w * 0.22).toInt())
        }
        scroll.addView(box, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getDataClient(this).dataItems.addOnSuccessListener { buf ->
            for (item in buf) {
                if (item.uri.path == "/ddt/state") apply(DataMapItem.fromDataItem(item).dataMap.getString("json"))
            }
            buf.release()
        }
        handler.post(ticker)
        scroll.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
        handler.removeCallbacks(ticker)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == "/ddt/state") {
                apply(DataMapItem.fromDataItem(e.dataItem).dataMap.getString("json"))
            }
        }
        events.release()
    }

    private fun apply(json: String?) {
        if (json == null) return
        val s = try { JSONObject(json) } catch (e: Exception) { return }
        runOnUiThread {
            val key = s.optString("key")
            if (key.isNotEmpty() && key != lastKey && lastKey.isNotEmpty()) buzz(220)
            lastKey = key
            state = s
            render()
        }
    }

    private fun buzz(ms: Long) {
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {}
    }

    private fun send(id: String) {
        val msg = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) {
                Toast.makeText(this, "Phone not connected", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }
            for (n in nodes) msg.sendMessage(n.id, "/ddt/action", id.toByteArray())
        }
    }

    // ---------- drawing ----------
    private fun text(t: String, sp: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = t
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun pill(label: String, bg: Int, fg: Int, onTap: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply { cornerRadius = dp(24f).toFloat(); setColor(bg) }
            minHeight = dp(46f)
            setOnClickListener { buzz(30); onTap() }
        }

    private fun gap(h: Float) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    private fun full(v: View, h: Float = 46f): View {
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(h)).apply { topMargin = dp(6f) }
        return v
    }

    /** "−  25 lb  +" */
    private fun stepper(value: String, minus: String, plus: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val m = pill("−", dim, Color.WHITE) { send(minus) }
        val p = pill("+", dim, Color.WHITE) { send(plus) }
        val v = text(value, 22f, Color.WHITE, true)
        row.addView(m, LinearLayout.LayoutParams(dp(46f), dp(46f)))
        row.addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(p, LinearLayout.LayoutParams(dp(46f), dp(46f)))
        row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6f) }
        return row
    }

    private fun fmtW(w: Double): String = if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()

    private fun render() {
        box.removeAllViews()
        timerView = null
        val s = state
        val mode = s.optString("mode", "idle")
        val live = mode == "work" || mode == "rest" || mode == "prep"
        if (live) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        when (mode) {
            "idle", "done" -> {
                box.addView(text("Dig Deeper", 18f, orange, true))
                box.addView(gap(8f))
                box.addView(text(
                    if (mode == "done") s.optString("sub", "Workout complete. Nice work.")
                    else "Start a workout in Dig Deeper on your phone. It shows up here.", 14f, muted))
                return
            }
        }

        val accent = if (mode == "work") orange else green
        box.addView(text(s.optString("kicker", ""), 12f, accent, true))
        box.addView(text(s.optString("title", ""), 18f, Color.WHITE, true))
        val sub = s.optString("sub", "")
        if (sub.isNotEmpty()) box.addView(text(sub, 13f, muted))
        timerView = text("", 30f, Color.WHITE, true)
        box.addView(timerView)
        updateTimer()

        if (mode == "work") {
            if (s.optBoolean("weighted")) {
                box.addView(stepper(if (s.isNull("w")) "– lb" else fmtW(s.optDouble("w")) + " lb", "wDown", "wUp"))
            }
            if (s.optBoolean("hasReps")) {
                box.addView(stepper(s.optInt("reps").toString() + " reps", "repDown", "repUp"))
            }
            box.addView(full(pill("Done", orange, Color.parseColor("#1F0E04")) { send("done") }, 52f))
            box.addView(full(pill(if (s.optBoolean("paused")) "Resume" else "Pause", dim, Color.WHITE) { send("pause") }))
        } else {
            box.addView(full(pill(if (mode == "prep") "Start now" else "Skip rest", green, Color.parseColor("#052A1F")) { send("skip") }, 52f))
            box.addView(full(pill("+15 s", dim, Color.WHITE) { send("more") }))
            if (s.optBoolean("hasLast")) {
                box.addView(gap(10f))
                box.addView(text("Last set: " + s.optString("lastName", ""), 12f, muted))
                if (s.optBoolean("lastWeighted")) {
                    box.addView(stepper(if (s.isNull("lastW")) "– lb" else fmtW(s.optDouble("lastW")) + " lb", "fwDown", "fwUp"))
                }
                if (s.optBoolean("lastHasReps")) {
                    box.addView(stepper(s.optInt("lastReps").toString() + " reps", "fRepDown", "fRepUp"))
                }
            }
            box.addView(full(pill(if (s.optBoolean("paused")) "Resume" else "Pause", dim, Color.WHITE) { send("pause") }))
        }
    }

    private fun updateTimer() {
        val tv = timerView ?: return
        val s = state
        if (s.optBoolean("paused")) { tv.text = "Paused"; tv.visibility = View.VISIBLE; return }
        val endsAt = s.optLong("endsAt", 0L)
        if (endsAt <= 0L) { tv.visibility = View.GONE; return }
        val left = ceil((endsAt - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(0)
        tv.visibility = View.VISIBLE
        tv.text = if (left >= 60) "%d:%02d".format(left / 60, left % 60) else left.toString()
        if (left in 1..3 && left != lastBuzzSec) { lastBuzzSec = left; buzz(60) }
    }
}
