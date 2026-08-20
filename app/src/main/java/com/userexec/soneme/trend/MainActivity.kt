package com.userexec.soneme.trend

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import com.userexec.soneme.trend.analysis.TrendAnalysis
import com.userexec.soneme.trend.data.RegistryStore
import com.userexec.soneme.trend.data.TrendRepository
import com.userexec.soneme.trend.model.*
import com.userexec.soneme.trend.time.TimeMath
import com.userexec.soneme.trend.ui.ChartBuilder
import com.userexec.soneme.trend.ui.TrendChartView
import com.userexec.soneme.trend.ui.UiFactory
import com.userexec.soneme.trend.ui.UiFactory.dp
import java.math.BigDecimal
import java.time.*
import java.util.Locale

class MainActivity : SonimActivity() {
    companion object {
        private const val PICK_TREE = 1200
        private const val LINE_BLUE = 0xFF4F6F8F.toInt()
        private const val HIGHLIGHT_BLUE = 0xFF0070E0.toInt()
        private val CORR_LINES = intArrayOf(0xFF4F6F8F.toInt(), 0xFFB07156.toInt(), 0xFF7B9E72.toInt(), 0xFFAB4E68.toInt())
        private val CORR_HIGHLIGHTS = intArrayOf(0xFF0070E0.toInt(), 0xFFFF5005.toInt(), 0xFF3FFF0F.toInt(), 0xFFFA0047.toInt())
    }

    private enum class Screen { SETUP, DATA, ANALYSES, CORRELATIONS, DATUM_SETUP, DATUM, DATUM_ADD, DATUM_DATE_TIME, ANALYSIS, CORRELATION_SETUP, CORRELATION }
    private enum class DatumSetupMode { NEW, EDIT, RECOVERY }
    private enum class CorrelationSetupOrigin { CORRELATIONS, CORRELATION }
    private enum class DateTimePart { YEAR, MONTH, DAY, HOUR, MINUTE }

    private lateinit var repository: TrendRepository
    private lateinit var registry: RegistryStore
    private var screen = Screen.SETUP
    private var currentDatumUid: String? = null
    private var currentCorrelationUid: String? = null
    private var selectedDatumUid: String? = null
    private var selectedCorrelationUid: String? = null
    private var selectedBucket: BucketKey? = null
    private var selectedDataRowIndex: Int = -1
    private var setupMode = DatumSetupMode.NEW
    private var currentRange: ChartRange = ChartRange.ALL_TIME
    private var selectedSeries = 0
    private var selectedSeriesUid: String? = null
    private var selectedChartPoint: Int? = null
    private var addLocalDateTime: LocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)
    private var addValueDraft: String = ""
    private var pickerLocalDateTime: LocalDateTime = addLocalDateTime
    private var pickerPart: DateTimePart = DateTimePart.YEAR
    private var pickerValueViews: MutableMap<DateTimePart, TextView> = linkedMapOf()
    private var correlationSetupOrigin = CorrelationSetupOrigin.CORRELATIONS
    private var rangeInitialized = false
    private var softKeysSuppressed = false

    // Currently displayed form controls. Dynamic screens keep state only long enough to act on a softkey.
    private var datumNameField: EditText? = null
    private var datumFilenameField: EditText? = null
    private var datumUnitField: EditText? = null
    private var datumBasisSpinner: Spinner? = null
    private var datumGoalField: EditText? = null
    private var addValueField: EditText? = null
    private var quickValueField: EditText? = null
    private var correlationNameField: EditText? = null
    private var correlationChecks: MutableMap<String, CheckBox> = linkedMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TrendRepository(this)
        registry = RegistryStore(this)
        if (repository.configuredStorage() == null) showSetup() else showData()
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized && screen != Screen.SETUP && repository.configuredStorage() == null) {
            repository.clearStorage()
            showSetup()
        }
    }

    private fun softKeyActions(): Triple<Pair<Int, String>, Pair<Int, String>, Pair<Int, String>> {
        fun empty() = 0 to ""
        return when (screen) {
            Screen.SETUP -> Triple(101 to "Exit", empty(), 102 to "Set up")
            Screen.DATA -> {
                val state = registry.load().datums.sortedBy { it.order }
                val i = state.indexOfFirst { it.uid == selectedDatumUid }
                Triple(if (i >= 0) 201 to "Delete" else empty(), if (i > 0) 202 to "Move up" else empty(), 203 to "New")
            }
            Screen.ANALYSES -> Triple(empty(), empty(), empty())
            Screen.CORRELATIONS -> {
                val state = registry.load().correlations.sortedBy { it.order }
                val i = state.indexOfFirst { it.uid == selectedCorrelationUid }
                Triple(if (i >= 0) 301 to "Delete" else empty(), if (i > 0) 302 to "Move up" else empty(), 303 to "New")
            }
            Screen.DATUM_SETUP -> Triple(401 to "Cancel", empty(), 402 to "Save")
            Screen.DATUM -> {
                if (quickValueField?.hasFocus() == true) {
                    val valid = decimalOrNull(quickValueField?.text?.toString(), false) != null
                    if (valid) Triple(501 to "Decrement", 502 to "Set", 503 to "Increment")
                    else Triple(empty(), 505 to "Edit", 506 to "New point")
                } else Triple(if (selectedBucket != null) 504 to "Remove" else empty(), 505 to "Edit", 506 to "New point")
            }
            Screen.DATUM_ADD -> Triple(601 to "Cancel", empty(), 602 to "Save")
            Screen.DATUM_DATE_TIME -> Triple(611 to "Cancel", empty(), 612 to "Done")
            Screen.ANALYSIS -> Triple(empty(), empty(), 701 to "Top")
            Screen.CORRELATION_SETUP -> {
                val validName = !correlationNameField?.text?.toString()?.trim().isNullOrBlank()
                val checked = correlationChecks.values.count { it.isChecked && it.isEnabled }
                Triple(empty(), empty(), if (validName && checked in 2..4) 802 to "Save" else empty())
            }
            Screen.CORRELATION -> Triple(901 to "Edit", empty(), 902 to "Line")
        }
    }

    override fun softKeyLabels(): Triple<String, String, String> {
        if (!::registry.isInitialized || softKeysSuppressed) return Triple("", "", "")
        val a = softKeyActions()
        return Triple(a.first.second, a.second.second, a.third.second)
    }

    override fun handleSoftKey(slot: SoftKeySlot) {
        val a = softKeyActions()
        val id = when (slot) {
            SoftKeySlot.LEFT -> a.first.first
            SoftKeySlot.CENTER -> a.second.first
            SoftKeySlot.RIGHT -> a.third.first
        }
        when (id) {
            101 -> finish()
            102 -> pickStorage()
            201 -> deleteSelectedDatum()
            202 -> selectedDatumUid?.let { registry.moveDatumUp(it); showData() }
            203 -> { setupMode = DatumSetupMode.NEW; currentDatumUid = null; showDatumSetup() }
            301 -> deleteSelectedCorrelation()
            302 -> selectedCorrelationUid?.let { registry.moveCorrelationUp(it); showCorrelations() }
            303 -> { currentCorrelationUid = null; correlationSetupOrigin = CorrelationSetupOrigin.CORRELATIONS; showCorrelationSetup() }
            401 -> returnFromDatumSetup()
            402 -> saveDatumSetup()
            501 -> mutateQuick(TrendRepository.Mutation.DECREMENT)
            502 -> mutateQuick(TrendRepository.Mutation.SET)
            503 -> mutateQuick(TrendRepository.Mutation.INCREMENT)
            504 -> removeSelectedPoint()
            505 -> { setupMode = DatumSetupMode.EDIT; showDatumSetup() }
            506 -> showDatumAdd(reset = true)
            601 -> showDatum()
            602 -> saveDatumAdd()
            611 -> showDatumAdd(reset = false)
            612 -> { addLocalDateTime = pickerLocalDateTime; showDatumAdd(reset = false) }
            701 -> scrollAnalysisTop()
            801 -> showCorrelations()
            802 -> saveCorrelationSetup()
            901 -> { correlationSetupOrigin = CorrelationSetupOrigin.CORRELATION; showCorrelationSetup() }
            902 -> chooseCorrelationLine()
        }
    }

    override fun onBackPressed() {
        when (screen) {
            Screen.SETUP -> finish()
            Screen.DATA -> finish()
            Screen.ANALYSES -> showData()
            Screen.CORRELATIONS -> showAnalyses()
            Screen.DATUM_SETUP -> returnFromDatumSetup()
            Screen.DATUM -> showData()
            Screen.DATUM_ADD -> showDatum()
            Screen.DATUM_DATE_TIME -> showDatumAdd(reset = false)
            Screen.ANALYSIS -> showAnalyses()
            Screen.CORRELATION_SETUP -> returnFromCorrelationSetup()
            Screen.CORRELATION -> showCorrelations()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (handleNumericKeypadSymbol(event)) return true

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (screen) {
                Screen.DATA -> when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> return true
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { showAnalyses(); return true }
                    else -> Unit
                }
                Screen.ANALYSES -> when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { showData(); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { showCorrelations(); return true }
                    else -> Unit
                }
                Screen.CORRELATIONS -> when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { showAnalyses(); return true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> return true
                    else -> Unit
                }
                else -> Unit
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN && screen == Screen.DATUM_DATE_TIME &&
            event.keyCode in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            adjustDateTimePart(if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1)
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN && screen == Screen.DATUM) {
            val quick = quickValueField
            if (event.keyCode == KeyEvent.KEYCODE_BACK && quick?.hasFocus() == true) {
                if (quick.text.isEmpty()) {
                    showData()
                    return true
                }
                val start = quick.selectionStart.coerceAtLeast(0)
                val end = quick.selectionEnd.coerceAtLeast(0)
                if (start != end) quick.text.delete(minOf(start, end), maxOf(start, end))
                else if (start > 0) quick.text.delete(start - 1, start)
                updateSonimSoftKeys()
                return true
            }
            val digit = when (event.keyCode) {
                KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'; KeyEvent.KEYCODE_2 -> '2'; KeyEvent.KEYCODE_3 -> '3'; KeyEvent.KEYCODE_4 -> '4'
                KeyEvent.KEYCODE_5 -> '5'; KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'; KeyEvent.KEYCODE_8 -> '8'; KeyEvent.KEYCODE_9 -> '9'
                else -> null
            }
            if (digit != null && quick != null && !quick.hasFocus()) {
                quick.requestFocus()
                quick.setSelection(quick.text.length)
                quick.append(digit.toString())
                updateSonimSoftKeys()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun handleNumericKeypadSymbol(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_STAR && event.keyCode != KeyEvent.KEYCODE_POUND) return false
        val field = currentFocus as? EditText ?: return false
        if (field !== quickValueField && field !== addValueField && field !== datumGoalField) return false

        // Consume both DOWN and UP so the Sonim input method never sees these as mode/symbol keys.
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return true

        when (event.keyCode) {
            KeyEvent.KEYCODE_STAR -> {
                val editable = field.text
                val start = minOf(field.selectionStart.coerceAtLeast(0), field.selectionEnd.coerceAtLeast(0))
                val end = maxOf(field.selectionStart.coerceAtLeast(0), field.selectionEnd.coerceAtLeast(0))
                val outsideSelection = editable.substring(0, start) + editable.substring(end)
                if (!outsideSelection.contains('.')) {
                    var insertAt = start
                    var replaceEnd = end
                    // Keep a leading sign at the beginning even if the current selection touches it.
                    if (editable.startsWith("-") && insertAt == 0) {
                        insertAt = 1
                        replaceEnd = maxOf(replaceEnd, 1)
                    }
                    editable.replace(insertAt, replaceEnd, ".")
                    field.setSelection((insertAt + 1).coerceAtMost(editable.length))
                }
            }
            KeyEvent.KEYCODE_POUND -> {
                val editable = field.text
                val start = minOf(field.selectionStart.coerceAtLeast(0), field.selectionEnd.coerceAtLeast(0))
                val end = maxOf(field.selectionStart.coerceAtLeast(0), field.selectionEnd.coerceAtLeast(0))
                if (editable.startsWith("-")) {
                    editable.delete(0, 1)
                    field.setSelection(
                        (start - 1).coerceAtLeast(0).coerceAtMost(editable.length),
                        (end - 1).coerceAtLeast(0).coerceAtMost(editable.length)
                    )
                } else {
                    editable.insert(0, "-")
                    field.setSelection(
                        (start + 1).coerceAtMost(editable.length),
                        (end + 1).coerceAtMost(editable.length)
                    )
                }
            }
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_TREE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                repository.configureSelectedTree(uri)
                showData()
            } catch (e: Exception) {
                toast(e.message ?: "Could not set up storage")
                showSetup()
            }
        }
    }

    private fun pickStorage() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, PICK_TREE)
    }

    private fun showSetup() {
        screen = Screen.SETUP
        selectedDatumUid = null; selectedCorrelationUid = null
        val root = UiFactory.vertical(this)
        root.addView(UiFactory.heading(this, "Soneme Trend"))
        root.addView(UiFactory.centeredMessage(this, "No SonemeTrend folder is set up.\n\nChoose a folder or storage root. Trend will use an existing SonemeTrend folder there, or create one."), LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        updateSonimSoftKeys()
    }

    private fun showData() {
        screen = Screen.DATA
        currentDatumUid = null; selectedBucket = null; quickValueField = null
        val state = registry.load(); val datums = state.datums.sortedBy { it.order }
        if (selectedDatumUid !in datums.map { it.uid }) selectedDatumUid = datums.firstOrNull()?.uid
        val root = UiFactory.vertical(this, scroll = true, horizontalPadding = 0) as ScrollView
        val content = root.tag as LinearLayout
        content.addView(UiFactory.tabs(this, 0))
        if (datums.isEmpty()) content.addView(UiFactory.centeredMessage(this, "No data sets yet.\nChoose New to create one."))
        datums.forEach { d ->
            val loaded = try { repository.read(d.csvFilename) } catch (_: Exception) { null }
            val pts = loaded?.let(TimeMath::logicalPoints)
            val count = pts?.size ?: 0
            val row = if (loaded == null) {
                UiFactory.dataRow(this, d.name, null, null, "${d.csvFilename} missing or unreadable")
            } else {
                UiFactory.dataRow(
                    this,
                    d.name,
                    "$count data ${if (count == 1) "point" else "points"}",
                    pts?.lastOrNull()?.let { TimeMath.pretty(it.bucket) } ?: "No entries"
                )
            }
            row.setOnFocusChangeListener { _, has -> if (has) { selectedDatumUid = d.uid; updateSonimSoftKeys() } }
            row.setOnClickListener {
                selectedDatumUid = d.uid; currentDatumUid = d.uid
                if (repository.fileIsValid(d.csvFilename)) showDatum() else { setupMode = DatumSetupMode.RECOVERY; showDatumSetup() }
            }
            content.addView(row)
            content.addView(UiFactory.divider(this))
        }
        setContentView(root)
        root.post { findAndFocusRow(root, datums.indexOfFirst { it.uid == selectedDatumUid }) }
        updateSonimSoftKeys()
    }

    private fun showAnalyses() {
        screen = Screen.ANALYSES
        val datums = registry.load().datums.sortedBy { it.order }
        val root = UiFactory.vertical(this, scroll = true, horizontalPadding = 0) as ScrollView; val content = root.tag as LinearLayout
        content.addView(UiFactory.tabs(this, 1))
        if (datums.isEmpty()) content.addView(UiFactory.centeredMessage(this, "Analyses appear here for configured data sets."))
        var firstFocusable: View? = null
        var preferredFocus: View? = null
        datums.forEach { d ->
            val valid = repository.fileIsValid(d.csvFilename)
            val row = UiFactory.row(this, d.name, if (valid) null else "Data unavailable", false).apply {
                isEnabled = valid; isFocusable = valid; if (!valid) alpha = 0.45f
            }
            if (valid) {
                if (firstFocusable == null) firstFocusable = row
                if (d.uid == selectedDatumUid) preferredFocus = row
                row.setOnFocusChangeListener { _, has -> if (has) selectedDatumUid = d.uid }
                row.setOnClickListener { selectedDatumUid = d.uid; currentDatumUid = d.uid; rangeInitialized = false; showAnalysis() }
            }
            content.addView(row)
            content.addView(UiFactory.divider(this))
        }
        setContentView(root)
        root.post { (preferredFocus ?: firstFocusable)?.requestFocus() }
        updateSonimSoftKeys()
    }

    private fun showCorrelations() {
        screen = Screen.CORRELATIONS
        currentCorrelationUid = null
        val state = registry.load(); val correlations = state.correlations.sortedBy { it.order }
        if (selectedCorrelationUid !in correlations.map { it.uid }) selectedCorrelationUid = correlations.firstOrNull()?.uid
        val datumMap = state.datums.associateBy { it.uid }
        val root = UiFactory.vertical(this, scroll = true, horizontalPadding = 0) as ScrollView; val content = root.tag as LinearLayout
        content.addView(UiFactory.tabs(this, 2))
        if (correlations.isEmpty()) content.addView(UiFactory.centeredMessage(this, "No correlations yet.\nChoose New to compare data sets."))
        correlations.forEach { c ->
            val members = c.datumUids.mapNotNull { datumMap[it] }
            val validMembers = members.filter { repository.fileIsValid(it.csvFilename) }
            val statusByUid = members.associate { it.uid to repository.fileIsValid(it.csvFilename) }
            val listedMembers = members.map { it.name to (statusByUid[it.uid] != true) }
            val row = UiFactory.correlationRow(this, c.name, listedMembers)
            row.setOnFocusChangeListener { _, has -> if (has) { selectedCorrelationUid = c.uid; updateSonimSoftKeys() } }
            row.setOnClickListener {
                selectedCorrelationUid = c.uid; currentCorrelationUid = c.uid; rangeInitialized = false
                if (validMembers.size >= 2) showCorrelation() else { correlationSetupOrigin = CorrelationSetupOrigin.CORRELATIONS; showCorrelationSetup() }
            }
            content.addView(row)
            content.addView(UiFactory.divider(this))
        }
        setContentView(root)
        root.post { findAndFocusRow(root, correlations.indexOfFirst { it.uid == selectedCorrelationUid }) }
        updateSonimSoftKeys()
    }

    private fun findAndFocusRow(root: ScrollView, listIndex: Int) {
        if (listIndex < 0) return
        val content = root.getChildAt(0) as? LinearLayout ?: return
        var focusableIndex = 0
        for (i in 1 until content.childCount) { // child 0 is the non-focusable tab bar
            val child = content.getChildAt(i)
            if (!child.isFocusable) continue
            if (focusableIndex == listIndex) {
                child.requestFocus()
                return
            }
            focusableIndex++
        }
    }

    private fun showDatumSetup() {
        screen = Screen.DATUM_SETUP
        val state = registry.load(); val existing = currentDatumUid?.let { uid -> state.datums.firstOrNull { it.uid == uid } }
        if (setupMode != DatumSetupMode.NEW && existing == null) { showData(); return }
        val root = UiFactory.vertical(this, scroll = true) as ScrollView; val content = root.tag as LinearLayout
        datumNameField = UiFactory.edit(this, "Name").apply { setText(existing?.name ?: "") }
        datumFilenameField = UiFactory.edit(this, "example.csv").apply { setText(existing?.csvFilename ?: "") }
        datumUnitField = UiFactory.edit(this, "Unit")
        datumGoalField = UiFactory.edit(this, "Optional").apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED }
        datumBasisSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, TimeBasis.entries.map { it.perLabel })
        }

        if (setupMode == DatumSetupMode.NEW) {
            var previewAttached = false
            datumFilenameField?.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val filename = normalizeCsvFilename(s?.toString().orEmpty())
                    val attachedElsewhere = registry.load().datums.any { it.csvFilename.equals(filename, true) }
                    val validExisting = filename.isNotBlank() && !attachedElsewhere && repository.exists(filename) && repository.fileIsValid(filename)
                    if (validExisting) {
                        val f = repository.read(filename)
                        datumUnitField?.setText(f.unit); datumBasisSpinner?.setSelection(f.timeBasis.ordinal); datumGoalField?.setText(f.goal?.let(TimeMath::formatNumber) ?: "")
                        datumUnitField?.isEnabled = false; datumBasisSpinner?.isEnabled = false; datumGoalField?.isEnabled = false
                        previewAttached = true
                    } else {
                        datumUnitField?.isEnabled = true; datumBasisSpinner?.isEnabled = true; datumGoalField?.isEnabled = true
                        if (previewAttached) { datumUnitField?.setText(""); datumBasisSpinner?.setSelection(0); datumGoalField?.setText("") }
                        previewAttached = false
                    }
                }
            })
        }

        val currentFile = existing?.takeIf { repository.fileIsValid(it.csvFilename) }?.let { repository.read(it.csvFilename) }
        if (currentFile != null) {
            datumUnitField?.setText(currentFile.unit); datumBasisSpinner?.setSelection(currentFile.timeBasis.ordinal)
            datumGoalField?.setText(currentFile.goal?.let(TimeMath::formatNumber) ?: "")
        }
        when (setupMode) {
            DatumSetupMode.NEW -> Unit
            DatumSetupMode.EDIT -> {
                datumFilenameField?.isEnabled = false; datumUnitField?.isEnabled = false; datumBasisSpinner?.isEnabled = false
            }
            DatumSetupMode.RECOVERY -> {
                datumNameField?.isEnabled = false; datumUnitField?.isEnabled = false; datumBasisSpinner?.isEnabled = false; datumGoalField?.isEnabled = false
            }
        }

        content.addView(UiFactory.labeled(this, "Name", datumNameField!!))
        content.addView(UiFactory.labeled(this, "CSV filename", datumFilenameField!!))
        if (setupMode != DatumSetupMode.RECOVERY) {
            content.addView(UiFactory.text(this, "Units, Y-axis", 15f, true))
            content.addView(UiFactory.text(this, "e.g. calories, repetitions, millimeters", 13.5f))
            content.addView(datumUnitField!!, LinearLayout.LayoutParams(-1, -2))
            content.addView(UiFactory.text(this, "Time basis per measurement, X-axis", 15f, true))
            content.addView(UiFactory.text(this, "Determines when a data point is incremented versus when a new data point is created.\nHow often do you plan to take measurements?", 13.5f))
            content.addView(datumBasisSpinner!!, LinearLayout.LayoutParams(-1, -2))
            content.addView(UiFactory.labeled(this, "Goal (optional)", datumGoalField!!))
            content.addView(UiFactory.text(this, "Examples:\nCalories per day\nMillimeters per hour\nRepetitions per week\nBooks per month", 14f))
        } else content.addView(UiFactory.text(this, "Choose a valid, unattached CSV file for this datum.", 15f))
        setContentView(root); updateSonimSoftKeys()
    }

    private fun saveDatumSetup() {
        val state = registry.load(); val name = datumNameField?.text?.toString()?.trim().orEmpty(); val filename = normalizeCsvFilename(datumFilenameField?.text?.toString().orEmpty())
        val existing = currentDatumUid?.let { uid -> state.datums.firstOrNull { it.uid == uid } }
        fun nameTaken(): Boolean = state.datums.any { it.uid != existing?.uid && it.name.equals(name, true) }
        fun fileAttachedToOther(): Boolean = state.datums.any { it.uid != existing?.uid && it.csvFilename.equals(filename, true) }
        if (name.isBlank() && setupMode != DatumSetupMode.RECOVERY) return toast("Name is required")
        if (setupMode != DatumSetupMode.RECOVERY && nameTaken()) return toast("That datum name is already in use")
        if (!repository.safeFilename(filename)) return toast("Enter a safe .csv filename")
        if (fileAttachedToOther()) return toast("That CSV is already attached to another datum")

        try {
            when (setupMode) {
                DatumSetupMode.NEW -> {
                    val fileExists = repository.exists(filename)
                    if (fileExists) {
                        if (!repository.fileIsValid(filename)) return toast("Existing CSV is invalid")
                    } else {
                        val unit = datumUnitField?.text?.toString()?.trim().orEmpty(); if (unit.isBlank()) return toast("Unit is required")
                        val basis = TimeBasis.entries[datumBasisSpinner?.selectedItemPosition ?: 0]
                        val goal = decimalOrNull(datumGoalField?.text?.toString(), allowBlank = true) ?: if (datumGoalField?.text?.toString().isNullOrBlank()) null else return toast("Goal must be numeric")
                        repository.create(filename, unit, basis, goal)
                    }
                    val d = registry.addDatum(name, filename); selectedDatumUid = d.uid; currentDatumUid = d.uid
                }
                DatumSetupMode.EDIT -> {
                    val d = existing ?: return showData()
                    val goalText = datumGoalField?.text?.toString().orEmpty()
                    val goal = decimalOrNull(goalText, allowBlank = true) ?: if (goalText.isBlank()) null else return toast("Goal must be numeric")
                    repository.updateGoal(d.csvFilename, goal) // re-reads first
                    registry.updateDatum(d.copy(name = name))
                }
                DatumSetupMode.RECOVERY -> {
                    val d = existing ?: return showData()
                    if (!repository.exists(filename) || !repository.fileIsValid(filename)) return toast("Invalid CSV")
                    registry.updateDatum(d.copy(csvFilename = filename)); selectedDatumUid = d.uid; currentDatumUid = d.uid
                }
            }
            showData()
        } catch (e: Exception) {
            toast(e.message ?: "Could not save datum")
            showData()
        }
    }

    private fun returnFromDatumSetup() {
        if (setupMode == DatumSetupMode.EDIT && currentDatumUid != null && registry.load().datums.any { it.uid == currentDatumUid }) showDatum() else showData()
    }

    private fun showDatum() {
        screen = Screen.DATUM
        val d = currentDatum() ?: return showData()
        val file = try { repository.read(d.csvFilename) } catch (_: Exception) { toast("CSV is missing or invalid"); setupMode = DatumSetupMode.RECOVERY; return showDatumSetup() }
        val points = TimeMath.logicalPoints(file)
        val root = UiFactory.vertical(this, scroll = true, horizontalPadding = 0) as ScrollView; val content = root.tag as LinearLayout
        content.addView(datumChartHeader(d.name, "${file.unit} ${file.timeBasis.perLabel}", points.size))

        if (points.size >= 2) {
            val previewPoints = points.takeLast(10)
            val input = inputFor(d, file, previewPoints, 0)
            val build = ChartBuilder.build(listOf(input), ChartRange.ALL_TIME, false)
            if (build != null) {
                content.addView(xRangeLabel(build))
                content.addView(chartView(build, false, 0, null, inspectable = false, range = null), LinearLayout.LayoutParams(-1, dp(128)))
            }
        } else content.addView(UiFactory.centeredMessage(this, "insufficient data to chart"))

        quickValueField = UiFactory.edit(this, "Value").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setOnFocusChangeListener { _, _ -> selectedBucket = null; updateSonimSoftKeys() }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSonimSoftKeys()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        content.addView(UiFactory.labeled(this, "Quick entry (${file.unit})", quickValueField!!))
        content.addView(UiFactory.divider(this))
        content.addView(UiFactory.text(this, "Data", 16f, true))
        selectedBucket = null
        val descending = points.asReversed()
        if (descending.isEmpty()) content.addView(UiFactory.text(this, "No data recorded yet", 14f))
        descending.forEachIndexed { index, p ->
            val row = UiFactory.pointRow(this, TimeMath.pretty(p.bucket), "${TimeMath.formatNumber(p.value)} ${file.unit}")
            row.setOnFocusChangeListener { _, has -> if (has) { selectedBucket = p.bucket; selectedDataRowIndex = index; updateSonimSoftKeys() } }
            row.setOnClickListener { selectedBucket = p.bucket; selectedDataRowIndex = index; updateSonimSoftKeys() }
            content.addView(row)
        }
        setContentView(root)
        root.post {
            if (descending.isNotEmpty() && selectedDataRowIndex >= descending.size) selectedDataRowIndex = descending.lastIndex
            if (selectedDataRowIndex in descending.indices) {
                // heading, metadata, chart/message, quick-entry container, divider, Data heading, then rows
                val firstRow = content.childCount - descending.size
                content.getChildAt(firstRow + selectedDataRowIndex)?.requestFocus()
            } else quickValueField?.requestFocus()
        }
        updateSonimSoftKeys()
    }

    private fun mutateQuick(mutation: TrendRepository.Mutation) {
        val d = currentDatum() ?: return showData(); val raw = quickValueField?.text?.toString().orEmpty(); val value = decimalOrNull(raw, false) ?: return toast("Enter a numeric value")
        try {
            val file = repository.read(d.csvFilename)
            val updated = repository.mutateBucket(d.csvFilename, Instant.now(), value, mutation)
            val basis = file.timeBasis.singular
            val operand = TimeMath.formatNumber(value); val result = TimeMath.formatNumber(updated)
            quickValueField?.setText(""); selectedDataRowIndex = -1; showDatum()
            toast(when (mutation) {
                TrendRepository.Mutation.DECREMENT -> "Current $basis decremented by $operand to $result."
                TrendRepository.Mutation.SET -> "Current $basis set to $result."
                TrendRepository.Mutation.INCREMENT -> "Current $basis incremented by $operand to $result."
            })
        } catch (_: Exception) { toast("CSV changed or became invalid"); showData() }
    }

    private fun removeSelectedPoint() {
        val d = currentDatum() ?: return showData(); val bucket = selectedBucket ?: return
        val point = try { TimeMath.logicalPoints(repository.read(d.csvFilename)).firstOrNull { it.bucket == bucket } } catch (_: Exception) { null } ?: return showData()
        AlertDialog.Builder(this).setTitle("Remove data point?").setMessage("Remove data point \"${TimeMath.formatNumber(point.value)}\" from ${TimeMath.pretty(bucket)}?")
            .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                try { repository.removeBucket(d.csvFilename, bucket); showDatum() } catch (_: Exception) { toast("CSV changed or became invalid"); showData() }
            }.show()
    }

    private fun showDatumAdd(reset: Boolean) {
        screen = Screen.DATUM_ADD
        val d = currentDatum() ?: return showData(); val file = try { repository.read(d.csvFilename) } catch (_: Exception) { return showData() }
        if (reset) {
            addLocalDateTime = LocalDateTime.now().withSecond(0).withNano(0)
            addValueDraft = ""
        }
        val root = UiFactory.vertical(this)
        root.addView(UiFactory.heading(this, "New point · ${d.name}"))
        val dateControl = UiFactory.focusableText(this, TimeMath.prettyLocalDateTime(addLocalDateTime)).apply {
            setOnClickListener {
                addValueDraft = addValueField?.text?.toString().orEmpty()
                showDateTimePicker()
            }
        }
        root.addView(UiFactory.labeled(this, "Date and time", dateControl))
        addValueField = UiFactory.edit(this, "${file.unit} value").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(addValueDraft)
            setSelection(text.length)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { addValueDraft = s?.toString().orEmpty() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(UiFactory.labeled(this, "Value", addValueField!!))
        setContentView(root)
        if (reset) addValueField?.requestFocus() else dateControl.requestFocus()
        updateSonimSoftKeys()
    }

    private fun showDateTimePicker() {
        screen = Screen.DATUM_DATE_TIME
        pickerLocalDateTime = addLocalDateTime
        pickerPart = DateTimePart.YEAR
        pickerValueViews = linkedMapOf()
        val root = UiFactory.vertical(this)
        root.addView(UiFactory.heading(this, "New point date/time"))
        root.addView(UiFactory.text(this, "Up/down select · Left/right change", 14f))
        DateTimePart.entries.forEach { part ->
            val row = UiFactory.pickerRow(this, dateTimePartLabel(part), dateTimePartValue(part))
            val valueView = row.getChildAt(1) as TextView
            pickerValueViews[part] = valueView
            row.setOnFocusChangeListener { _, has -> if (has) pickerPart = part }
            row.setOnClickListener { pickerPart = part }
            root.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)))
        }
        setContentView(root)
        root.post { root.getChildAt(2)?.requestFocus() }
        updateSonimSoftKeys()
    }

    private fun dateTimePartLabel(part: DateTimePart): String = when (part) {
        DateTimePart.YEAR -> "Year"
        DateTimePart.MONTH -> "Month"
        DateTimePart.DAY -> "Day"
        DateTimePart.HOUR -> "Hour"
        DateTimePart.MINUTE -> "Minute"
    }

    private fun dateTimePartValue(part: DateTimePart): String = when (part) {
        DateTimePart.YEAR -> pickerLocalDateTime.year.toString()
        DateTimePart.MONTH -> pickerLocalDateTime.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        DateTimePart.DAY -> pickerLocalDateTime.dayOfMonth.toString()
        DateTimePart.HOUR -> if (android.text.format.DateFormat.is24HourFormat(this)) {
            String.format(Locale.getDefault(), "%02d", pickerLocalDateTime.hour)
        } else {
            val h = ((pickerLocalDateTime.hour + 11) % 12) + 1
            val ap = if (pickerLocalDateTime.hour < 12) "AM" else "PM"
            "$h $ap"
        }
        DateTimePart.MINUTE -> String.format(Locale.getDefault(), "%02d", pickerLocalDateTime.minute)
    }

    private fun adjustDateTimePart(delta: Int) {
        val current = pickerLocalDateTime
        pickerLocalDateTime = when (pickerPart) {
            DateTimePart.YEAR -> {
                val year = (current.year + delta).coerceIn(1900, 9999)
                clampedDateTime(year, current.monthValue, current.dayOfMonth, current.hour, current.minute)
            }
            DateTimePart.MONTH -> {
                val month = (current.monthValue + delta).coerceIn(1, 12)
                clampedDateTime(current.year, month, current.dayOfMonth, current.hour, current.minute)
            }
            DateTimePart.DAY -> current.withDayOfMonth((current.dayOfMonth + delta).coerceIn(1, current.toLocalDate().lengthOfMonth()))
            DateTimePart.HOUR -> current.withHour((current.hour + delta).coerceIn(0, 23))
            DateTimePart.MINUTE -> current.withMinute((current.minute + delta).coerceIn(0, 59))
        }
        pickerValueViews.forEach { (part, view) -> view.text = dateTimePartValue(part) }
    }

    private fun clampedDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int): LocalDateTime {
        val maxDay = YearMonth.of(year, month).lengthOfMonth()
        return LocalDateTime.of(year, month, day.coerceAtMost(maxDay), hour, minute)
    }

    private fun saveDatumAdd() {
        val d = currentDatum() ?: return showData(); val raw = addValueField?.text?.toString().orEmpty(); val value = decimalOrNull(raw, false) ?: return toast("Enter a numeric value")
        val instant = addLocalDateTime.atZone(ZoneId.systemDefault()).toInstant()
        val file = try { repository.read(d.csvFilename) } catch (_: Exception) { toast("CSV changed or became invalid"); return showData() }
        val bucket = TimeMath.bucketFor(instant, file.timeBasis)
        val collision = TimeMath.logicalPoints(file).any { it.bucket == bucket }
        if (!collision) {
            try { repository.mutateBucket(d.csvFilename, instant, value, TrendRepository.Mutation.SET); showDatum() } catch (_: Exception) { toast("CSV changed or became invalid"); showData() }
            return
        }
        val existingValue = TimeMath.logicalPoints(file).first { it.bucket == bucket }.value
        val shown = TimeMath.formatNumber(value)
        val labels = arrayOf("Overwrite with $shown", "Increment by $shown", "Decrement by $shown")
        softKeysSuppressed = true
        updateSonimSoftKeys(force = true)
        val dialog = AlertDialog.Builder(this).setTitle("${TimeMath.pretty(bucket)} already has a value of ${TimeMath.formatNumber(existingValue)}.")
            .setItems(labels) { _, which ->
                val mutation = when (which) { 1 -> TrendRepository.Mutation.INCREMENT; 2 -> TrendRepository.Mutation.DECREMENT; else -> TrendRepository.Mutation.SET }
                try { repository.mutateBucket(d.csvFilename, instant, value, mutation); showDatum() } catch (_: Exception) { toast("CSV changed or became invalid"); showData() }
            }.setNegativeButton("Cancel", null).create()
        dialog.setOnDismissListener {
            softKeysSuppressed = false
            updateSonimSoftKeys(force = true)
        }
        dialog.show()
    }

    private fun showAnalysis() {
        screen = Screen.ANALYSIS
        val d = currentDatum() ?: return showAnalyses(); val file = try { repository.read(d.csvFilename) } catch (_: Exception) { return showAnalyses() }
        val points = TimeMath.logicalPoints(file); val input = inputFor(d, file, points, 0)
        val ranges = ChartBuilder.availableRanges(listOf(input), false)
        if (!rangeInitialized) { currentRange = ranges.lastOrNull() ?: ChartRange.ALL_TIME; rangeInitialized = true }
        if (currentRange !in ranges) currentRange = ranges.lastOrNull() ?: ChartRange.ALL_TIME
        val build = ChartBuilder.build(listOf(input), currentRange, false)
        val report = TrendAnalysis.build(file, points)
        val root = UiFactory.vertical(this, scroll = true, horizontalPadding = 0) as ScrollView; root.id = 0x7001; val content = root.tag as LinearLayout
        content.addView(datumChartHeader(d.name, "${file.unit} ${file.timeBasis.perLabel}", points.size))
        content.addView(chartHeader(build?.recordsInRange ?: 0, ranges) { r -> currentRange = r; selectedChartPoint = null; showAnalysis() })
        var chartToFocus: TrendChartView? = null
        if (build == null) content.addView(UiFactory.centeredMessage(this, "insufficient data to chart"))
        else {
            content.addView(xRangeLabel(build))
            val s = build.series[0]
            if (selectedChartPoint == null || selectedChartPoint !in s.points.indices || s.points[selectedChartPoint!!].inRange.not()) selectedChartPoint = lastInRangeIndex(s)
            chartToFocus = chartView(build, false, 0, selectedChartPoint, inspectable = true, range = currentRange)
            content.addView(chartToFocus, LinearLayout.LayoutParams(-1, dp(145)))
            val point = s.points[selectedChartPoint!!]
            content.addView(coloredReadout(LINE_BLUE, null, point, s).apply { id = 0x7201 })
        }
        content.addView(UiFactory.divider(this))
        content.addView(Space(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)))
        if (report == null) content.addView(UiFactory.text(this, "insufficient data for statistics", 14f)) else addReport(content, file, report)
        setContentView(root); root.post { chartToFocus?.requestFocus() }; updateSonimSoftKeys()
    }

    private fun addReport(content: LinearLayout, file: TrendFile, report: TrendAnalysis.Report) {
        content.addView(UiFactory.text(this, "Change over time", 16f, true))
        content.addView(directionalStat("Overall change", report.overallChange, file.unit, percent = false))
        report.changes.forEach { c ->
            content.addView(statLine("Change over ${c.label.lowercase(Locale.getDefault())}", TrendAnalysis.formatChange(c.change), file.unit))
        }

        content.addView(UiFactory.text(this, "Percent change", 16f, true).apply { setPadding(dp(2), dp(7), dp(2), dp(3)) })
        report.overallPercent?.let { content.addView(directionalStat("All time", it, null, percent = true)) }
        report.changes.forEach { c ->
            c.percent?.let { content.addView(statLine("% change over ${c.label.lowercase(Locale.getDefault())}", TrendAnalysis.formatPercent(it), null)) }
        }

        report.goalProjection?.let {
            content.addView(UiFactory.text(this, it.text, 15f, true).apply { setPadding(dp(2), dp(7), dp(2), dp(4)) })
        }
        if (report.estimates.isNotEmpty()) {
            content.addView(UiFactory.text(this, "Estimated value in...", 16f, true).apply { setPadding(dp(2), dp(7), dp(2), dp(3)) })
            report.estimates.forEach { e -> content.addView(statLine(e.label, TrendAnalysis.formatEstimate(e.value), file.unit)) }
        }
    }

    private fun directionalStat(label: String, value: Double, unit: String?, percent: Boolean): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(UiFactory.text(this@MainActivity, label, 13.5f))
        val direction = when { value > 0 -> "↑ "; value < 0 -> "↓ "; else -> "" }
        val formatted = if (percent) TrendAnalysis.formatPercent(value) else TrendAnalysis.formatChange(value)
        addView(UiFactory.text(this@MainActivity, "$direction$formatted${unit?.let { " $it" } ?: ""}", 20f, true).apply {
            when {
                value > 0 -> setTextColor(0xFF16813A.toInt())
                value < 0 -> setTextColor(UiFactory.RED)
            }
            setPadding(dp(2), 0, dp(2), dp(4))
        })
    }

    private fun statLine(label: String, value: String, unit: String?): TextView = UiFactory.text(this, "$label: $value${unit?.let { " $it" } ?: ""}", 15f)

    private fun datumChartHeader(name: String, metadata: String, records: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(UiFactory.HEADER_GRAY)
        setPadding(dp(8), dp(6), dp(8), dp(6))

        val left = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(UiFactory.text(this@MainActivity, name, 18f, true).apply {
                setPadding(0, 0, 0, dp(1))
            })
            addView(UiFactory.text(this@MainActivity, metadata, 14f).apply {
                setPadding(0, 0, 0, 0)
            })
        }
        val right = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(UiFactory.text(this@MainActivity, records.toString(), 18f, true).apply {
                gravity = Gravity.END
                setPadding(0, 0, 0, dp(1))
            })
            addView(UiFactory.text(this@MainActivity, "Records", 13.5f).apply {
                gravity = Gravity.END
                setPadding(0, 0, 0, 0)
            })
        }
        addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(right, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun rangeSelector(ranges: List<ChartRange>, onSelect: (ChartRange) -> Unit): TextView = UiFactory.focusableText(this, "Range: ${currentRange.label}", 15f).apply {
        setOnClickListener {
            AlertDialog.Builder(this@MainActivity).setTitle("Chart range").setItems(ranges.map { it.label }.toTypedArray()) { _, i -> onSelect(ranges[i]) }.show()
        }
    }

    private fun chartHeader(records: Int, ranges: List<ChartRange>, onSelect: (ChartRange) -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(UiFactory.text(this@MainActivity, "$records records in range", 13.5f), LinearLayout.LayoutParams(0, -2, 1f))
        addView(rangeSelector(ranges, onSelect), LinearLayout.LayoutParams(-2, -2))
    }

    private fun lastInRangeIndex(series: ChartSeries): Int = series.points.indexOfLast { it.inRange }.let { if (it >= 0) it else series.points.lastIndex }

    private fun chartView(
        build: ChartBuilder.BuildResult,
        correlation: Boolean,
        seriesIndex: Int,
        pointIndex: Int?,
        inspectable: Boolean = true,
        range: ChartRange? = currentRange
    ): TrendChartView = TrendChartView(this).apply {
        isFocusable = inspectable
        isClickable = inspectable
        model = TrendChartView.Model(build.series, seriesIndex, pointIndex, correlation, 0.0, build.xSpan, range)
        if (!inspectable) return@apply
        setOnKeyListener { _, key, event ->
            if (event.action != KeyEvent.ACTION_DOWN || key !in listOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) return@setOnKeyListener false
            val m = model ?: return@setOnKeyListener false
            val si = m.selectedSeries.coerceIn(0, m.series.lastIndex)
            val series = m.series[si]
            val navigable = series.points.indices.filter { series.points[it].inRange }
            if (navigable.isEmpty()) return@setOnKeyListener true
            val currentIndex = m.selectedPoint?.takeIf { it in navigable } ?: navigable.last()
            val currentPosition = navigable.indexOf(currentIndex).coerceAtLeast(0)
            val nextPosition = if (key == KeyEvent.KEYCODE_DPAD_LEFT) {
                (currentPosition - 1).coerceAtLeast(0)
            } else {
                (currentPosition + 1).coerceAtMost(navigable.lastIndex)
            }
            val next = navigable[nextPosition]
            selectedChartPoint = next
            model = m.copy(selectedPoint = next)
            updatePointReadout(this@MainActivity, series.points[next], series, correlation)
            true
        }
    }

    private fun updatePointReadout(activity: Activity, point: ChartPoint, series: ChartSeries, correlation: Boolean) {
        activity.findViewById<TextView>(0x7202)?.text = TimeMath.pretty(point.logicalPoint.bucket)
        activity.findViewById<TextView>(0x7203)?.text = "${TimeMath.formatNumber(point.logicalPoint.value)} ${series.unit}"
        activity.findViewById<TextView>(0x7204)?.apply {
            text = if (correlation) "${series.name} - ${series.unit} ${series.timeBasis.perLabel}" else ""
            visibility = if (correlation) View.VISIBLE else View.GONE
        }
    }

    private fun xRangeLabel(build: ChartBuilder.BuildResult): TextView {
        val left = TimeMath.pretty(BucketKey(build.xBasis, TimeMath.floor(build.leftEdge, build.xBasis)))
        val right = TimeMath.pretty(BucketKey(build.xBasis, TimeMath.floor(build.rightEdge, build.xBasis)))
        return UiFactory.text(this, "$left  —  $right", 13.5f, true).apply { gravity = Gravity.CENTER }
    }

    private fun coloredReadout(
        color: Int,
        header: String?,
        point: ChartPoint,
        series: ChartSeries,
        roomy: Boolean = false
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(color)
        val horizontal = if (roomy) dp(8) else dp(5)
        val vertical = if (roomy) dp(10) else dp(5)
        setPadding(horizontal, vertical, horizontal, vertical)
        addView(UiFactory.text(this@MainActivity, header.orEmpty(), if (roomy) 14.5f else 14f, true).apply {
            id = 0x7204
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = if (header == null) View.GONE else View.VISIBLE
            setPadding(dp(1), 0, dp(1), if (roomy && header != null) dp(7) else dp(2))
        })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(UiFactory.text(this@MainActivity, TimeMath.pretty(point.logicalPoint.bucket), if (roomy) 14f else 13.5f, true).apply {
                id = 0x7202
                setTextColor(Color.WHITE)
                setPadding(0, 0, dp(3), 0)
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(UiFactory.text(this@MainActivity, "${TimeMath.formatNumber(point.logicalPoint.value)} ${series.unit}", if (roomy) 14f else 13.5f, true).apply {
                id = 0x7203
                setTextColor(Color.WHITE)
                gravity = Gravity.END
                setPadding(0, 0, 0, 0)
                setSingleLine(true)
            }, LinearLayout.LayoutParams(-2, -2))
        })
    }
    private fun colorBar(color: Int): View = View(this).apply { setBackgroundColor(color); layoutParams = LinearLayout.LayoutParams(-1, dp(4)).apply { topMargin = dp(3); bottomMargin = dp(3) } }

    private fun scrollAnalysisTop() { findViewById<ScrollView>(0x7001)?.smoothScrollTo(0, 0) }

    private fun showCorrelationSetup() {
        screen = Screen.CORRELATION_SETUP
        val state = registry.load(); val existing = currentCorrelationUid?.let { uid -> state.correlations.firstOrNull { it.uid == uid } }
        correlationChecks = linkedMapOf()
        val root = UiFactory.vertical(this, scroll = true) as ScrollView; val content = root.tag as LinearLayout
        correlationNameField = UiFactory.edit(this, "Name").apply {
            setText(existing?.name ?: "")
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateSonimSoftKeys()
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        content.addView(UiFactory.labeled(this, "Name", correlationNameField!!))
        content.addView(UiFactory.text(this, "Data sets (choose 2–4)", 15f, true))
        state.datums.sortedBy { it.order }.forEach { d ->
            val valid = repository.fileIsValid(d.csvFilename)
            val cb = CheckBox(this).apply {
                text = d.name + if (valid) "" else " (unavailable)"
                isChecked = valid && (d.uid in (existing?.datumUids ?: emptyList()))
                isEnabled = valid; isFocusable = valid
                textSize = 16f
                setOnCheckedChangeListener { _, _ -> updateSonimSoftKeys() }
            }
            correlationChecks[d.uid] = cb; content.addView(cb)
        }
        setContentView(root); updateSonimSoftKeys()
    }

    private fun returnFromCorrelationSetup() {
        if (correlationSetupOrigin == CorrelationSetupOrigin.CORRELATION) {
            val state = registry.load()
            val correlation = currentCorrelationUid?.let { uid -> state.correlations.firstOrNull { it.uid == uid } }
            val datums = state.datums.associateBy { it.uid }
            val renderable = correlation?.datumUids?.count { uid -> datums[uid]?.let { repository.fileIsValid(it.csvFilename) } == true } ?: 0
            if (correlation != null && renderable >= 2) {
                showCorrelation()
                return
            }
        }
        showCorrelations()
    }

    private fun saveCorrelationSetup() {
        val state = registry.load(); val existing = currentCorrelationUid?.let { uid -> state.correlations.firstOrNull { it.uid == uid } }
        val name = correlationNameField?.text?.toString()?.trim().orEmpty(); if (name.isBlank()) return toast("Name is required")
        if (state.correlations.any { it.uid != existing?.uid && it.name.equals(name, true) }) return toast("That correlation name is already in use")
        val members = state.datums.sortedBy { it.order }.map { it.uid }.filter { correlationChecks[it]?.isChecked == true }
        if (members.size !in 2..4) return toast("Choose 2 to 4 data sets")
        if (existing == null) {
            val c = registry.addCorrelation(name, members); selectedCorrelationUid = c.uid; currentCorrelationUid = c.uid
        } else {
            registry.updateCorrelation(existing.copy(name = name, datumUids = members)); selectedCorrelationUid = existing.uid; currentCorrelationUid = existing.uid
        }
        showCorrelations()
    }

    private fun showCorrelation() {
        screen = Screen.CORRELATION
        val state = registry.load(); val c = currentCorrelationUid?.let { uid -> state.correlations.firstOrNull { it.uid == uid } } ?: return showCorrelations()
        val members = state.datums.sortedBy { it.order }.filter { it.uid in c.datumUids && repository.fileIsValid(it.csvFilename) }
        if (members.size < 2) return showCorrelationSetup()
        val inputs = members.mapIndexedNotNull { i, d ->
            try { val f = repository.read(d.csvFilename); inputFor(d, f, TimeMath.logicalPoints(f), i) } catch (_: Exception) { null }
        }
        if (inputs.size < 2) return showCorrelationSetup()
        val ranges = ChartBuilder.availableRanges(inputs, true)
        if (!rangeInitialized) { currentRange = ChartRange.ALL_TIME; rangeInitialized = true }
        if (currentRange !in ranges) currentRange = ChartRange.ALL_TIME
        val build = ChartBuilder.build(inputs, currentRange, true)
        val root = UiFactory.vertical(this, scroll = false, horizontalPadding = 0) as LinearLayout
        val content = root
        content.addView(UiFactory.chartTitleHeader(this, c.name))
        content.addView(chartHeader(build?.recordsInRange ?: 0, ranges) { r -> currentRange = r; selectedChartPoint = null; showCorrelation() })
        var chartToFocus: TrendChartView? = null
        if (build == null) {
            content.addView(UiFactory.centeredMessage(this, "insufficient data to chart"))
        } else {
            content.addView(xRangeLabel(build))
            val matched = selectedSeriesUid?.let { uid -> build.series.indexOfFirst { it.datumUid == uid } } ?: -1
            selectedSeries = if (matched >= 0) matched else 0
            selectedSeriesUid = build.series[selectedSeries].datumUid
            val s = build.series[selectedSeries]
            if (selectedChartPoint == null || selectedChartPoint !in s.points.indices || s.points[selectedChartPoint!!].inRange.not()) selectedChartPoint = lastInRangeIndex(s)
            chartToFocus = chartView(build, true, selectedSeries, selectedChartPoint, inspectable = true, range = currentRange)
            content.addView(chartToFocus, LinearLayout.LayoutParams(-1, 0, 1f))
            val readout = coloredReadout(s.lineColor, "${s.name} - ${s.unit} ${s.timeBasis.perLabel}", s.points[selectedChartPoint!!], s, roomy = true).apply { id = 0x7201 }
            content.addView(readout, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(root); root.post { chartToFocus?.requestFocus() }; updateSonimSoftKeys()
    }

    private fun chooseCorrelationLine() {
        val state = registry.load(); val c = currentCorrelationUid?.let { uid -> state.correlations.firstOrNull { it.uid == uid } } ?: return
        val members = state.datums.sortedBy { it.order }.filter { it.uid in c.datumUids && repository.fileIsValid(it.csvFilename) }
        if (members.isEmpty()) return
        val inputs = members.mapIndexedNotNull { i, d -> try { val f = repository.read(d.csvFilename); inputFor(d, f, TimeMath.logicalPoints(f), i) } catch (_: Exception) { null } }
        val build = ChartBuilder.build(inputs, currentRange, true) ?: return
        AlertDialog.Builder(this).setTitle("Line").setItems(build.series.map { it.name }.toTypedArray()) { _, i ->
            selectedSeriesUid = build.series[i].datumUid; selectedChartPoint = null; showCorrelation()
        }.show()
    }

    private fun inputFor(d: DatumDefinition, file: TrendFile, points: List<LogicalPoint>, colorIndex: Int): ChartBuilder.InputSeries {
        val i = colorIndex.coerceIn(0, 3)
        return ChartBuilder.InputSeries(d.uid, d.name, file.unit, file.timeBasis, points, file.goal?.toDouble(), if (colorIndex == 0) LINE_BLUE else CORR_LINES[i], if (colorIndex == 0) HIGHLIGHT_BLUE else CORR_HIGHLIGHTS[i])
    }

    private fun deleteSelectedDatum() {
        val d = selectedDatumUid?.let { uid -> registry.load().datums.firstOrNull { it.uid == uid } } ?: return
        AlertDialog.Builder(this).setTitle("Delete datum?").setMessage("Delete ${d.name}? The CSV file will also be deleted.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                try { repository.delete(d.csvFilename) } catch (_: Exception) { }
                registry.deleteDatum(d.uid); selectedDatumUid = null; showData()
            }.show()
    }

    private fun deleteSelectedCorrelation() {
        val c = selectedCorrelationUid?.let { uid -> registry.load().correlations.firstOrNull { it.uid == uid } } ?: return
        AlertDialog.Builder(this).setTitle("Delete correlation?").setMessage("Delete ${c.name}? Data CSV files are not changed.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ -> registry.deleteCorrelation(c.uid); selectedCorrelationUid = null; showCorrelations() }.show()
    }

    private fun currentDatum(): DatumDefinition? = currentDatumUid?.let { uid -> registry.load().datums.firstOrNull { it.uid == uid } }

    private fun normalizeCsvFilename(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed.endsWith(".csv", ignoreCase = true)) return trimmed
        return "$trimmed.csv"
    }

    private fun decimalOrNull(value: String?, allowBlank: Boolean): BigDecimal? {
        val v = value?.trim().orEmpty(); if (v.isBlank()) return if (allowBlank) null else null
        return try { BigDecimal(v) } catch (_: Exception) { null }
    }

    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
}
