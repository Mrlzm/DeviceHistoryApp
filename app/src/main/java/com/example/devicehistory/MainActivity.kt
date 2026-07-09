package com.example.devicehistory

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.TimePicker
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
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        isLenient = false
    }

    private enum class ApiEnvironment(val label: String, val baseUrl: String) {
        Release("Release", "https://dashboard.biceek.com/api"),
        Dev("Dev", "https://staging.dashboard.kittens.cloud/api")
    }

    private fun showDateTimePicker(target: EditText) {
        val calendar = Calendar.getInstance()
        val currentText = target.text.toString().trim()
        runCatching { dateTimeFormat.parse(currentText) }
            .getOrNull()
            ?.let { calendar.time = it }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 12, 32, 0)
        }
        val datePicker = DatePicker(this).apply {
            calendarViewShown = false
            init(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
                null
            )
        }
        val timePicker = TimePicker(this).apply {
            setIs24HourView(true)
            hour = calendar.get(Calendar.HOUR_OF_DAY)
            minute = calendar.get(Calendar.MINUTE)
        }

        container.addView(datePicker)
        container.addView(timePicker)

        AlertDialog.Builder(this)
            .setTitle("\u9009\u62e9\u65e5\u671f\u65f6\u95f4")
            .setView(container)
            .setNegativeButton("\u53d6\u6d88", null)
            .setPositiveButton("\u786e\u5b9a") { _, _ ->
                calendar.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                calendar.set(Calendar.SECOND, 0)
                target.setText(dateTimeFormat.format(calendar.time))
                target.setSelection(target.text.length)
            }
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        val py = Python.getInstance()
        val module = py.getModule("my_script")

        val spEnvironment = findViewById<Spinner>(R.id.spEnvironment)
        val etDevices = findViewById<EditText>(R.id.etDevices)
        val etFrom = findViewById<EditText>(R.id.etFrom)
        val etTo = findViewById<EditText>(R.id.etTo)
        val btnPickFrom = findViewById<Button>(R.id.btnPickFrom)
        val btnPickTo = findViewById<Button>(R.id.btnPickTo)
        val btn = findViewById<Button>(R.id.btnRun)
        val txt = findViewById<TextView>(R.id.txtLog)

        val environments = ApiEnvironment.entries.toTypedArray()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            environments.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spEnvironment.adapter = adapter

        etDevices.setText("E086B252400000CF,E086B2524000002B")
        etFrom.setText("2025-10-29 18:42:00")
        etTo.setText("2025-10-30 00:00:00")
        btnPickFrom.setOnClickListener { showDateTimePicker(etFrom) }
        btnPickTo.setOnClickListener { showDateTimePicker(etTo) }

        btn.setOnClickListener {
            val environment = environments[spEnvironment.selectedItemPosition]
            val devices = etDevices.text.toString().trim()
            val from = etFrom.text.toString().trim()
            val to = etTo.text.toString().trim()
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
