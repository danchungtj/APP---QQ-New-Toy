package com.example.qqnewtoy

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
    fun onShowNotesForDate(date: Date)
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
        loadAndShowMonths()

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
        val notesButton = findViewById<Button>(R.id.notesButton)
        notesButton.setOnClickListener {
            showAllNotesDialog()
        }
    }

    override fun onAddNoteRequest(date: Date) {
        showAddNoteDialog(date)
    }

    override fun onShowNote(note: com.example.qqnewtoy.data.Note) {
        // This method is now unused, but kept for interface compatibility
    }

    override fun onShowNotesForDate(date: Date) {
        lifecycleScope.launch {
            val notes = database.noteDao().getNotesByDate(date).firstOrNull() ?: emptyList()
            if (notes.isEmpty()) {
                onAddNoteRequest(date) // Prompt to add if no notes exist
            } else {
                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
                val title = "Notes for ${dateFormat.format(date)}"

                // Create a LinearLayout to hold notes and delete buttons
                val context = this@MainActivity
                val notesContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(50, 20, 50, 20) // Add some padding
                }

                // Function to refresh the notes list within the dialog
                fun refreshNotesList(container: LinearLayout, currentNotes: MutableList<com.example.qqnewtoy.data.Note>) {
                    container.removeAllViews()
                    if (currentNotes.isEmpty()) {
                        val emptyView = TextView(context).apply { text = "No notes for this date." }
                        container.addView(emptyView)
                        // Consider closing the dialog or prompting to add a note again
                    } else {
                        currentNotes.forEachIndexed { index, note ->
                            val noteLayout = LinearLayout(context).apply {
                                orientation = LinearLayout.HORIZONTAL
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                )
                                weightSum = 1f // Distribute space
                            }

                            val noteTextView = TextView(context).apply {
                                text = note.text
                                layoutParams = LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f // Take 80% width
                                )
                                setPadding(0, 8, 8, 8)
                            }

                            val deleteButton = Button(context).apply {
                                text = getString(R.string.delete) // Use string resource
                                layoutParams = LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.2f // Take 20% width
                                )
                                setOnClickListener {
                                    // Confirmation dialog before deleting
                                    AlertDialog.Builder(context)
                                        .setTitle(getString(R.string.confirm_delete_title))
                                        .setMessage(getString(R.string.confirm_delete_message))
                                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                                            lifecycleScope.launch {
                                                try {
                                                    database.noteDao().delete(note)
                                                    val updatedNotes = notes.toMutableList().apply { removeAt(index) }
                                                    refreshNotesList(notesContainer, updatedNotes) // Refresh list in dialog
                                                    Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) {
                                                     Toast.makeText(context, "Error deleting note: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                        .setNegativeButton(getString(R.string.cancel), null)
                                        .show()
                                }
                            }

                            noteLayout.addView(noteTextView)
                            noteLayout.addView(deleteButton)
                            container.addView(noteLayout)
                        }
                    }
                }

                // Initial population of the list
                val mutableNotes = notes.toMutableList()
                refreshNotesList(notesContainer, mutableNotes)

                // Show the dialog with the custom layout
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setView(notesContainer) // Set the LinearLayout as the view
                    .setPositiveButton(getString(R.string.close), null) // Changed from "OK" to "Close"
                    .show()
            }
        }
    }

    private fun showAddNoteDialog(date: Date) {
        val editText = EditText(this)
        editText.hint = getString(R.string.note_hint)
        editText.setSingleLine(false)
        editText.requestFocus()

        val dateFormat = SimpleDateFormat("yy-MM-dd", Locale.getDefault())
        val dialogTitle = getString(R.string.add_new_toy) + if (date != null) " ${dateFormat.format(date)}" else ""

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(dialogTitle)
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
                        val note = com.example.qqnewtoy.data.Note(id = 0, date = date, text = noteText)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_show_notes -> {
                showAllNotesDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAllNotesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_all_notes, null)
        val notesContainer = dialogView.findViewById<LinearLayout>(R.id.notesContainer)
        val errorArea = dialogView.findViewById<TextView>(R.id.errorArea)
        val copyErrorButton = dialogView.findViewById<Button>(R.id.copyErrorButton)

        errorArea.visibility = View.GONE
        copyErrorButton.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val notes = withContext(Dispatchers.IO) {
                    database.noteDao().getAllNotes()
                }
                notesContainer.removeAllViews()
                if (notes.isEmpty()) {
                    val emptyView = TextView(this@MainActivity)
                    emptyView.text = getString(R.string.no_notes_found)
                    notesContainer.addView(emptyView)
                } else {
                    val dateFormat = SimpleDateFormat("yy-MM-dd", Locale.getDefault())
                    notes.forEach { note ->
                        val noteView = TextView(this@MainActivity)
                        noteView.text = "${dateFormat.format(note.date)}: ${note.text}"
                        noteView.setPadding(8, 8, 8, 8)
                        notesContainer.addView(noteView)
                    }
                }
            } catch (e: Exception) {
                errorArea.visibility = View.VISIBLE
                errorArea.text = "Error loading notes: ${e.message}"
                copyErrorButton.visibility = View.VISIBLE
                copyErrorButton.setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Error Details", "${e::class.java.name}: ${e.message}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this@MainActivity, "Error details copied", Toast.LENGTH_SHORT).show()
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.all_notes))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun loadAndShowMonths() {
        lifecycleScope.launch {
            val months = withContext(Dispatchers.IO) {
                val now = Calendar.getInstance()
                val defaultMonths = (0 until 4).map { i ->
                    Calendar.getInstance().apply { add(Calendar.MONTH, -i) }
                }
                val toyDays = database.toyDayDao().getAllToyDays()
                val notes = database.noteDao().getAllNotes()
                val allDates = (toyDays.map { it.date } + notes.map { it.date }).distinct()
                val allMonths = allDates.map {
                    Calendar.getInstance().apply {
                        time = it
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }.distinctBy { Pair(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
                val combined = (defaultMonths + allMonths)
                    .distinctBy { Pair(it.get(Calendar.YEAR), it.get(Calendar.MONTH)) }
                    .sortedWith(compareByDescending<Calendar> { it.get(Calendar.YEAR) }.thenByDescending { it.get(Calendar.MONTH) })
                combined
            }
            if (::calendarAdapter.isInitialized) {
                calendarAdapter.updateMonths(months)
            } else {
                calendarAdapter = CalendarAdapter(database, this@MainActivity, months)
                calendarRecyclerView.adapter = calendarAdapter
            }
        }
    }
}

class CalendarAdapter(
    private val database: AppDatabase,
    private val listener: NoteInteractionListener,
    var months: List<Calendar>
) : RecyclerView.Adapter<CalendarAdapter.MonthViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_month_calendar, parent, false)
        return MonthViewHolder(view, database, listener)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        holder.bind(months[position])
    }

    override fun getItemCount() = months.size

    fun updateMonths(newMonths: List<Calendar>) {
        months = newMonths
        notifyDataSetChanged()
    }

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

            // Shade past and current month cards
            val cardView = itemView as com.google.android.material.card.MaterialCardView
            val now = Calendar.getInstance()
            if (calendar.get(Calendar.YEAR) < now.get(Calendar.YEAR) ||
                (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.MONTH) < now.get(Calendar.MONTH))) {
                cardView.setCardBackgroundColor(itemView.context.getColor(R.color.past_month_card_bg))
            } else if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.MONTH) == now.get(Calendar.MONTH)) {
                cardView.setCardBackgroundColor(itemView.context.getColor(R.color.primary))
            } else {
                cardView.setCardBackgroundColor(itemView.context.getColor(R.color.card_background))
            }

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
        holder.dayIcon.visibility = View.GONE
        holder.dayNumber.visibility = View.VISIBLE
        holder.dayNumber.text = dayItem.number
        holder.dayNumber.setTextColor(context.getColor(R.color.text_primary)) // Default text color

        // Shade current date
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val cardView = holder.itemView as com.google.android.material.card.MaterialCardView
        if (dayItem.date != null && dayItem.date == today) {
            cardView.setCardBackgroundColor(context.getColor(R.color.current_date_bg))
        } else {
            cardView.setCardBackgroundColor(context.getColor(R.color.card_background))
        }

        when (dayItem.state) {
            DayState.EMPTY -> {
                // Keep default appearance
            }
            DayState.CHECK -> {
                holder.dayIcon.visibility = View.VISIBLE
                holder.dayNumber.visibility = View.INVISIBLE // Hide number behind icon
                holder.dayIcon.setImageResource(android.R.drawable.presence_online)
                holder.dayIcon.setColorFilter(context.getColor(R.color.check_mark))
            }
            DayState.EXCLAMATION -> {
                holder.dayIcon.visibility = View.VISIBLE
                holder.dayNumber.visibility = View.INVISIBLE // Hide number behind icon
                holder.dayIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                holder.dayIcon.setColorFilter(context.getColor(R.color.exclamation))
            }
            DayState.X -> {
                holder.dayIcon.visibility = View.VISIBLE
                holder.dayNumber.visibility = View.INVISIBLE // Hide number behind icon
                holder.dayIcon.setImageResource(android.R.drawable.ic_delete)
                holder.dayIcon.setColorFilter(android.graphics.Color.BLACK)
            }
            DayState.STUDY -> {
                holder.dayNumber.text = "S"
                holder.dayNumber.setTextColor(context.getColor(android.R.color.holo_blue_dark))
                holder.dayNumber.setTypeface(null, android.graphics.Typeface.BOLD)
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
                    DayState.X -> DayState.STUDY // Cycle to STUDY
                    DayState.STUDY -> DayState.EMPTY // Cycle back to EMPTY
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
                        listener.onShowNotesForDate(date)
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
} 