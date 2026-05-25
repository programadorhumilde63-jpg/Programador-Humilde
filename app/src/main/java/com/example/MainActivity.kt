package com.example

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                var currentTab by remember { mutableStateOf("Agenda") }
                
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        BottomNavigationBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    AgendaAppScreen(
                        currentTab = currentTab,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = Color(0xFFF3EDF7),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding() // Navigation bar safe areas
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple("Agenda", Icons.Default.DateRange, "Agenda"),
                Triple("Tarefas", Icons.Default.List, "Tarefas"),
                Triple("Avisos", Icons.Default.Notifications, "Avisos"),
                Triple("Ajustes", Icons.Default.Settings, "Ajustes")
            )
            
            tabs.forEach { (name, icon, _) ->
                val isSelected = currentTab == name
                Column(
                    modifier = Modifier
                        .clickable { onTabSelected(name) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val pillBg = if (isSelected) Color(0xFFEADDFF) else Color.Transparent
                    val iconColor = if (isSelected) Color(0xFF21005D) else Color(0xFF49454F)
                    
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(32.dp)
                            .background(pillBg, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = name,
                            tint = iconColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF21005D) else Color(0xFF49454F),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaAppScreen(
    currentTab: String = "Agenda",
    modifier: Modifier = Modifier,
    taskViewModel: TaskViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // Permission State
    val hasNotificationPermission = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission.value = isGranted
        if (isGranted) {
            Toast.makeText(context, "Lembretes e notificações ativados!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "As notificações push foram negadas. Você não receberá alertas de tarefas.", Toast.LENGTH_LONG).show()
        }
    }

    // Launch permission request on startup if needed on Android 13+
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission.value) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Task data states
    val tasks by taskViewModel.tasks.collectAsStateWithLifecycle()
    val filteredTasks by taskViewModel.filteredTasks.collectAsStateWithLifecycle()
    val selectedDate by taskViewModel.selectedDateMills.collectAsStateWithLifecycle()
    val selectedCategory by taskViewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedPriority by taskViewModel.selectedPriority.collectAsStateWithLifecycle()

    // Sheet / Add dialog visibility state
    var showAddDialog by remember { mutableStateOf(false) }

    // Statistics
    val totalTasksCount = tasks.size
    val completedTasksCount = tasks.count { it.isCompleted }
    val progressPercent = if (totalTasksCount > 0) completedTasksCount.toFloat() / totalTasksCount else 0f

    // Available categories/priorities details
    val categories = listOf("Todas", "Geral", "Trabalho", "Pessoal", "Estudos", "Saúde", "Outros")
    val priorities = listOf("Todas", "Baixa", "Normal", "Alta")

    Box(
        modifier = modifier
            .background(Color(0xFFFFFBFE)) // Theme Background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Conditionally Swap Content Panel based on Tab selected
            when (currentTab) {
                "Agenda" -> {
                    // Header Section
                    AgendaHeaderSection(
                        totalCount = totalTasksCount,
                        completedCount = completedTasksCount,
                        progressPercent = progressPercent,
                        hasNotificationPermission = hasNotificationPermission.value,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    )

                    // Weekday planner row selector
                    WeekdayPlannerSelector(
                        selectedDate = selectedDate,
                        onDateSelected = { taskViewModel.selectDate(it) }
                    )

                    // Categories/Priority filter row
                    FilterChipsBar(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { taskViewModel.selectCategory(it) },
                        priorities = priorities,
                        selectedPriority = selectedPriority,
                        onPrioritySelected = { taskViewModel.selectPriority(it) }
                    )

                    // Filtered checklist
                    TaskList(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        filteredTasks = filteredTasks,
                        onToggleComplete = { taskViewModel.toggleTaskCompletion(it) },
                        onDelete = { task ->
                            taskViewModel.deleteTask(task)
                            Toast.makeText(context, "Tarefa removida com sucesso", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                "Tarefas" -> {
                    TarefasTabScreen(
                        tasks = tasks,
                        onToggleComplete = { taskViewModel.toggleTaskCompletion(it) },
                        onDelete = { taskViewModel.deleteTask(it) }
                    )
                }
                "Avisos" -> {
                    AvisosTabScreen(
                        hasPermission = hasNotificationPermission.value,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        tasks = tasks
                    )
                }
                "Ajustes" -> {
                    AjustesTabScreen(
                        totalScheduled = totalTasksCount,
                        totalCompleted = completedTasksCount,
                        onDeleteAll = {
                            tasks.forEach { taskViewModel.deleteTask(it) }
                            Toast.makeText(context, "Todas as tarefas foram removidas!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 100.dp) // Adjusted to not overlap the beautiful M3 bottom navbar
                .testTag("add_task_fab"),
            containerColor = Color(0xFFD0BCFF), // As specified in custom HTML button
            contentColor = Color(0xFF381E72),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Criar nova tarefa",
                modifier = Modifier.size(28.dp)
            )
        }

        // Add task Dialog
        if (showAddDialog) {
            AddTaskOverlayDialog(
                onDismiss = { showAddDialog = false },
                onSave = { title, description, timeMills, category, priority, remind, minsBefore ->
                    taskViewModel.addTask(
                        title = title,
                        description = description,
                        dateTimeMills = timeMills,
                        category = category,
                        priority = priority,
                        remindMe = remind,
                        reminderMinutesBefore = minsBefore
                    )
                    showAddDialog = false
                    Toast.makeText(context, "Tarefa agendada e lembrete configurado!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun AgendaHeaderSection(
    totalCount: Int,
    completedCount: Int,
    progressPercent: Float,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Minha Agenda",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF1C1B1F)
                )
                
                val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
                Text(
                    text = dateFormat.format(Date()).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF49454F),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // M3 styling for user person display avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFEADDFF), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF21005D),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clean subtle linear status indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progresso de tarefas",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF49454F)
            )
            Text(
                text = "$completedCount de $totalCount concluídas",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6750A4)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { progressPercent },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = Color(0xFF6750A4),
            trackColor = Color(0xFFE7E0EC)
        )

        // Banner Alert
        if (!hasNotificationPermission) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF9DEDC), shape = RoundedCornerShape(12.dp))
                    .clickable { onRequestPermission() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert",
                    tint = Color(0xFFB3261E),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Lembretes inativos! Toque para ativar notificações.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF410E0B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun WeekdayPlannerSelector(
    selectedDate: Long?,
    onDateSelected: (Long?) -> Unit
) {
    val dates = remember {
        val list = mutableListOf<Calendar>()
        val startCal = Calendar.getInstance()
        for (i in 0 until 14) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = startCal.timeInMillis
                add(Calendar.DAY_OF_YEAR, i)
            }
            list.add(cal)
        }
        list
    }

    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cronograma diário",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F)
            )

            TextButton(
                onClick = { onDateSelected(null) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (selectedDate == null) Color(0xFF6750A4) else Color(0xFF49454F)
                )
            ) {
                Text(
                    text = "Ver Todas",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (selectedDate == null) FontWeight.Bold else FontWeight.Normal
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(dates) { calendar ->
                val dayOfWeek = SimpleDateFormat("EEE", Locale("pt", "BR")).format(calendar.time)
                    .replace(".", "").capitalize()
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH).toString()
                
                val isSelected = selectedDate?.let { isSameDay(it, calendar.timeInMillis) } ?: false

                val containerColor = if (isSelected) Color(0xFFEADDFF) else Color(0xFFF4EFF4)
                val contentColor = if (isSelected) Color(0xFF21005D) else Color(0xFF49454F)

                Card(
                    modifier = Modifier
                        .width(58.dp)
                        .height(78.dp)
                        .clickable {
                            if (isSelected) {
                                onDateSelected(null)
                            } else {
                                onDateSelected(calendar.timeInMillis)
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayOfMonth,
                            style = MaterialTheme.typography.titleLarge,
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipsBar(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    priorities: List<String>,
    selectedPriority: String,
    onPrioritySelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Categories Row
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filtro:",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF49454F),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEADDFF),
                            selectedLabelColor = Color(0xFF21005D),
                            containerColor = Color.Transparent,
                            labelColor = Color(0xFF49454F)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFFCAC4D0),
                            borderWidth = 1.dp,
                            selectedBorderColor = Color(0xFF6750A4)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TaskList(
    modifier: Modifier = Modifier,
    filteredTasks: List<Task>,
    onToggleComplete: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    if (filteredTasks.isEmpty()) {
        Box(
            modifier = modifier.padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "📅",
                    fontSize = 44.sp
                )
                Text(
                    text = "Nenhum compromisso agendado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF49454F)
                )
                Text(
                    text = "Toque no botão de "+" para agendar sua primeira tarefa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F).copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = filteredTasks,
                key = { it.id }
            ) { task ->
                TaskItemCard(
                    task = task,
                    onToggleComplete = { onToggleComplete(task) },
                    onDelete = { onDelete(task) }
                )
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    onToggleComplete: () -> Unit,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Dynamic color tags corresponding to Tailwind design choices
    val (primaryBg, iconCircleColor, labelThemeColor) = when (task.category) {
        "Trabalho" -> Triple(Color(0xFFE7E0EC), Color(0xFF6750A4), Color(0xFF21005D))
        "Estudos" -> Triple(Color(0xFFE5F6FD), Color(0xFF0288D1), Color(0xFF01579B))
        "Saúde" -> Triple(Color(0xFFFCE8E6), Color(0xFF7D5260), Color(0xFF31111D))
        "Pessoal" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Color(0xFF1B5E20))
        else -> Triple(Color(0xFFF4EFF4), Color(0xFF313033), Color(0xFF1C1B1F)) // Geral / Outros
    }

    val dateStr = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(task.dateTimeMills))

    val borderStroke = if (task.isCompleted) {
        null
    } else {
        BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.8f))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("task_item_${task.id}"),
        shape = RoundedCornerShape(24.dp), // Styled as rounded-3xl as specified
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) {
                Color(0xFFF4EFF4).copy(alpha = 0.6f)
            } else if (task.priority == "Alta") {
                Color(0xFFE7E0EC) // As specified in custom HTML
            } else {
                Color.White
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circle Status Icon with dynamic material vector
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (task.isCompleted) Color(0xFFCAC4D0) else iconCircleColor,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val iconVector = when (task.category) {
                        "Trabalho" -> Icons.Default.Home
                        "Estudos" -> Icons.Default.Star
                        "Saúde" -> Icons.Default.Favorite
                        "Pessoal" -> Icons.Default.Face
                        else -> Icons.Default.List
                    }
                    Icon(
                        imageVector = if (task.isCompleted) Icons.Default.Check else iconVector,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Title & Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (task.isCompleted) Color(0xFF49454F).copy(alpha = 0.6f) else Color(0xFF1C1B1F),
                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = "$dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }

                // Notification bell icon representation
                if (task.remindMe && !task.isCompleted) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notificação ativa",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "PUSH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4),
                            fontSize = 8.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // Expanded detail drawer
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = task.description.ifBlank { "Sem notas adicionais." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF49454F),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Priority and Status settings
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prioridade: ${task.priority}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6750A4)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        when (task.priority) {
                                            "Alta" -> Color(0xFFB3261E)
                                            "Baixa" -> Color(0xFF2E7D32)
                                            else -> Color(0xFF6750A4)
                                        },
                                        shape = CircleShape
                                    )
                            )
                        }

                        // Complete / Delete switches
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox task completation toggle
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleComplete() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { onToggleComplete() },
                                    modifier = Modifier.testTag("task_checkbox_${task.id}")
                                )
                                Text(
                                    text = if (task.isCompleted) "Reabrir" else "Concluir",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6750A4),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("delete_task_${task.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir",
                                    tint = Color(0xFFB3261E),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAREFAS SCREEN Sub-View (Full tab view for checklists)
// -------------------------------------------------------------
@Composable
fun TarefasTabScreen(
    tasks: List<Task>,
    onToggleComplete: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    val pending = tasks.filter { !it.isCompleted }
    val completed = tasks.filter { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Minhas Tarefas",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF1C1B1F)
        )
        Text(
            text = "Acompanhamento geral das listas",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF49454F),
            modifier = Modifier.padding(bottom = 20.dp)
        )

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nenhuma tarefa criada. Agende na aba Agenda!",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (pending.isNotEmpty()) {
                    item {
                        Text(
                            text = "Pendentes (${pending.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(pending) { task ->
                        SimplifiedTaskItem(task, onToggleComplete, onDelete)
                    }
                }

                if (completed.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Concluídas (${completed.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(completed) { task ->
                        SimplifiedTaskItem(task, onToggleComplete, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
fun SimplifiedTaskItem(
    task: Task,
    onToggleComplete: (Task) -> Unit,
    onDelete: (Task) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = task.isCompleted,
                onCheckedChange = { onToggleComplete(task) }
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) Color.Gray else Color(0xFF1C1B1F)
                )
                Text(
                    text = task.category + "  •  " + SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(task.dateTimeMills)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            IconButton(onClick = { onDelete(task) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFB3261E).copy(alpha = 0.7f))
            }
        }
    }
}

// -------------------------------------------------------------
// AVISOS SCREEN Sub-View (Push alerts test & guidelines)
// -------------------------------------------------------------
@Composable
fun AvisosTabScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    tasks: List<Task>
) {
    val context = LocalContext.current
    val withReminders = tasks.count { it.remindMe && !it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Avisos & Lembretes",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF1C1B1F)
        )
        Text(
            text = "Sistema de alertas automáticos via push",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF49454F),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF).copy(alpha = 0.8f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Status das Notificações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF21005D)
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (hasPermission) Color(0xFF2E7D32) else Color(0xFFB3261E)
                    )
                    Text(
                        text = if (hasPermission) "Autorizado a enviar push" else "Permissão de canais desligada",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF21005D)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Você possui atualmente $withReminders lembrete(s) automático(s) programados para soar no horário exato de seus compromissos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF21005D).copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Large push tester buttons block
        Button(
            onClick = {
                // Test push alert instantly
                NotificationHelper.showReminderNotification(
                    context,
                    999L,
                    "🔔 Alerta Teste Push - Agenda!",
                    "Este é um lembrete automático simulado do seu aplicativo Minha Agenda."
                )
                Toast.makeText(context, "Notificação de teste disparada com sucesso!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Text("Testar Notificação Push Agora")
            }
        }

        if (!hasPermission) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Solicitar Autorização")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Informações sobre os alarmes",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "• Este app utiliza o AlarmManager para soar notificações de forma precisa.\n\n" +
                    "• Se você reiniciar seu dispositivo, suas agenda e lembretes são remarcados automaticamente de forma transparente.\n\n" +
                    "• Os canais de som do alerta vêm configurados como importância alta para que sejam ouvidos imediatamente.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF49454F)
        )
    }
}

// -------------------------------------------------------------
// AJUSTES SCREEN Sub-View
// -------------------------------------------------------------
@Composable
fun AjustesTabScreen(
    totalScheduled: Int,
    totalCompleted: Int,
    onDeleteAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF1C1B1F)
        )
        Text(
            text = "Preferências e estatísticas",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF49454F),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4EFF4))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Métricas de Organização",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total agendado:", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF49454F))
                    Text("$totalScheduled tarefas", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total concluído:", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF49454F))
                    Text("$totalCompleted concluídas", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Clear layout preferences
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Ações Rápidas",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onDeleteAll,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Limpar Todas as Tarefas", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Professional Footer info
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Minha Agenda v1.1",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Text(
                    text = "programadorhumilde63@gmail.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskOverlayDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String, Boolean, Int) -> Unit
) {
    val context = LocalContext.current
    
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Geral") }
    var selectedPriority by remember { mutableStateOf("Normal") }
    var remindMe by remember { mutableStateOf(true) }
    val reminderMinutesOptions = listOf(0, 5, 10, 30, 60)
    var selectedMinutesIndex by remember { mutableStateOf(2) }

    val taskCalendar = remember { Calendar.getInstance() }
    
    var dateString by remember { mutableStateOf("") }
    var timeString by remember { mutableStateOf("") }

    val categories = listOf("Geral", "Trabalho", "Pessoal", "Estudos", "Saúde", "Outros")
    val priorities = listOf("Baixa", "Normal", "Alta")

    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    val timeFormatter = SimpleDateFormat("HH:mm", Locale("pt", "BR"))

    LaunchedEffect(Unit) {
        val mins = taskCalendar.get(Calendar.MINUTE)
        taskCalendar.set(Calendar.MINUTE, ((mins / 10) + 1) * 10)
        taskCalendar.set(Calendar.SECOND, 0)
        dateString = dateFormatter.format(taskCalendar.time)
        timeString = timeFormatter.format(taskCalendar.time)
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            taskCalendar.set(Calendar.YEAR, year)
            taskCalendar.set(Calendar.MONTH, month)
            taskCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            dateString = dateFormatter.format(taskCalendar.time)
        },
        taskCalendar.get(Calendar.YEAR),
        taskCalendar.get(Calendar.MONTH),
        taskCalendar.get(Calendar.DAY_OF_MONTH)
    )

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            taskCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
            taskCalendar.set(Calendar.MINUTE, minute)
            timeString = timeFormatter.format(taskCalendar.time)
        },
        taskCalendar.get(Calendar.HOUR_OF_DAY),
        taskCalendar.get(Calendar.MINUTE),
        true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Agendar Compromisso 📝",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título do Evento") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_title"),
                        singleLine = true
                    )
                }

                // Description
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Notas ou Descrição") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .testTag("input_description"),
                        maxLines = 3
                    )
                }

                // Date and Time Choices
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { datePickerDialog.show() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_pick_date"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (dateString.isEmpty()) "Escolher Data" else dateString,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { timePickerDialog.show() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_pick_time"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(
                                    text = if (timeString.isEmpty()) "Escolher Hora" else timeString,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Category Selection chip bar
                item {
                    Text(
                        text = "Categoria",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF49454F)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = cat == selectedCategory
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFFEADDFF) else Color(0xFFF4EFF4)
                                    )
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color(0xFF21005D) else Color(0xFF49454F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Priority Selection row
                item {
                    Text(
                        text = "Prioridade",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF49454F)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        priorities.forEach { prio ->
                            val isSelected = prio == selectedPriority
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFFEADDFF) else Color(0xFFF4EFF4)
                                    )
                                    .clickable { selectedPriority = prio }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = prio,
                                    color = if (isSelected) Color(0xFF21005D) else Color(0xFF49454F),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Reminder Configurations
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF6750A4))
                            Text(
                                "Lembrete Automático",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Switch(
                            checked = remindMe,
                            onCheckedChange = { remindMe = it },
                            modifier = Modifier.testTag("switch_remind_me")
                        )
                    }
                }

                // Time warning choice
                if (remindMe) {
                    item {
                        Column {
                            Text(
                                text = "Lembrar no horário exato?",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                reminderMinutesOptions.forEachIndexed { index, mins ->
                                    val isSelected = index == selectedMinutesIndex
                                    val label = if (mins == 0) "Na hora" else "$mins min"
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Color(0xFFEADDFF) else Color(0xFFF4EFF4).copy(alpha = 0.5f)
                                            )
                                            .clickable { selectedMinutesIndex = index }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color(0xFF21005D) else Color(0xFF49454F),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Por favor, insira um título!", Toast.LENGTH_SHORT).show()
                    } else {
                        val finalMinsBefore = if (remindMe) reminderMinutesOptions[selectedMinutesIndex] else 0
                        onSave(
                            title,
                            description,
                            taskCalendar.timeInMillis,
                            selectedCategory,
                            selectedPriority,
                            remindMe,
                            finalMinsBefore
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                modifier = Modifier.testTag("btn_save_task")
            ) {
                Text("Agendar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF6750A4))
            ) {
                Text("Cancelar")
            }
        }
    )
}

// Global helpers
private fun isSameDay(ms1: Long, ms2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = ms1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = ms2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
