package com.example.qqnewtoy

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.qqnewtoy.data.AppDatabase
import com.example.qqnewtoy.data.DayItem
import com.example.qqnewtoy.data.DayState
import com.example.qqnewtoy.data.ToyDay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull

// Define the interface at the top level
interface NoteInteractionListener {
    fun onShowNote(note: com.example.qqnewtoy.data.Note)
    fun onAddNoteRequest(date: Date)
}

class MainActivity : AppCompatActivity(), NoteInteractionListener { // Implement the top-level interface
    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var database: AppDatabase
    private lateinit var currentDateText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView)
        currentDateText = findViewById(R.id.currentDateText)
        calendarRecyclerView.layoutManager = LinearLayoutManager(this)
        calendarAdapter = CalendarAdapter(database, this)
        calendarRecyclerView.adapter = calendarAdapter

        // Set current date text
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        currentDateText.text = dateFormat.format(Date())

        val addToyButton = findViewById<Button>(R.id.addToyButton)
        addToyButton.setOnClickListener {
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            showAddNoteDialog(today)
        }
    }

    override fun onAddNoteRequest(date: Date) {
        showAddNoteDialog(date)
    }

    override fun onShowNote(note: com.example.qqnewtoy.data.Note) {
        AlertDialog.Builder(this)
            .setTitle("Note for ${SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).format(note.date)}")
            .setMessage(note.text)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun showAddNoteDialog(@Suppress("UNUSED_PARAMETER") date: Date) {
        val editText = EditText(this)
        editText.hint = getString(R.string.note_hint)
        editText.setSingleLine(false)
        editText.requestFocus()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_new_toy))
            .setView(editText)
            .setPositiveButton(getString(R.string.ok), null)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.setOnShowListener {
            val okButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            okButton.setOnClickListener {
                val noteText = editText.text.toString().trim()
                if (noteText.isEmpty()) {
                    editText.error = "Note cannot be empty"
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    try {
                        val today = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 0)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.time
                        val note = com.example.qqnewtoy.data.Note(today, noteText)
                        database.noteDao().insert(note)
                        dialog.dismiss()
                    } catch (e: Exception) {
                        editText.error = "Error saving note: ${e.message}"
                    }
                }
            }
        }
        dialog.show()
        editText.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }
}

class CalendarAdapter(
    private val database: AppDatabase,
    private val listener: NoteInteractionListener
) : RecyclerView.Adapter<CalendarAdapter.MonthViewHolder>() {
    private val months = mutableListOf<Calendar>()

    init {
        val currentMonth = Calendar.getInstance()
        val previousMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }
        months.add(currentMonth)
        months.add(previousMonth)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_month_calendar, parent, false)
        return MonthViewHolder(view, database, listener)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        holder.bind(months[position])
    }

    override fun getItemCount() = months.size

    class MonthViewHolder(
        itemView: View,
        private val database: AppDatabase,
        private val listener: NoteInteractionListener
    ) : RecyclerView.ViewHolder(itemView) {
        private val monthTitle: TextView = itemView.findViewById(R.id.monthTitle)
        private val calendarGrid: RecyclerView = itemView.findViewById(R.id.calendarGrid)
        private lateinit var dayAdapter: DayAdapter

        fun bind(calendar: Calendar) {
            val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            monthTitle.text = dateFormat.format(calendar.time)

            dayAdapter = DayAdapter(itemView.context, calendar, database, listener)
            calendarGrid.layoutManager = GridLayoutManager(itemView.context, 7)
            calendarGrid.adapter = dayAdapter
            calendarGrid.isNestedScrollingEnabled = false
        }
    }
}

// Remove interface definition from inside DayAdapter
class DayAdapter(
    private val context: Context,
    private val month: Calendar,
    private val database: AppDatabase,
    private val listener: NoteInteractionListener // Listener is now the top-level interface
) : RecyclerView.Adapter<DayAdapter.DayViewHolder>() {
    private val days = mutableListOf<DayItem>()
    private val dateFormat = SimpleDateFormat("d", Locale.ENGLISH)

    init {
        val firstDayOfMonth = month.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        
        val lastDayOfMonth = month.clone() as Calendar
        lastDayOfMonth.set(Calendar.DAY_OF_MONTH, month.getActualMaximum(Calendar.DAY_OF_MONTH))

        val dayOfWeekOfFirst = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)
        val paddingDays = dayOfWeekOfFirst - Calendar.SUNDAY

        for (i in 0 until paddingDays) {
            days.add(DayItem("", DayState.EMPTY, null))
        }

        for (day in 1..lastDayOfMonth.get(Calendar.DAY_OF_MONTH)) {
            val date = Calendar.getInstance().apply {
                set(Calendar.YEAR, month.get(Calendar.YEAR))
                set(Calendar.MONTH, month.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            days.add(DayItem(day.toString(), DayState.EMPTY, date))
        }

        loadDataFromDb()
    }

    private fun loadDataFromDb() {
        val firstDayOfMonth = month.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        val lastDayOfMonth = month.clone() as Calendar
        lastDayOfMonth.set(Calendar.DAY_OF_MONTH, month.getActualMaximum(Calendar.DAY_OF_MONTH))
        val startDate = firstDayOfMonth.time
        val endDate = lastDayOfMonth.time

        (context as? AppCompatActivity)?.lifecycleScope?.launch {
            database.toyDayDao().getDaysInRange(startDate, endDate).collect { toyDays ->
                var changed = false
                toyDays.forEach { toyDay ->
                    val index = days.indexOfFirst { it.date?.time == toyDay.date.time }
                    if (index != -1 && days[index].state != toyDay.state) {
                        days[index] = days[index].copy(state = toyDay.state)
                        changed = true
                    }
                }
                
                if (changed) {
                    notifyDataSetChanged()
                } else if (days.none { it.state != DayState.EMPTY && it.number.isNotEmpty() } && toyDays.isNotEmpty()) {
                    days.forEachIndexed { index, dayItem ->
                         val matchingToyDay = toyDays.find { it.date.time == dayItem.date?.time }
                         if(matchingToyDay != null && dayItem.state != matchingToyDay.state) {
                             days[index] = dayItem.copy(state = matchingToyDay.state)
                         } else if (matchingToyDay == null && dayItem.number.isNotEmpty() && dayItem.state != DayState.EMPTY) {
                              days[index] = dayItem.copy(state = DayState.EMPTY)
                         }
                    }
                    notifyDataSetChanged()
                 }
            }
        }
    }

    inner class DayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayNumber: TextView = itemView.findViewById(R.id.dayNumber)
        val dayIcon: ImageView = itemView.findViewById(R.id.dayIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return DayViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        val dayItem = days[position]
        holder.dayNumber.text = dayItem.number

        when (dayItem.state) {
            DayState.EMPTY -> {
                holder.dayIcon.visibility = View.GONE
            }
            DayState.CHECK -> {
                holder.dayIcon.visibility = View.VISIBLE
                holder.dayIcon.setImageResource(android.R.drawable.presence_online)
                holder.dayIcon.setColorFilter(context.getColor(R.color.check_mark))
            }
            DayState.EXCLAMATION -> {
                holder.dayIcon.visibility = View.VISIBLE
                holder.dayIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                holder.dayIcon.setColorFilter(context.getColor(R.color.exclamation))
            }
            DayState.X -> {
                holder.dayIcon.visibility = View.VISIBLE
                holder.dayIcon.setImageResource(android.R.drawable.ic_delete)
                holder.dayIcon.setColorFilter(android.graphics.Color.BLACK)
            }
        }

        if (dayItem.number.isNotEmpty()) {
            holder.itemView.setOnClickListener {
                val currentPosition = holder.adapterPosition
                if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

                val clickedDayItem = days[currentPosition]
                val currentState = clickedDayItem.state
                val newState = when (currentState) {
                    DayState.EMPTY -> DayState.CHECK
                    DayState.CHECK -> DayState.EXCLAMATION
                    DayState.EXCLAMATION -> DayState.X
                    DayState.X -> DayState.EMPTY
                }
                days[currentPosition] = clickedDayItem.copy(state = newState)
                notifyItemChanged(currentPosition)

                clickedDayItem.date?.let { date ->
                    (context as? AppCompatActivity)?.lifecycleScope?.launch {
                        val toyDayToSave = ToyDay(date, newState)
                        if (newState == DayState.EMPTY) {
                            database.toyDayDao().delete(toyDayToSave)
                        } else {
                            database.toyDayDao().insert(toyDayToSave)
                        }
                    }
                }
            }

            holder.itemView.setOnLongClickListener {
                val currentPosition = holder.adapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    val longClickedDayItem = days[currentPosition]
                    longClickedDayItem.date?.let { date ->
                        handleLongClick(date)
                    }
                }
                true
            }
        } else {
            holder.itemView.setOnClickListener(null)
            holder.itemView.setOnLongClickListener(null)
            holder.itemView.isClickable = false
        }
    }

    override fun getItemCount(): Int {
        return days.size
    }

    private fun handleLongClick(date: Date) {
        (context as? AppCompatActivity)?.lifecycleScope?.launch {
            try {
                val note = database.noteDao().getNoteByDate(date).firstOrNull()
                withContext(Dispatchers.Main) {
                    if (note != null && note.text.isNotEmpty()) {
                        listener.onShowNote(note)
                    } else {
                        listener.onAddNoteRequest(date)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DayAdapter", "Error handling long click for date $date", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading note data", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
} 