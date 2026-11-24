package lk.pulseping

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

class SuitePages(
    private val context: Context,
    private val onOverlayToggle: (Boolean, String, Int, Int) -> Boolean
) {
    val sensiPage: ScrollView = page()
    val aimPage: ScrollView = page()
    val toolsPage: ScrollView = page()
    private val handler = Handler(Looper.getMainLooper())
    private val resultValues = linkedMapOf<String, Int>()
    private var swipeCount = 0
    private var swipeSpeedTotal = 0f
    private var swipeDistanceTotal = 0f
    private var gameRunning = false
    private var gameEndsAt = 0L
    private var targetShownAt = 0L
    private var hitCount = 0
    private var attemptCount = 0
    private var reactionTotal = 0L
    private var targetAnimator: ValueAnimator? = null
    private var activeGameTarget: View? = null
    private var activeGameStart: Button? = null
    private var activeGameStats: TextView? = null

    init {
        buildSensiPage()
        buildAimPage()
        buildToolsPage()
    }

    fun stopAnimations() {
        pauseGames()
    }

    fun pauseGames() {
        gameRunning = false
        handler.removeCallbacksAndMessages(null)
        targetAnimator?.cancel()
        activeGameTarget?.visibility = View.GONE
        activeGameStart?.isEnabled = true
        activeGameStart?.alpha = 1f
        if (activeGameStats != null && attemptCount > 0) {
            activeGameStats?.text = "Trainer paused · tap start for a new 15-second session."
        }
    }

    private fun page(): ScrollView = ScrollView(context).apply {
        isFillViewport = true
        setBackgroundColor(color(R.color.background))
        visibility = View.GONE
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun content(page: ScrollView): LinearLayout = page.getChildAt(0) as LinearLayout

    private fun buildSensiPage() {
        val root = content(sensiPage)
        addHeader(root, "SENSI LAB", "CALIBRATE YOUR DRAG. START WITH A PROFILE.")
        addSectionTitle(root, "LIVE DRAG CALIBRATION PAD")
        val pad = card(root, 176)
        val instruction = text("Swipe upward three times like your in-game fire-button drag.", 13, R.color.text_secondary)
        pad.addView(instruction, matchWrap())
        val counter = text("0 / 3  ·  UPWARD SWIPES", 12, R.color.crimson, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(8))
        }
        pad.addView(counter, matchWrap())
        val speedLabel = text("Measured speed and travel appear here.", 12, R.color.text_primary).apply {
            gravity = Gravity.CENTER
        }
        pad.addView(speedLabel, matchWrap())
        pad.setBackground(background(R.color.surface_alt, R.color.border, 18))
        pad.setOnTouchListener(object : View.OnTouchListener {
            var downY = 0f
            var downAt = 0L
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.y
                        downAt = event.eventTime
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val distance = downY - event.y
                        val elapsed = (event.eventTime - downAt).coerceAtLeast(1L)
                        if (distance > dp(12)) {
                            swipeCount++
                            swipeSpeedTotal += distance / elapsed
                            swipeDistanceTotal += distance
                            counter.text = "$swipeCount / 3  ·  UPWARD SWIPES"
                            speedLabel.text = String.format(
                                Locale.US,
                                "Latest: %.2f px/ms  ·  %.0f px",
                                distance / elapsed,
                                distance
                            )
                            if (swipeCount >= REQUIRED_SWIPES) {
                                swipeCount = REQUIRED_SWIPES
                                counter.text = "CALIBRATION COMPLETE"
                                speedLabel.text = String.format(
                                    Locale.US,
                                    "3-swipe average: %.2f px/ms  ·  %.0f px",
                                    swipeSpeedTotal / REQUIRED_SWIPES,
                                    swipeDistanceTotal / REQUIRED_SWIPES
                                )
                                recalculateSensitivity()
                            }
                        } else {
                            instruction.text = "Swipe upward farther to record a drag."
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return true
            }
        })

        addSectionTitle(root, "DEVICE PROFILE")
        val profile = card(root)
        addSelector(profile, "DEVICE BRAND", listOf("Infinix", "Samsung", "Xiaomi / Redmi", "Realme", "Vivo", "iPhone", "Other"))
            .onItemSelectedListener = selectorListener { recalculateSensitivity() }
        addSelector(profile, "RAM", listOf("2GB", "3GB", "4GB", "6GB", "8GB", "12GB+"))
            .onItemSelectedListener = selectorListener { recalculateSensitivity() }
        addSelector(profile, "REFRESH RATE", listOf("60Hz", "90Hz", "120Hz"))
            .onItemSelectedListener = selectorListener { recalculateSensitivity() }

        addSectionTitle(root, "STARTING SENSITIVITY PROFILE")
        val resultsCard = card(root)
        listOf("General", "Red Dot", "2x Scope", "4x Scope", "Sniper Scope", "Free Look").forEach { label ->
            addSensitivityRow(resultsCard, label)
        }
        val dpiValue = text("Safe recommended DPI: —", 13, R.color.text_primary, true).apply { tag = "DPI" }
        resultsCard.addView(dpiValue, matchWrap(dp(34)))
        val fireSize = text("Optimal fire button: —", 13, R.color.text_primary, true).apply { tag = "FIRE" }
        resultsCard.addView(fireSize, matchWrap(dp(30)))
        val copyButton = button("COPY ALL SETTINGS")
        resultsCard.addView(copyButton, matchWrap(dp(48)))
        copyButton.setOnClickListener {
            if (resultValues.isEmpty()) {
                toast("Complete three upward swipes to generate your profile.")
            } else {
                val summary = buildString {
                    resultValues.forEach { (name, value) -> appendLine("$name: $value") }
                    appendLine(dpiValue.text)
                    append(fireSize.text)
                }
                clipboard(summary)
                toast("Sensitivity profile copied")
            }
        }
        pad.tag = listOf(counter, speedLabel, instruction)
        resultsCard.tag = listOf(dpiValue, fireSize)
    }

    private fun recalculateSensitivity() {
        if (swipeCount < REQUIRED_SWIPES) return
        val brand = (content(sensiPage).findViewWithTag<View>("DEVICE BRAND") as? Spinner)?.selectedItemPosition ?: 0
        val ram = (content(sensiPage).findViewWithTag<View>("RAM") as? Spinner)?.selectedItemPosition ?: 2
        val refresh = (content(sensiPage).findViewWithTag<View>("REFRESH RATE") as? Spinner)?.selectedItemPosition ?: 0
        val averageSpeed = swipeSpeedTotal / REQUIRED_SWIPES
        val averageDistance = swipeDistanceTotal / REQUIRED_SWIPES
        val base = (135 + averageSpeed * 15 + (refresh * 4) + (ram * 2) - (brand % 3 * 3)).roundToInt()
            .coerceIn(80, 200)
        val multipliers = linkedMapOf(
            "General" to 1f,
            "Red Dot" to 0.94f,
            "2x Scope" to 0.82f,
            "4x Scope" to 0.68f,
            "Sniper Scope" to 0.46f,
            "Free Look" to 0.72f
        )
        multipliers.forEach { (label, multiplier) ->
            val value = (base * multiplier).roundToInt().coerceIn(0, 200)
            resultValues[label] = value
            val bar = content(sensiPage).findViewWithTag<ProgressBar>("BAR:$label")
            val valueText = content(sensiPage).findViewWithTag<TextView>("VALUE:$label")
            animateProgress(bar, value)
            valueText?.text = value.toString()
        }
        val dpi = (420 + averageSpeed * 20 + refresh * 30).roundToInt().coerceIn(400, 720)
        val fire = (55 - averageDistance / 70).roundToInt().coerceIn(35, 55)
        content(sensiPage).findViewWithTag<TextView>("DPI")?.text = "Safe recommended DPI: $dpi"
        content(sensiPage).findViewWithTag<TextView>("FIRE")?.text = "Optimal fire button: $fire%"
    }

    private fun addSensitivityRow(parent: LinearLayout, label: String) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val title = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        title.addView(text(label, 13, R.color.text_primary), LinearLayout.LayoutParams(0, dp(24), 1f))
        title.addView(text("—", 13, R.color.neon_green, true).apply { tag = "VALUE:$label" })
        row.addView(title, matchWrap())
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 200
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(color(R.color.crimson))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.border))
            tag = "BAR:$label"
        }
        row.addView(bar, matchWrap(dp(6)))
        parent.addView(row, matchWrap())
    }

    private fun buildAimPage() {
        val root = content(aimPage)
        addHeader(root, "AIM & CROSS", "PRECISION TOOLS & REFLEX TRAINING")
        addSectionTitle(root, "FLOATING CROSSHAIR")
        val crossCard = card(root)
        val styles = listOf(CrosshairOverlayService.STYLE_DOT, CrosshairOverlayService.STYLE_CROSS, CrosshairOverlayService.STYLE_CIRCLE)
        val styleSelector = addSelector(crossCard, "STYLE", styles)
        val colors = listOf("Red", "Green", "Cyan")
        val colorValues = listOf(0xFFFF334F.toInt(), 0xFF00E676.toInt(), 0xFF00E5FF.toInt())
        val colorSelector = addSelector(crossCard, "COLOR", colors)
        val sizeLabel = text("Size: 28 dp", 13, R.color.text_primary)
        crossCard.addView(sizeLabel, matchWrap(dp(24)))
        val sizeSeek = android.widget.SeekBar(context).apply { max = 60; progress = 16 }
        crossCard.addView(sizeSeek, matchWrap(dp(38)))
        val toggle = CheckBox(context).apply {
            text = "Show precision crosshair over other apps"
            setTextColor(color(R.color.text_primary))
            buttonTintList = android.content.res.ColorStateList.valueOf(color(R.color.crimson))
        }
        crossCard.addView(toggle, matchWrap(dp(48)))
        fun updateOverlay(enabled: Boolean): Boolean {
            val accepted = onOverlayToggle(
                enabled,
                styleSelector.selectedItem.toString(),
                colorValues[colorSelector.selectedItemPosition],
                sizeSeek.progress + 12
            )
            if (!accepted) toggle.isChecked = false
            return accepted
        }
        toggle.setOnCheckedChangeListener { _, checked -> updateOverlay(checked) }
        val updateSettings = View.OnClickListener { if (toggle.isChecked) updateOverlay(true) }
        styleSelector.setOnItemSelectedListener(selectorListener { updateSettings.onClick(toggle) })
        colorSelector.setOnItemSelectedListener(selectorListener { updateSettings.onClick(toggle) })
        sizeSeek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                sizeLabel.text = "Size: ${progress + 12} dp"
                if (toggle.isChecked) updateOverlay(true)
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        crossCard.addView(text(
            "Android will ask you to allow display-over-other-apps access. The overlay does not intercept game touches.",
            12,
            R.color.text_secondary
        ), matchWrap())

        addSectionTitle(root, "15-SECOND REFLEX TRAINER")
        val gameCard = card(root)
        val stats = text("Tap START to begin your session.", 13, R.color.text_primary, true)
        gameCard.addView(stats, matchWrap(dp(38)))
        val gameArea = FrameLayout(context).apply {
            setBackground(background(R.color.surface_alt, R.color.border, 16))
            isClickable = true
        }
        val target = Button(context).apply {
            text = "●"
            textSize = 22f
            setTextColor(Color.WHITE)
            background = background(R.color.crimson, R.color.crimson, 48)
            visibility = View.GONE
            minWidth = 0
            minHeight = 0
            setPadding(0, 0, 0, 0)
        }
        gameArea.addView(target, FrameLayout.LayoutParams(dp(54), dp(54)))
        gameCard.addView(gameArea, matchWrap(dp(240)))
        val start = button("START 15-SECOND TRAINER")
        gameCard.addView(start, matchWrap(dp(48)))
        activeGameTarget = target
        activeGameStart = start
        activeGameStats = stats
        val finishGame = {
            gameRunning = false
            target.visibility = View.GONE
            val accuracy = if (attemptCount == 0) 0 else (hitCount * 100f / attemptCount).roundToInt()
            val averageReaction = if (hitCount == 0) 0 else reactionTotal / hitCount
            stats.text = "Finished  ·  $averageReaction ms average  ·  $accuracy% accuracy ($hitCount/$attemptCount)"
            start.isEnabled = true
            start.alpha = 1f
        }
        val moveTarget = {
            val maxX = (gameArea.width - target.width).coerceAtLeast(1)
            val maxY = (gameArea.height - target.height).coerceAtLeast(1)
            target.x = Random.nextInt(maxX).toFloat()
            target.y = Random.nextInt(maxY).toFloat()
            targetShownAt = android.os.SystemClock.elapsedRealtime()
            targetAnimator?.cancel()
            targetAnimator = ValueAnimator.ofFloat(0.82f, 1f).apply {
                duration = 180L
                addUpdateListener {
                    val scale = it.animatedValue as Float
                    target.scaleX = scale
                    target.scaleY = scale
                }
                start()
            }
        }
        target.setOnClickListener {
            if (gameRunning) {
                attemptCount++
                hitCount++
                reactionTotal += android.os.SystemClock.elapsedRealtime() - targetShownAt
                moveTarget()
                stats.text = "Time left: ${((gameEndsAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0) / 1000)}s  ·  Hits: $hitCount"
            }
        }
        gameArea.setOnTouchListener { _, event ->
            if (gameRunning && event.actionMasked == MotionEvent.ACTION_DOWN &&
                (event.x < target.x || event.x > target.x + target.width ||
                    event.y < target.y || event.y > target.y + target.height)
            ) {
                attemptCount++
            }
            true
        }
        val tick = object : Runnable {
            override fun run() {
                if (!gameRunning) return
                val remaining = gameEndsAt - android.os.SystemClock.elapsedRealtime()
                if (remaining <= 0L) {
                    finishGame()
                } else {
                    stats.text = "Time left: ${((remaining + 999) / 1000)}s  ·  Hits: $hitCount"
                    handler.postDelayed(this, 200L)
                }
            }
        }
        start.setOnClickListener {
            if (gameArea.width == 0) gameArea.post { start.performClick() }
            else {
                hitCount = 0
                attemptCount = 0
                reactionTotal = 0L
                gameRunning = true
                gameEndsAt = android.os.SystemClock.elapsedRealtime() + 15_000L
                start.isEnabled = false
                start.alpha = 0.6f
                target.visibility = View.VISIBLE
                gameArea.post { if (gameRunning) moveTarget() }
                handler.removeCallbacks(tick)
                handler.post(tick)
            }
        }
    }

    private fun buildToolsPage() {
        val root = content(toolsPage)
        addHeader(root, "PRO TOOLS", "YOUR PRE-MATCH EDGE STARTS HERE")
        addSectionTitle(root, "ESPORTS NICKNAME GENERATOR")
        val namesCard = card(root)
        val names = listOf(
            "亗Shadow亗", "༄ᶦᶰᵈ᭄RAVEN", "Novaツ", "×͜×Viper", "么Phantom",
            "亗Lanka々", "༄ᶦᶰᵈ᭄Blaze", "GhostツLK", "×͜×Clutch", "么VENOM",
            "亗NightFury", "༄ᶦᶰᵈ᭄Ace", "Nova么X", "ツHeadshot", "×͜×Rogue"
        )
        val nameList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun renderNames() {
            nameList.removeAllViews()
            names.shuffled().take(15).forEach { nickname ->
                nameList.addView(text(nickname, 14, R.color.text_primary, true).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    setOnClickListener {
                        clipboard(nickname)
                        toast("Nickname copied")
                    }
                }, matchWrap(dp(38)))
            }
        }
        renderNames()
        namesCard.addView(nameList, matchWrap())
        val generate = button("GENERATE 15 NAMES")
        namesCard.addView(generate, matchWrap(dp(46)))
        generate.setOnClickListener { renderNames() }
        namesCard.addView(text("Tap any nickname to copy it.", 12, R.color.text_secondary), matchWrap(dp(30)))

        addSectionTitle(root, "PRE-GAME LAG FIX")
        val checklist = card(root)
        val tasks = listOf(
            "Close unused background apps",
            "Use a stable Wi-Fi or mobile connection",
            "Pause active downloads and updates",
            "Enable your device's game mode",
            "Keep the device cool and charged",
            "Choose a nearby in-game server"
        )
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = tasks.size
            progressTintList = android.content.res.ColorStateList.valueOf(color(R.color.neon_green))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.border))
        }
        val percentage = text("GAME READY  ·  0%", 12, R.color.neon_green, true)
        checklist.addView(percentage, matchWrap(dp(28)))
        checklist.addView(progress, matchWrap(dp(7)))
        val checkBoxes = mutableListOf<CheckBox>()
        tasks.forEach { task ->
            val check = CheckBox(context).apply {
                text = task
                setTextColor(color(R.color.text_primary))
                buttonTintList = android.content.res.ColorStateList.valueOf(color(R.color.crimson))
            }
            checkBoxes.add(check)
            checklist.addView(check, matchWrap(dp(44)))
            check.setOnCheckedChangeListener { _, _ ->
                val done = checkBoxes.count { it.isChecked }
                val target = (done * 100 / tasks.size)
                ValueAnimator.ofInt(progress.progress, target).apply {
                    duration = 280L
                    addUpdateListener { progress.progress = it.animatedValue as Int }
                    start()
                }
                percentage.text = "GAME READY  ·  $target%"
            }
        }
        root.addView(text(
            "Network probes, sensitivity suggestions, and game-readiness checks are informational; results vary by device, network, and game.",
            12,
            R.color.text_secondary
        ), matchWrap(dp(58)))
    }

    private fun addHeader(parent: LinearLayout, title: String, subtitle: String) {
        parent.addView(text(title, 20, R.color.crimson, true), matchWrap(dp(32)))
        parent.addView(text(subtitle, 10, R.color.text_secondary, true), matchWrap(dp(24)))
    }

    private fun addSectionTitle(parent: LinearLayout, label: String) {
        parent.addView(text(label, 10, R.color.crimson, true).apply {
            letterSpacing = 0.1f
            setPadding(0, dp(15), 0, dp(7))
        }, matchWrap(dp(42)))
    }

    private fun card(parent: LinearLayout, minimumHeight: Int = 0): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(14), dp(15), dp(14))
            background = background(R.color.surface, R.color.border, 16)
            if (minimumHeight > 0) this.minimumHeight = dp(minimumHeight)
        }.also { parent.addView(it, matchWrap()) }
    }

    private fun addSelector(parent: LinearLayout, label: String, options: List<String>): Spinner {
        parent.addView(text(label, 10, R.color.text_secondary, true), matchWrap(dp(22)))
        val spinner = Spinner(context).apply {
            tag = label
            adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, options) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (super.getView(position, convertView, parent) as TextView).apply {
                        setTextColor(color(R.color.text_primary))
                        textSize = 14f
                    }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                    (super.getDropDownView(position, convertView, parent) as TextView).apply {
                        setTextColor(color(R.color.text_primary))
                        setBackgroundColor(color(R.color.surface))
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                    }
            }.also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            background = background(R.color.surface_alt, R.color.border, 10)
            setPadding(dp(10), 0, dp(10), 0)
        }
        parent.addView(spinner, matchWrap(dp(46)))
        return spinner
    }

    private fun selectorListener(onSelected: () -> Unit) =
        object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) = onSelected()
        }

    private fun button(label: String): Button = Button(context).apply {
        text = label
        textSize = 12f
        setTextColor(Color.WHITE)
        background = background(R.color.crimson, R.color.crimson, 12)
        stateListAnimator = null
    }

    private fun text(label: CharSequence, size: Int, colorId: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = label
            textSize = size.toFloat()
            setTextColor(color(colorId))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun background(fill: Int, stroke: Int, radiusDp: Int) = GradientDrawable().apply {
        setColor(color(fill))
        cornerRadius = dp(radiusDp).toFloat()
        setStroke(dp(1), color(stroke))
    }

    private fun animateProgress(bar: ProgressBar?, value: Int) {
        bar ?: return
        ValueAnimator.ofInt(bar.progress, value).apply {
            duration = 400L
            addUpdateListener { bar.progress = it.animatedValue as Int }
            start()
        }
    }

    private fun clipboard(value: String) {
        val manager = context.getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("PulsePing", value))
    }

    private fun toast(value: String) = Toast.makeText(context, value, Toast.LENGTH_SHORT).show()
    private fun color(resource: Int): Int = androidx.core.content.ContextCompat.getColor(context, resource)
    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).roundToInt()
    private fun matchWrap(height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

    companion object {
        private const val REQUIRED_SWIPES = 3
    }
}
