package lk.pulseping

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class SuitePages(private val context: Context) {
    val sensiPage: ScrollView = page()
    val toolsPage: ScrollView = page()
    private val resultValues = linkedMapOf<String, Int>()
    private val metricAnimators = mutableMapOf<String, ValueAnimator>()
    private val barAnimators = mutableMapOf<ProgressBar, ValueAnimator>()
    private lateinit var gameModeButtons: List<TextView>
    private lateinit var brandSpinner: Spinner
    private lateinit var ramSpinner: Spinner
    private lateinit var refreshSpinner: Spinner
    private lateinit var playstyleSpinner: Spinner
    private lateinit var tierButtons: List<TextView>
    private var selectedTier = TIER_NORMAL
    private var selectedGameMode = FREE_FIRE_MODE
    private var lastDpi: Int? = null
    private var lastFireSize: Int? = null

    init {
        buildSensiPage()
        buildToolsPage()
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
        addHeader(root, "SENSI LAB", "BUILD A DEVICE-MATCHED STARTING PROFILE.")
        val profile = card(root)
        profile.addView(text("GAME MODE", 10, R.color.text_secondary, true), matchWrap(dp(22)))
        val modeBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        gameModeButtons = listOf("Free Fire (0–200)", "PUBG Mobile").mapIndexed { index, label ->
            text(label, 12, R.color.text_secondary, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(12), dp(6), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedGameMode = index
                    updateGameModeButtons()
                    recalculateSensitivity()
                }
                modeBar.addView(this, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                })
            }
        }
        profile.addView(modeBar, matchWrap(dp(54)))
        brandSpinner = addSelector(
            profile,
            "DEVICE BRAND",
            listOf("Infinix", "Samsung", "Xiaomi / Redmi", "Realme", "Vivo", "iPhone", "Huawei / Honor", "Other")
        )
        ramSpinner = addSelector(profile, "RAM", listOf("2GB", "3GB", "4GB", "6GB", "8GB", "12GB+"))
        refreshSpinner = addSelector(profile, "REFRESH RATE", listOf("60Hz", "90Hz", "120Hz", "144Hz"))
        playstyleSpinner = addSelector(
            profile,
            "PLAYSTYLE",
            listOf(
                "One-Tap Headshot (Desert Eagle / M1887)",
                "SMG Rush Drag (MP40 / UMP)",
                "Balanced / All-Rounder",
                "Sniper / Long Range"
            )
        )
        listOf(brandSpinner, ramSpinner, refreshSpinner, playstyleSpinner)
            .forEach { it.onItemSelectedListener = selectorListener { recalculateSensitivity() } }

        addSectionTitle(root, "SENSITIVITY INTENSITY")
        val tierCard = card(root)
        tierCard.addView(text("Choose how fast or controlled your profile feels.", 12, R.color.text_secondary), matchWrap(dp(28)))
        val tierBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        tierButtons = listOf(
            TIER_HIGH to "HIGH\nFast One-Tap",
            TIER_NORMAL to "NORMAL\nBalanced",
            TIER_LOW to "LOW\nPrecision Control"
        ).map { (tier, label) ->
            text(label, 10, R.color.text_secondary, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(10), dp(4), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedTier = tier
                    updateTierButtons()
                    recalculateSensitivity()
                }
                tierBar.addView(this, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                })
            }
        }
        tierCard.addView(tierBar, matchWrap())

        addSectionTitle(root, "STARTING SENSITIVITY PROFILE")
        val resultsCard = card(root)
        listOf("General", "Red Dot", "2x Scope", "4x Scope", "Sniper Scope", "Free Look").forEach { label ->
            addSensitivityRow(resultsCard, label)
        }
        val dpiValue = text("Safe recommended DPI: 0", 13, R.color.text_primary, true).apply { tag = "DPI" }
        resultsCard.addView(dpiValue, matchWrap(dp(34)))
        val fireSize = text("Optimal fire button size: 0%", 13, R.color.text_primary, true).apply { tag = "FIRE" }
        resultsCard.addView(fireSize, matchWrap(dp(30)))
        val copyButton = button("COPY PROFILE")
        resultsCard.addView(copyButton, matchWrap(dp(48)))
        copyButton.setOnClickListener {
            clipboard(profileSummary())
            toast("Sensitivity profile copied")
        }
        val saveCard = card(root)
        addSectionTitle(saveCard, "SAVED PROFILES")
        (1..3).forEach { slot ->
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            val save = button("SAVE SLOT $slot")
            val load = button("LOAD SLOT $slot")
            actions.addView(save, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(5) })
            actions.addView(load, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginStart = dp(5) })
            saveCard.addView(actions, matchWrap(dp(52)))
            save.setOnClickListener { saveProfile(slot) }
            load.setOnClickListener { loadProfile(slot) }
        }
        updateGameModeButtons()
        updateTierButtons()
        recalculateSensitivity()
    }

    private fun recalculateSensitivity() {
        val gameMax = if (selectedGameMode == PUBG_MODE) 300 else 200
        val tierRange = when (selectedTier) {
            TIER_HIGH -> if (gameMax == 200) 185..200 else 270..300
            TIER_LOW -> if (gameMax == 200) 130..155 else 195..232
            else -> if (gameMax == 200) 160..180 else 240..270
        }
        val tierBase = when (selectedTier) {
            TIER_HIGH -> if (gameMax == 200) 192 else 288
            TIER_LOW -> if (gameMax == 200) 143 else 215
            else -> if (gameMax == 200) 170 else 255
        }
        val brandAdjustment = when (brandSpinner.selectedItemPosition) {
            1, 5 -> -2
            6 -> -1
            else -> 0
        }
        val ramAdjustment = (ramSpinner.selectedItemPosition - 2).coerceIn(-2, 3)
        val refreshAdjustment = refreshSpinner.selectedItemPosition * 2
        val playstyleAdjustment = when (playstyleSpinner.selectedItemPosition) {
            0 -> 4
            1 -> 2
            3 -> -5
            else -> 0
        }
        val base = (tierBase + brandAdjustment + ramAdjustment + refreshAdjustment + playstyleAdjustment)
            .coerceIn(tierRange.first, tierRange.last)
        val multipliers = linkedMapOf(
            "General" to 1f,
            "Red Dot" to 0.94f,
            "2x Scope" to 0.82f,
            "4x Scope" to 0.68f,
            "Sniper Scope" to 0.46f,
            "Free Look" to 0.72f
        )
        val root = content(sensiPage)
        root.findViewWithTag<ProgressBar>("BAR:General")?.max = gameMax
        multipliers.forEach { (label, multiplier) ->
            val value = (base * multiplier).roundToInt().coerceIn(0, gameMax)
            resultValues[label] = value
            val bar = root.findViewWithTag<ProgressBar>("BAR:$label")
            bar?.max = gameMax
            val valueText = root.findViewWithTag<TextView>("VALUE:$label")
            animateProgress(bar, value)
            valueText?.text = value.toString()
        }
        val dpi = (when (selectedTier) {
            TIER_HIGH -> 620
            TIER_LOW -> 440
            else -> 530
        } + refreshSpinner.selectedItemPosition * 15 + ramSpinner.selectedItemPosition * 4)
            .coerceIn(400, 720)
        val fire = (when (selectedTier) {
            TIER_HIGH -> 48
            TIER_LOW -> 55
            else -> 52
        } + if (playstyleSpinner.selectedItemPosition == 1) -2 else 0)
            .coerceIn(35, 60)
        animateMetric(root.findViewWithTag<TextView>("DPI"), "dpi", lastDpi, dpi) {
            "Safe recommended DPI: $it"
        }
        animateMetric(root.findViewWithTag<TextView>("FIRE"), "fire", lastFireSize, fire) {
            "Optimal fire button size: $it%"
        }
        lastDpi = dpi
        lastFireSize = fire
    }

    private fun animateMetric(
        view: TextView?,
        key: String,
        previous: Int?,
        destination: Int,
        label: (Int) -> String
    ) {
        view ?: return
        metricAnimators.remove(key)?.cancel()
        if (previous == null || previous == destination) {
            view.text = label(destination)
            return
        }
        metricAnimators[key] = ValueAnimator.ofInt(previous, destination).apply {
            duration = 350L
            addUpdateListener { view.text = label(it.animatedValue as Int) }
            start()
        }
    }

    private fun updateTierButtons() {
        tierButtons.forEachIndexed { index, pill ->
            val tier = listOf(TIER_HIGH, TIER_NORMAL, TIER_LOW)[index]
            val active = selectedTier == tier
            pill.setTextColor(color(if (active) R.color.text_primary else R.color.text_secondary))
            pill.background = background(
                if (active) R.color.crimson else R.color.surface_alt,
                if (active) R.color.crimson else R.color.border,
                12
            )
        }
    }

    private fun updateGameModeButtons() {
        gameModeButtons.forEachIndexed { index, pill ->
            val active = selectedGameMode == index
            pill.setTextColor(color(if (active) R.color.text_primary else R.color.text_secondary))
            pill.background = background(
                if (active) R.color.crimson else R.color.surface_alt,
                if (active) R.color.crimson else R.color.border,
                12
            )
        }
    }

    private fun profileSummary(): String = buildString {
        appendLine("PulsePing ${gameModeButtons[selectedGameMode].text} · $selectedTier")
        appendLine("Device: ${brandSpinner.selectedItem} · ${ramSpinner.selectedItem} · ${refreshSpinner.selectedItem}")
        appendLine("Playstyle: ${playstyleSpinner.selectedItem}")
        resultValues.forEach { (name, value) -> appendLine("$name: $value") }
        appendLine(content(sensiPage).findViewWithTag<TextView>("DPI")?.text.orEmpty())
        append(content(sensiPage).findViewWithTag<TextView>("FIRE")?.text.orEmpty())
    }

    private fun saveProfile(slot: Int) {
        context.getSharedPreferences(SENSI_PREFERENCES, Context.MODE_PRIVATE).edit()
            .putBoolean("slot_$slot_saved", true)
            .putInt("slot_$slot_game", selectedGameMode)
            .putInt("slot_$slot_brand", brandSpinner.selectedItemPosition)
            .putInt("slot_$slot_ram", ramSpinner.selectedItemPosition)
            .putInt("slot_$slot_refresh", refreshSpinner.selectedItemPosition)
            .putInt("slot_$slot_playstyle", playstyleSpinner.selectedItemPosition)
            .putString("slot_$slot_tier", selectedTier)
            .apply()
        toast("Profile saved to slot $slot")
    }

    private fun loadProfile(slot: Int) {
        val saved = context.getSharedPreferences(SENSI_PREFERENCES, Context.MODE_PRIVATE)
        if (!saved.getBoolean("slot_${slot}_saved", false)) {
            toast("Slot $slot is empty")
            return
        }
        selectedGameMode = saved.getInt("slot_${slot}_game", FREE_FIRE_MODE).coerceIn(FREE_FIRE_MODE, PUBG_MODE)
        updateGameModeButtons()
        brandSpinner.setSelection(saved.getInt("slot_${slot}_brand", 0))
        ramSpinner.setSelection(saved.getInt("slot_${slot}_ram", 2))
        refreshSpinner.setSelection(saved.getInt("slot_${slot}_refresh", 0))
        playstyleSpinner.setSelection(saved.getInt("slot_${slot}_playstyle", 2))
        selectedTier = saved.getString("slot_${slot}_tier", TIER_NORMAL) ?: TIER_NORMAL
        updateTierButtons()
        recalculateSensitivity()
        toast("Profile loaded from slot $slot")
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
        title.addView(text("0", 13, R.color.neon_green, true).apply { tag = "VALUE:$label" })
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
        barAnimators.remove(bar)?.cancel()
        barAnimators[bar] = ValueAnimator.ofInt(bar.progress, value).apply {
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
        private const val SENSI_PREFERENCES = "pulseping_sensitivity"
        private const val FREE_FIRE_MODE = 0
        private const val PUBG_MODE = 1
        private const val TIER_HIGH = "HIGH"
        private const val TIER_NORMAL = "NORMAL"
        private const val TIER_LOW = "LOW"
    }
}
