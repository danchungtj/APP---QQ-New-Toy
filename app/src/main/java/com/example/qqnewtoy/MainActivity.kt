package com.example.qqnewtoy

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.qqnewtoy.data.AppDatabase
import com.example.qqnewtoy.data.ToyDay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var calendarRecyclerView: RecyclerView
    private lateinit var calendarAdapter: CalendarAdapter
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = AppDatabase.getDatabase(this)
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView)
        calendarRecyclerView.layoutManager = LinearLayoutManager(this)
        calendarAdapter = CalendarAdapter(database)
        calendarRecyclerView.adapter = calendarAdapter
    }
}

class CalendarAdapter(private val database: AppDatabase) : RecyclerView.Adapter<CalendarAdapter.MonthViewHolder>() {
    private val months = mutableListOf<Calendar>()

    init {
        val currentMonth = Calendar.getInstance()
        val previousMonth = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }
        months.add(previousMonth)
        months.add(currentMonth)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MonthViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_month_calendar, parent, false)
        return MonthViewHolder(view, database)
    }

    override fun onBindViewHolder(holder: MonthViewHolder, position: Int) {
        holder.bind(months[position])
    }

    override fun getItemCount() = months.size

    class MonthViewHolder(
        itemView: View,
        private val database: AppDatabase
    ) : RecyclerView.ViewHolder(itemView) {
        private val monthTitle: TextView = itemView.findViewById(R.id.monthTitle)
        private val calendarGrid: android.widget.GridView = itemView.findViewById(R.id.calendarGrid)

        fun bind(calendar: Calendar) {
            val dateFormat = SimpleDateFormat("yyyy年MM月", Locale.CHINA)
            monthTitle.text = dateFormat.format(calendar.time)

            calendarGrid.adapter = DayAdapter(calendar, database)
        }
    }
}

class DayAdapter(
    private val month: Calendar,
    private val database: AppDatabase
) : BaseAdapter() {
    private val days = mutableListOf<DayItem>()
    private val dateFormat = SimpleDateFormat("d", Locale.CHINA)

    init {
        val firstDayOfMonth = month.clone() as Calendar
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
        
        val lastDayOfMonth = month.clone() as Calendar
        lastDayOfMonth.set(Calendar.DAY_OF_MONTH, month.getActualMaximum(Calendar.DAY_OF_MONTH))

        // Add empty cells for days before the first day of the month
        val firstDayOfWeek = firstDayOfMonth.get(Calendar.DAY_OF_WEEK)
        for (i in 1 until firstDayOfWeek) {
            days.add(DayItem("", DayState.EMPTY, null))
        }

        // Add days of the month
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

        // Load saved states from database
        val startDate = firstDayOfMonth.time
        val endDate = lastDayOfMonth.time
        (itemView.context as? AppCompatActivity)?.lifecycleScope?.launch {
            database.toyDayDao().getDaysInRange(startDate, endDate).collect { toyDays ->
                toyDays.forEach { toyDay ->
                    val index = days.indexOfFirst { it.date?.time == toyDay.date.time }
                    if (index != -1) {
                        days[index] = days[index].copy(state = toyDay.state)
                    }
                }
                notifyDataSetChanged()
            }
        }
    }

    override fun getCount() = days.size
    override fun getItem(position: Int) = days[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)

        val dayNumber = view.findViewById<TextView>(R.id.dayNumber)
        val dayIcon = view.findViewById<ImageView>(R.id.dayIcon)

        val dayItem = days[position]
        dayNumber.text = dayItem.number

        when (dayItem.state) {
            DayState.EMPTY -> {
                dayIcon.visibility = View.GONE
            }
            DayState.CHECK -> {
                dayIcon.visibility = View.VISIBLE
                dayIcon.setImageResource(android.R.drawable.presence_online)
                dayIcon.setColorFilter(android.graphics.Color.RED)
            }
            DayState.EXCLAMATION -> {
                dayIcon.visibility = View.VISIBLE
                dayIcon.setImageResource(android.R.drawable.ic_dialog_alert)
            }
        }

        view.setOnClickListener {
            val currentState = dayItem.state
            val newState = when (currentState) {
                DayState.EMPTY -> DayState.CHECK
                DayState.CHECK -> DayState.EXCLAMATION
                DayState.EXCLAMATION -> DayState.EMPTY
            }
            days[position] = dayItem.copy(state = newState)
            
            // Save to database
            dayItem.date?.let { date ->
                (parent.context as? AppCompatActivity)?.lifecycleScope?.launch {
                    if (newState == DayState.EMPTY) {
                        database.toyDayDao().delete(ToyDay(date, newState))
                    } else {
                        database.toyDayDao().insert(ToyDay(date, newState))
                    }
                }
            }
            
            notifyDataSetChanged()
        }

        return view
    }
}

data class DayItem(
    val number: String,
    var state: DayState,
    val date: Date?
)

enum class DayState {
    EMPTY,
    CHECK,
    EXCLAMATION
} 