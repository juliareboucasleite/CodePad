package com.juliareboucasleite.codepad

import android.os.Bundle
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var editNotes: EditText
    private lateinit var datePicker: DatePicker
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy - EEEE", Locale("pt", "BR"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editNotes = findViewById(R.id.editNotes)
        datePicker = findViewById(R.id.datePicker)
        findViewById<TextView>(R.id.lblVersion).text =
            "v${BuildConfig.VERSION_NAME}"

        val today = Calendar.getInstance()
        datePicker.init(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH),
            null
        )

        findViewById<Button>(R.id.btnInsertDate).setOnClickListener {
            insertLine(formatSelectedDate())
        }

        findViewById<Button>(R.id.btnInsertTask).setOnClickListener {
            insertLine("- [ ] ")
        }
    }

    private fun formatSelectedDate(): String {
        val cal = Calendar.getInstance().apply {
            set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
        }
        return "## ${dateFormat.format(cal.time)}"
    }

    private fun insertLine(text: String) {
        val start = editNotes.selectionStart.coerceAtLeast(0)
        val current = editNotes.text?.toString().orEmpty()
        val prefix = if (start > 0 && current.getOrNull(start - 1) != '\n') "\n" else ""
        val insertion = prefix + text
        editNotes.text?.insert(start, insertion)
        editNotes.setSelection((start + insertion.length).coerceAtMost(editNotes.text?.length ?: 0))
    }
}
