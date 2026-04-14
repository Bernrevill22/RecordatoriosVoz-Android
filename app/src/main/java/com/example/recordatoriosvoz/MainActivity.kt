
package com.example.recordatoriosvoz // <-- VERIFICA QUE ESTE SEA TU PAQUETE

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.speech.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.*
import androidx.room.Entity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- DATA & ROOM ---
@Entity(tableName = "recordatorios_table")
data class Recordatorio(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mensaje: String,
    val fecha: String,
    val categoria: String,
    val timestamp: Long
)

@Dao
interface RecordatorioDao {
    @Query("SELECT * FROM recordatorios_table ORDER BY id DESC")
    fun getAll(): Flow<List<Recordatorio>>
    @Insert suspend fun insert(recordatorio: Recordatorio)
    @Delete suspend fun delete(recordatorio: Recordatorio)
}

@Database(entities = [Recordatorio::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordatorioDao(): RecordatorioDao
}

// --- ACTIVITY ---
class MainActivity : ComponentActivity() {
    private val db by lazy {
        Room.databaseBuilder(applicationContext, AppDatabase::class.java, "recordatorios-db")
            .fallbackToDestructiveMigration().build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear Canal de Notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("recordatorios_channel", "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val dao = db.recordatorioDao()
        setContent {
            val paletaOscura = darkColorScheme(
                background = Color.Black,
                surface = Color(0xFF1E1E1E),
                primary = Color(0xFF00E676),
                onBackground = Color.White
            )
            MaterialTheme(colorScheme = paletaOscura) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    RecordatorioScreen(dao = dao)
                }
            }
        }
    }
}

// --- UI PRINCIPAL ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordatorioScreen(dao: RecordatorioDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var textoInput by remember { mutableStateOf("") }
    val listaTotal by dao.getAll().collectAsState(initial = emptyList())

    // Categorías
    val categorias = listOf("Todas", "Universidad", "SystemKW", "Personal")
    var filtroActual by remember { mutableStateOf("Todas") }

    // Alarma
    val calendar = remember { Calendar.getInstance() }
    var fechaTexto by remember { mutableStateOf("Sin fecha") }
    var timestampSel by remember { mutableLongStateOf(0L) }

    val listaFiltrada = if (filtroActual == "Todas") listaTotal else listaTotal.filter { it.categoria == filtroActual }

    // Launcher de Permisos
    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[android.Manifest.permission.RECORD_AUDIO] == false) {
            Toast.makeText(context, "Permiso de audio denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Pedir permisos al abrir
    LaunchedEffect(Unit) {
        val permissions = mutableListOf(android.Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsLauncher.launch(permissions.toTypedArray())
    }

    // Lógica para programar alarma
    fun programarAlarma(time: Long, msg: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            return
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply { putExtra("mensaje", msg) }
        val pi = PendingIntent.getBroadcast(context, time.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
    }

    val guardar: (String) -> Unit = { texto ->
        val cat = if (filtroActual == "Todas") "Personal" else filtroActual
        val tAlarma = timestampSel
        val fAlarma = fechaTexto

        // Reset Inmediato
        textoInput = ""
        fechaTexto = "Sin fecha"
        timestampSel = 0L

        scope.launch {
            dao.insert(Recordatorio(mensaje = texto.trim(), fecha = fAlarma, categoria = cat, timestamp = tAlarma))
            if (tAlarma > System.currentTimeMillis()) programarAlarma(tAlarma, texto)
        }
    }

    // Date & Time Pickers
    val tPicker = TimePickerDialog(context, { _, h, m ->
        calendar.set(Calendar.HOUR_OF_DAY, h)
        calendar.set(Calendar.MINUTE, m)
        calendar.set(Calendar.SECOND, 0)
        timestampSel = calendar.timeInMillis
        fechaTexto = SimpleDateFormat("dd MMM, hh:mm a", Locale("es", "MX")).format(calendar.time)
    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false)

    val dPicker = DatePickerDialog(context, { _, y, m, d ->
        calendar.set(y, m, d)
        tPicker.show()
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))

    Column(modifier = Modifier.padding(16.dp)) {
        // Barra Categorías
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            items(categorias) { cat ->
                FilterChip(
                    selected = filtroActual == cat,
                    onClick = { filtroActual = cat },
                    label = { Text(cat) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedLabelColor = Color.Black)
                )
            }
        }

        // Lista de Tareas
        Box(modifier = Modifier.weight(1f)) {
            if (listaFiltrada.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("¡Todo al día!", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(listaFiltrada, key = { it.id }) { item ->
                        val dState = rememberSwipeToDismissBoxState(confirmValueChange = {
                            if (it != SwipeToDismissBoxValue.Settled) { scope.launch { dao.delete(item) }; true } else false
                        })
                        SwipeToDismissBox(state = dState, backgroundContent = {
                            Box(Modifier.fillMaxSize().background(Color(0xFFE53935)).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                                Icon(Icons.Default.Delete, null, tint = Color.White)
                            }
                        }) {
                            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(item.categoria.uppercase(), color = Color(0xFF00E676), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(item.fecha, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.mensaje, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Barra de Entrada
        Column {
            if (fechaTexto != "Sin fecha") Text("Alarma para: $fechaTexto", color = Color(0xFF00E676), modifier = Modifier.padding(start = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { dPicker.show() }) {
                    Icon(Icons.Default.Event, null, tint = if(timestampSel > 0L) Color(0xFF00E676) else Color.White)
                }
                TextField(
                    value = textoInput, onValueChange = { textoInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe o dicta...", color = Color.Gray) },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF1E1E1E), unfocusedContainerColor = Color(0xFF1E1E1E), cursorColor = Color.White, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                FloatingActionButton(onClick = {
                    if (textoInput.isNotBlank()) guardar(textoInput)
                    else {
                        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            iniciarDictado(context) { guardar(it) }
                        } else permissionsLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                    }
                }, containerColor = Color(0xFF00E676)) {
                    Icon(if (textoInput.isEmpty()) Icons.Default.Mic else Icons.Default.Send, null, tint = Color.Black)
                }
            }
        }
    }
}

// --- RECONOCEDOR DE VOZ ---
fun iniciarDictado(context: Context, callback: (String) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
    }
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(r: Bundle?) {
            r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { callback(it) }
            recognizer.destroy()
        }
        override fun onError(e: Int) { recognizer.destroy() }
        override fun onReadyForSpeech(p0: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(p0: Float) {}
        override fun onBufferReceived(p0: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(p0: Bundle?) {}
        override fun onEvent(p0: Int, p1: Bundle?) {}
    })
    recognizer.startListening(intent)
}