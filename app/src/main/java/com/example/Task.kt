package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val dateTimeMills: Long, // Date and time of the task/appointment
    val isCompleted: Boolean = false,
    val priority: String = "Normal", // "Baixa", "Normal", "Alta"
    val category: String = "Geral", // "Pessoal", "Trabalho", "Estudos", "Saúde", "Outros"
    val remindMe: Boolean = true,
    val reminderMinutesBefore: Int = 0 // 0 = at event time, 10 = 10m before, 30 = 30m before, etc.
) {
    val reminderTimeMills: Long
        get() = dateTimeMills - (reminderMinutesBefore * 60 * 1000L)
}
