package com.example.devicehistory

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        isLenient = false
    }

    private enum class ApiEnvironment(val label: String, val baseUrl: String) {
        Release("Release", "https://dashboard.biceek.com/api"),
        Dev("Dev", "https://staging.dashboard.kittens.cloud/api")
    }

    private fun calendarFromText(text: String): Calendar? {
        val value = text.trim()
        if (value.isEmpty()) return null
        val date = runCatching { dateTimeFormat.parse(value) }.getOrNull()
            ?: runCatching { dateFormat.parse(value) }.getOrNull()
            ?: return null
        return Calendar.getInstance().apply { time = date }
    }

    private fun parseCalendar(text: String): Calendar {
        val calendar = Calendar.getInstance()
        calendarFromText(text)?.let { calendar.time = it.time }
        return calendar
    }

    private fun showDatePicker(target: EditText) {
        val calendar = parseCalendar(target.text.toString())
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                updateDateText(target, calendar)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateText(target: EditText, baseCalendar: Calendar = parseCalendar(target.text.toString())) {
        target.setText(dateFormat.format(baseCalendar.time))
        target.setSelection(target.text.length)
    }

    private fun composeDateTime(target: EditText, hourView: AutoCompleteTextView, minuteView: AutoCompleteTextView): String {
        val calendar = calendarFromText(target.text.toString())
            ?: throw IllegalArgumentException("\u8bf7\u586b\u5199\u6b63\u786e\u7684\u65e5\u671f")
        val hour = hourView.text.toString().toIntOrNull()
            ?: throw IllegalArgumentException("\u8bf7\u9009\u62e9\u5c0f\u65f6")
        val minute = minuteView.text.toString().toIntOrNull()
            ?: throw IllegalArgumentException("\u8bf7\u9009\u62e9\u5206\u949f")
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        return dateTimeFormat.format(calendar.time)
    }

    private fun setupNumberDropdown(view: AutoCompleteTextView, values: List<String>, selected: String) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, values))
        view.threshold = 0
        view.setText(selected, false)
        view.setOnClickListener { view.showDropDown() }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) view.showDropDown()
        }
    }

    private fun setupTextDropdown(view: AutoCompleteTextView, values: List<String>, selected: String) {
        view.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, values))
        view.threshold = 0
        view.setText(selected, false)
        view.setOnClickListener { view.showDropDown() }
        view.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) view.showDropDown()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        val py = Python.getInstance()
        val module = py.getModule("my_script")

        val spEnvironment = findViewById<AutoCompleteTextView>(R.id.spEnvironment)
        val etDevices = findViewById<EditText>(R.id.etDevices)
        val etFrom = findViewById<EditText>(R.id.etFrom)
        val etTo = findViewById<EditText>(R.id.etTo)
        val btnPickFrom = findViewById<ImageButton>(R.id.btnPickFrom)
        val btnPickTo = findViewById<ImageButton>(R.id.btnPickTo)
        val acFromHour = findViewById<AutoCompleteTextView>(R.id.acFromHour)
        val acFromMinute = findViewById<AutoCompleteTextView>(R.id.acFromMinute)
        val acToHour = findViewById<AutoCompleteTextView>(R.id.acToHour)
        val acToMinute = findViewById<AutoCompleteTextView>(R.id.acToMinute)
        val btn = findViewById<Button>(R.id.btnRun)
        val txt = findViewById<TextView>(R.id.txtLog)

        val environments = ApiEnvironment.entries.toTypedArray()
        setupTextDropdown(spEnvironment, environments.map { it.label }, environments.first().label)

        val hours = (0..23).map { "%02d".format(it) }
        val minutes = (0..59).map { "%02d".format(it) }
        etDevices.setText("E086B252400000CF\nE086B2524000002B")
        val toCalendar = Calendar.getInstance()
        val fromCalendar = Calendar.getInstance().apply {
            time = toCalendar.time
            add(Calendar.HOUR_OF_DAY, -6)
        }
        etFrom.setText(dateFormat.format(fromCalendar.time))
        etTo.setText(dateFormat.format(toCalendar.time))
        setupNumberDropdown(acFromHour, hours, "%02d".format(fromCalendar.get(Calendar.HOUR_OF_DAY)))
        setupNumberDropdown(acFromMinute, minutes, "%02d".format(fromCalendar.get(Calendar.MINUTE)))
        setupNumberDropdown(acToHour, hours, "%02d".format(toCalendar.get(Calendar.HOUR_OF_DAY)))
        setupNumberDropdown(acToMinute, minutes, "%02d".format(toCalendar.get(Calendar.MINUTE)))
        etFrom.setOnClickListener { showDatePicker(etFrom) }
        etTo.setOnClickListener { showDatePicker(etTo) }
        btnPickFrom.setOnClickListener { showDatePicker(etFrom) }
        btnPickTo.setOnClickListener { showDatePicker(etTo) }

        btn.setOnClickListener {
            val environment = environments.firstOrNull { it.label == spEnvironment.text.toString() }
                ?: environments.first()
            val devices = etDevices.text.toString().trim()
            val from = runCatching { composeDateTime(etFrom, acFromHour, acFromMinute) }
                .getOrElse {
                    txt.text = it.message ?: "\u8bf7\u586b\u5199\u6b63\u786e\u7684\u5f00\u59cb\u65f6\u95f4"
                    return@setOnClickListener
                }
            val to = runCatching { composeDateTime(etTo, acToHour, acToMinute) }
                .getOrElse {
                    txt.text = it.message ?: "\u8bf7\u586b\u5199\u6b63\u786e\u7684\u7ed3\u675f\u65f6\u95f4"
                    return@setOnClickListener
                }
            if (devices.isEmpty() || from.isEmpty() || to.isEmpty()) {
                txt.text = "\u8bf7\u586b\u5199\u5168\u90e8\u8f93\u5165\u9879"
                return@setOnClickListener
            }
            if (BuildConfig.API_TOKEN.isBlank()) {
                txt.text = "API token is not configured"
                return@setOnClickListener
            }
            txt.text = "\u6267\u884c\u4e2d...\n"
            btn.isEnabled = false
            scope.launch(Dispatchers.IO) {
                try {
                    val result = module.callAttr(
                        "run_query",
                        devices,
                        from,
                        to,
                        environment.baseUrl,
                        BuildConfig.API_TOKEN
                    ).toString()
                    withContext(Dispatchers.Main) { txt.text = result }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        txt.text = "\u51fa\u9519\uff1a" + e.message
                    }
                } finally {
                    withContext(Dispatchers.Main) { btn.isEnabled = true }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
