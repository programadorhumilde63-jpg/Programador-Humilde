package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TaskRepository
    
    init {
        val database = AppDatabase.getDatabase(application)
        repository = TaskRepository(database.taskDao())
        
        // Ensure notification channel is initialized at startup
        NotificationHelper.createNotificationChannel(application)
    }

    // Filter properties for specific Day/Category/Priority
    private val _selectedDateMills = MutableStateFlow<Long?>(null) // null means filter is cleared/show all days
    val selectedDateMills: StateFlow<Long?> = _selectedDateMills.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String>("Todas")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedPriority = MutableStateFlow<String>("Todas")
    val selectedPriority: StateFlow<String> = _selectedPriority.asStateFlow()

    // Base Tasks from Database
    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered tasks computed dynamically
    val filteredTasks: StateFlow<List<Task>> = combine(
        tasks,
        _selectedDateMills,
        _selectedCategory,
        _selectedPriority
    ) { allTasks, dateMills, category, priority ->
        allTasks.filter { task ->
            val dateMatches = if (dateMills != null) {
                isSameDay(task.dateTimeMills, dateMills)
            } else {
                true
            }
            val categoryMatches = if (category != "Todas") task.category == category else true
            val priorityMatches = if (priority != "Todas") task.priority == priority else true
            dateMatches && categoryMatches && priorityMatches
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectDate(mills: Long?) {
        _selectedDateMills.value = mills
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun selectPriority(priority: String) {
        _selectedPriority.value = priority
    }

    fun addTask(
        title: String,
        description: String,
        dateTimeMills: Long,
        category: String,
        priority: String,
        remindMe: Boolean,
        reminderMinutesBefore: Int
    ) {
        val task = Task(
            title = title,
            description = description,
            dateTimeMills = dateTimeMills,
            category = category,
            priority = priority,
            remindMe = remindMe,
            reminderMinutesBefore = reminderMinutesBefore
        )
        viewModelScope.launch {
            val id = repository.insert(task)
            val insertedTask = task.copy(id = id)
            if (remindMe) {
                ReminderScheduler.schedule(getApplication(), insertedTask)
            }
        }
    }

    fun toggleTaskCompletion(task: Task) {
        val updatedTask = task.copy(isCompleted = !task.isCompleted)
        viewModelScope.launch {
            repository.update(updatedTask)
            if (updatedTask.isCompleted) {
                // Cancel scheduled notification if complete
                ReminderScheduler.cancel(getApplication(), updatedTask)
            } else if (updatedTask.remindMe) {
                // Reschedule if uncompleted and reminder toggle is active
                ReminderScheduler.schedule(getApplication(), updatedTask)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            ReminderScheduler.cancel(getApplication(), task)
            repository.delete(task)
        }
    }

    private fun isSameDay(ms1: Long, ms2: Long): Boolean {
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = ms1 }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = ms2 }
        return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
                cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
    }
}
