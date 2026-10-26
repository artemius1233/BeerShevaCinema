@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package il.co.beershevacinema

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import il.co.beershevacinema.data.CinemaSource
import il.co.beershevacinema.data.Screening
import il.co.beershevacinema.data.SourceResult
import il.co.beershevacinema.data.ScheduleRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import coil.compose.AsyncImage

private val Bg = Color(0xFF101015)
private val Surface1 = Color(0xFF1B1B24)
private val Surface2 = Color(0xFF252532)
private val Purple = Color(0xFFB9A2FF)
private val Muted = Color(0xFFAAA9B8)
private val MainText = Color(0xFFF5F3FA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Purple,
                onPrimary = Color(0xFF24124D),
                background = Bg,
                onBackground = MainText,
                surface = Surface1,
                onSurface = MainText,
                secondary = Color(0xFF8CD9C3)
            )) {
                CinemaApp()
            }
        }
    }
}

@Composable
private fun CinemaApp() {
    val context = LocalContext.current
    val repository = remember { ScheduleRepository() }
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateListOf<SourceResult>() }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedCinema by remember { mutableStateOf("Все") }
    var selectedLanguage by remember { mutableStateOf("Любой язык") }
    var selectedSubtitle by remember { mutableStateOf("Любые субтитры") }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var selectedDateKey by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val fresh = repository.loadAll()
            results.clear()
            results.addAll(fresh)
            lastUpdated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val allScreenings = results.flatMap { it.screenings }
    val filtered = allScreenings.filter { item ->
        val q = query.trim()
        val matchesQuery = q.isBlank() ||
                item.title.contains(q, ignoreCase = true) ||
                item.originalTitle.contains(q, ignoreCase = true) ||
                item.cinema.contains(q, ignoreCase = true) ||
                item.audioLanguage.contains(q, ignoreCase = true) ||
                item.dubbing.contains(q, ignoreCase = true) ||
                item.subtitles.contains(q, ignoreCase = true) ||
                item.format.contains(q, ignoreCase = true)
        val matchesCinema = selectedCinema == "Все" || item.cinema == selectedCinema
        val matchesLanguage = selectedLanguage == "Любой язык" || when (selectedLanguage) {
            "Английский" -> item.audioLanguage == "Английский"
            "Иврит" -> item.audioLanguage == "Иврит"
            "Русский" -> item.audioLanguage == "Русский" || item.dubbing == "Русский дубляж"
            else -> item.audioLanguage == selectedLanguage
        }
        val matchesSubtitles = selectedSubtitle == "Любые субтитры" || when (selectedSubtitle) {
            "Русские" -> item.subtitles == "Русские"
            "Английские" -> item.subtitles == "Английские"
            "Иврит" -> item.subtitles == "Иврит"
            "Без разницы" -> true
            else -> true
        }
        val matchesDate = selectedDateKey == null || item.dateKey == selectedDateKey
        matchesQuery && matchesCinema && matchesLanguage && matchesSubtitles && matchesDate
    }.sortedWith(compareBy<Screening> { it.dateKey }.thenBy { it.time }.thenBy { it.title })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding()
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(52.dp).height(52.dp).clip(RoundedCornerShape(17.dp)).background(Surface2),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Movie, contentDescription = null, tint = Purple, modifier = Modifier.width(29.dp).height(29.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Кино в Беэр-Шеве", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MainText)
                Text("РАСПИСАНИЕ И ЯЗЫКИ СЕАНСОВ", fontSize = 10.sp, color = Muted, letterSpacing = 1.1.sp)
            }
            Surface(shape = CircleShape, color = Surface1, modifier = Modifier.clickable { refresh() }) {
                Box(Modifier.padding(11.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = Purple)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Purple, modifier = Modifier.width(18.dp).height(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Сегодня и ближайшие дни", color = MainText, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(lastUpdated?.let { "Обновлено $it" } ?: "Обновление…", color = Muted, fontSize = 11.sp)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Найти фильм или кинотеатр", color = Muted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Muted) },
            shape = RoundedCornerShape(16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Дата:",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.width(8.dp))

            FilterChip(
                selected = selectedDateKey == null,
                onClick = { selectedDateKey = null },
                label = { Text("Все даты") }
            )

            Spacer(Modifier.width(8.dp))

            FilterChip(
                selected = selectedDateKey != null,
                onClick = { showDatePicker = true },
                label = {
                    Text(
                        selectedDateKey?.let { key ->
                            runCatching {
                                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(key)
                                date?.let {
                                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(it)
                                } ?: key
                            }.getOrDefault(key)
                        } ?: "Выбрать день"
                    )
                }
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("КИНОТЕАТР", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        ChipRow(listOf("Все", "Planet Cinema", "Cinema City"), selectedCinema) { selectedCinema = it }
        Text("ЯЗЫК ОРИГИНАЛА / ОЗВУЧКА", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
        ChipRow(listOf("Любой язык", "Английский", "Иврит", "Русский"), selectedLanguage) { selectedLanguage = it }
        Text("СУБТИТРЫ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
        ChipRow(listOf("Любые субтитры", "Русские", "Английские", "Иврит"), selectedSubtitle) { selectedSubtitle = it }
        HorizontalDivider(color = Surface2)
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("СЕАНСЫ", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.weight(1f))
            Text("${filtered.size} найдено", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        when {
            loading && results.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Purple)
                    Spacer(Modifier.height(12.dp))
                    Text("Загружаем расписание…", color = Muted)
                }
            }
            filtered.isNotEmpty() -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { screening -> ScreeningCard(screening) }
                item { SourceFooter(results, onOpen = { openUrl(context, it) }) }
            }
            else -> LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    EmptyState(
                        loading = loading,
                        results = results,
                        onRefresh = { refresh() },
                        onOpen = { openUrl(context, it) }
                    )
                }
            }
        }
    }
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDateKey =
                            datePickerState.selectedDateMillis?.let { millis ->
                                SimpleDateFormat(
                                    "yyyy-MM-dd",
                                    Locale.US
                                ).apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }.format(Date(millis))
                            }

                        showDatePicker = false
                    }
                ) {
                    Text("Применить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false }
                ) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(modifier = Modifier.fillMaxWidth()) {
        items(options) { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option) },
                label = { Text(option, fontSize = 11.sp, maxLines = 1) },
                modifier = Modifier.padding(end = 6.dp)
            )
        }
    }
}
@Composable
private fun ScreeningCard(screening: Screening) {
    val context = LocalContext.current

    Surface(
        color = Surface1,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Постер или заглушка
                if (!screening.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = screening.posterUrl,
                        contentDescription = "Постер: ${screening.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(76.dp)
                            .height(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .width(76.dp)
                            .height(112.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Movie,
                            contentDescription = "Постер недоступен",
                            tint = Purple,
                            modifier = Modifier
                                .width(30.dp)
                                .height(30.dp)
                        )
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        screening.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MainText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        screening.cinema,
                        color = Purple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        screening.dateLabel,
                        color = Muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = Surface2,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(
                                horizontal = 11.dp,
                                vertical = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = Purple,
                                modifier = Modifier
                                    .width(15.dp)
                                    .height(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                screening.time,
                                color = MainText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(13.dp))
            val langLabel = if (screening.dubbing != "Не указано" && screening.dubbing.isNotBlank()) {
                "${screening.audioLanguage} (${screening.dubbing})"
            } else {
                "${screening.audioLanguage} (Оригинальный звук)"
            }
            InfoLine(
                Icons.Default.Language,
                "Язык: $langLabel"
            )
            InfoLine(
                Icons.Default.Subtitles,
                "Субтитры: ${screening.subtitles}"
            )

            if (screening.format.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    screening.format,
                    color = Color(0xFF8CD9C3),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(13.dp))
            Button(
                onClick = {
                    openUrl(context, screening.ticketUrl)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Purple,
                    contentColor = Color(0xFF24124D)
                )
            ) {
                Text(
                    "Билеты и подробности",
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier
                        .width(16.dp)
                        .height(16.dp)
                )
            }

            Text(
                "Источник: ${screening.sourceLabel}",
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}


@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 5.dp)) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.width(15.dp).height(15.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyState(
    loading: Boolean,
    results: List<SourceResult>,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(66.dp).height(66.dp).clip(RoundedCornerShape(22.dp)).background(Surface1), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = Purple, modifier = Modifier.width(32.dp).height(32.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(if (loading) "Обновляем расписание" else "Сеансов не найдено", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MainText)
        Spacer(Modifier.height(6.dp))
        Text(
            if (results.isEmpty()) "Проверь подключение к интернету." else "API кинотеатров иногда недоступны. Открой источник — там будет актуальная информация.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRefresh, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = MainText)) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text("Попробовать снова")
        }
        Spacer(Modifier.height(18.dp))
        results.forEach { result ->
            SourceCard(result.source, result.message, result.updatedAt, onOpen)
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun SourceFooter(results: List<SourceResult>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 26.dp)) {
        Text("ИСТОЧНИКИ РАСПИСАНИЯ", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        results.forEach { result ->
            SourceCard(result.source, result.message, result.updatedAt, onOpen)
            Spacer(Modifier.height(8.dp))
        }
        Text("Сеансы и языковые метки могут меняться. Перед покупкой проверь данные на сайте кинотеатра.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun SourceCard(source: CinemaSource, message: String?, updatedAt: String?, onOpen: (String) -> Unit) {
    Surface(color = Surface1, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(source.url) }.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(source.name, color = MainText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(message ?: source.note, color = Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
                if (updatedAt != null) Text("Проверено в $updatedAt", color = Color(0xFF8CD9C3), fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "Открыть сайт", tint = Purple)
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
