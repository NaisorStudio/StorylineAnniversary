package com.example.storyline_anniversary

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.storyline_anniversary.ui.theme.StorylineAnniversaryTheme
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

// 1. Modelo de Datos
data class Actividad(
    val id: Int,
    val horaInicio: LocalTime,
    val horaFin: LocalTime,
    val textoEspanol: String,
    val colorFondoApp: Color,
    val fotoUri: Uri? = null
)

class MainActivity : ComponentActivity() {
    private var recargarFotosCallback by mutableStateOf({})

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StorylineAnniversaryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CronogramaApp(
                        modifier = Modifier.padding(innerPadding),
                        onRegistrarRecarga = { callback ->
                            recargarFotosCallback = callback
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recargarFotosCallback()
    }
}

// Helpers para Persistencia en SharedPreferences
fun guardarFotoUriEnPrefs(context: Context, idActividad: Int, uri: Uri) {
    val prefs = context.getSharedPreferences("storyline_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("foto_actividad_$idActividad", uri.toString()).apply()
}

fun obtenerFotoUriDePrefs(context: Context, idActividad: Int): Uri? {
    val prefs = context.getSharedPreferences("storyline_prefs", Context.MODE_PRIVATE)
    val uriString = prefs.getString("foto_actividad_$idActividad", null)
    return uriString?.let { Uri.parse(it) }
}

// Helper para crear un archivo temporal seguro para la cámara
fun crearArchivoImagenTemporal(context: Context): Uri {
    val directorioFotos = File(context.getExternalFilesDir(null), "Pictures")
    if (!directorioFotos.exists()) directorioFotos.mkdirs()
    val archivo = File.createTempFile("foto_${System.currentTimeMillis()}", ".jpg", directorioFotos)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        archivo
    )
}

// 2. Pantalla Principal
@Composable
fun CronogramaApp(
    modifier: Modifier = Modifier,
    onRegistrarRecarga: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    var fechaHoraActual by remember { mutableStateOf(LocalDateTime.now()) }

    val fechaEvento = remember { LocalDate.of(2026, 7, 23) }

    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    // Solicitar permiso de notificaciones para Android 13+
    SolicitarPermisoNotificaciones()

    // Función para recargar actividades
    fun obtenerListaActividades(): List<Actividad> {
        return listOf(
            Actividad(
                id = 1,
                horaInicio = LocalTime.of(8, 0),
                horaFin = LocalTime.of(10, 0),
                textoEspanol = "Preparar café y revisar pendientes",
                colorFondoApp = Color(0xFFFFEBEE),
                fotoUri = obtenerFotoUriDePrefs(context, 1)
            ),
            Actividad(
                id = 2,
                horaInicio = LocalTime.of(12, 53),
                horaFin = LocalTime.of(14, 0),
                textoEspanol = "Avanzar en el código de la aplicación",
                colorFondoApp = Color(0xFFE3F2FD),
                fotoUri = obtenerFotoUriDePrefs(context, 2)
            ),
            Actividad(
                id = 3,
                horaInicio = LocalTime.of(14, 0),
                horaFin = LocalTime.of(18, 0),
                textoEspanol = "Diseño de interfaz y pruebas en Android",
                colorFondoApp = Color(0xFFE8F5E9),
                fotoUri = obtenerFotoUriDePrefs(context, 3)
            ),
            Actividad(
                id = 4,
                horaInicio = LocalTime.of(18, 0),
                horaFin = LocalTime.of(22, 0),
                textoEspanol = "Tiempo libre / Descanso",
                colorFondoApp = Color(0xFFF3E5F5),
                fotoUri = obtenerFotoUriDePrefs(context, 4)
            )
        )
    }

    var actividades by remember { mutableStateOf(obtenerListaActividades()) }
    var actividadEnFoco by remember { mutableStateOf<Actividad?>(null) }
    var tempUriParaCamara by remember { mutableStateOf<Uri?>(null) }
    var idActividadFotografiando by remember { mutableStateOf<Int?>(null) }

    // Programar notificaciones 5 minutos antes para cada actividad
    LaunchedEffect(actividades) {
        actividades.forEach { actividad ->
            programarNotificacionCincoMinutosAntes(
                context = context,
                idActividad = actividad.id,
                horaInicio = actividad.horaInicio,
                fechaEvento = fechaEvento
            )
        }
    }

    // Registramos la recarga cuando vuelve de Momentos
    LaunchedEffect(Unit) {
        onRegistrarRecarga {
            actividades = obtenerListaActividades()
            if (actividadEnFoco != null) {
                actividadEnFoco = actividades.find { it.id == actividadEnFoco?.id }
            }
        }
    }

    // Launcher para la Cámara Nativa
    val launcherCamara = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { exito ->
        if (exito && tempUriParaCamara != null && idActividadFotografiando != null) {
            val uriGuardada = tempUriParaCamara!!
            val idAct = idActividadFotografiando!!

            guardarFotoUriEnPrefs(context, idAct, uriGuardada)
            actividades = obtenerListaActividades()
            actividadEnFoco = actividades.find { it.id == idAct }
        }
    }

    // Auto-selección al abrir
    LaunchedEffect(Unit) {
        while (true) {
            fechaHoraActual = LocalDateTime.now()

            if (actividadEnFoco == null) {
                val fechaActual = fechaHoraActual.toLocalDate()
                val horaActual = fechaHoraActual.toLocalTime()

                val enProgresoActual = actividades.find {
                    !fechaActual.isBefore(fechaEvento) &&
                            fechaActual.isEqual(fechaEvento) &&
                            !horaActual.isBefore(it.horaInicio) &&
                            !horaActual.isAfter(it.horaFin)
                }

                actividadEnFoco = enProgresoActual ?: actividades.firstOrNull()
            }

            delay(1000)
        }
    }

    val tieneFotoDeFondo = actividadEnFoco?.fotoUri != null

    val colorTitulo = if (tieneFotoDeFondo) Color.White else Color.Black
    val colorSubtitulo = if (tieneFotoDeFondo) Color.White.copy(alpha = 0.85f) else Color(0xFF333333)

    val colorDeFondoActual = actividadEnFoco?.colorFondoApp ?: MaterialTheme.colorScheme.background
    val colorDeFondoAnimado by animateColorAsState(
        targetValue = colorDeFondoActual,
        animationSpec = tween(durationMillis = 600),
        label = "ColorFondoPantalla"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // --- CAPA DE FONDO ---
        if (tieneFotoDeFondo) {
            AsyncImage(
                model = actividadEnFoco?.fotoUri,
                contentDescription = "Fondo de actividad",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.40f)
            ) {}
        } else {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = colorDeFondoAnimado
            ) {}
        }

        // --- CONTENIDO PRINCIPAL ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fila Superior con Título y Botón "Momentos"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp)) // Equilibrio visual

                Text(
                    text = "Cronograma Primer Aniversario",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorTitulo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // Botón Momentos
                IconButton(
                    onClick = {
                        val intent = Intent(context, MomentosActivity::class.java)
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = "Ver Momentos",
                        tint = colorTitulo
                    )
                }
            }

            Text(
                text = "Fecha actual: ${fechaHoraActual.format(dateFormatter)} | ${fechaHoraActual.format(timeFormatter)}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = colorSubtitulo,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(actividades, key = { it.id }) { actividad ->
                    val esLaSeleccionada = (actividad.id == actividadEnFoco?.id)

                    ActividadCard(
                        actividad = actividad,
                        fechaHoraActual = fechaHoraActual,
                        fechaEvento = fechaEvento,
                        esLaSeleccionada = esLaSeleccionada,
                        onClick = {
                            actividadEnFoco = actividad
                        },
                        onTomarFotoClick = {
                            val uri = crearArchivoImagenTemporal(context)
                            tempUriParaCamara = uri
                            idActividadFotografiando = actividad.id
                            launcherCamara.launch(uri)
                        }
                    )
                }
            }
        }
    }
}

// 3. Tarjeta Individual de Actividad
@Composable
fun ActividadCard(
    actividad: Actividad,
    fechaHoraActual: LocalDateTime,
    fechaEvento: LocalDate,
    esLaSeleccionada: Boolean,
    onClick: () -> Unit,
    onTomarFotoClick: () -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

    val fechaActual = fechaHoraActual.toLocalDate()
    val horaActual = fechaHoraActual.toLocalTime()

    val esDiaDelEvento = !fechaActual.isBefore(fechaEvento)

    val estaCompletada = esDiaDelEvento && (fechaActual.isAfter(fechaEvento) || horaActual.isAfter(actividad.horaFin))
    val estaEnProgreso = esDiaDelEvento && fechaActual.isEqual(fechaEvento) &&
            (!horaActual.isBefore(actividad.horaInicio) && !horaActual.isAfter(actividad.horaFin))

    val textoFicticioEstatico = remember(actividad.textoEspanol) {
        generarTextoFicticio(actividad.textoEspanol)
    }

    val debeRevelarEspanol = estaEnProgreso || estaCompletada

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (esLaSeleccionada) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (esLaSeleccionada) 6.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${actividad.horaInicio.format(timeFormatter)} - ${actividad.horaFin.format(timeFormatter)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Transición Mística
                EfectoRevelarMistico(
                    revelado = debeRevelarEspanol,
                    textoFicticio = textoFicticioEstatico,
                    textoEspanol = actividad.textoEspanol,
                    estaCompletada = estaCompletada
                )
            }

            // Ícono de Cámara
            IconButton(
                onClick = { onTomarFotoClick() },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Tomar foto para esta actividad",
                    tint = if (actividad.fotoUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// 4. Componente de Animación Mística
@Composable
fun EfectoRevelarMistico(
    revelado: Boolean,
    textoFicticio: String,
    textoEspanol: String,
    estaCompletada: Boolean
) {
    AnimatedContent(
        targetState = revelado,
        transitionSpec = {
            (fadeIn(animationSpec = tween(durationMillis = 1200, easing = LinearOutSlowInEasing)) +
                    scaleIn(initialScale = 0.85f, animationSpec = tween(1200)))
                .togetherWith(
                    fadeOut(animationSpec = tween(durationMillis = 1000, easing = FastOutLinearInEasing)) +
                            slideOutVertically(targetOffsetY = { -20 }, animationSpec = tween(1000))
                )
        },
        label = "MisticoRevealEffect"
    ) { estaTraducido ->
        if (estaTraducido) {
            Text(
                text = textoEspanol,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                textDecoration = if (estaCompletada) TextDecoration.LineThrough else TextDecoration.None
            )
        } else {
            Text(
                text = textoFicticio,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(0.85f)
            )
        }
    }
}

// 5. Algoritmo de Idioma Ficticio
fun generarTextoFicticio(original: String): String {
    return original.map { char ->
        when (char) {
            in 'a'..'z' -> Character.toString(0x10330 + (char - 'a'))
            in 'A'..'Z' -> Character.toString(0x10330 + (char - 'A'))
            in '0'..'9' -> Character.toString(0x10340 + (char - '0'))
            'á', 'à', 'ä' -> Character.toString(0x10330)
            'é', 'è', 'ë' -> Character.toString(0x10334)
            'í', 'ì', 'ï' -> Character.toString(0x10338)
            'ó', 'ò', 'ö' -> Character.toString(0x1033E)
            'ú', 'ù', 'ü' -> Character.toString(0x10344)
            'ñ', 'Ñ' -> Character.toString(0x1034A)
            else -> char.toString()
        }
    }.joinToString("")
}

// 6. Funciones de Notificaciones y Alarmas

@Composable
fun SolicitarPermisoNotificaciones() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { _ -> }
        )
        LaunchedEffect(Unit) {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@SuppressLint("ScheduleExactAlarm")
fun programarNotificacionCincoMinutosAntes(
    context: Context,
    idActividad: Int,
    horaInicio: LocalTime,
    fechaEvento: LocalDate
) {
    try {
        // Combinar fecha del evento con hora de inicio
        val fechaHoraInicio = LocalDateTime.of(fechaEvento, horaInicio)
        // Restar 5 minutos
        val fechaHoraNotificacion = fechaHoraInicio.minusMinutes(5)

        val millisNotificacion = fechaHoraNotificacion
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        // Si la hora calculada ya pasó, no se programa
        if (millisNotificacion <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificacionReceiver::class.java).apply {
            putExtra("TITULO", "¡Próxima Actividad!")
            putExtra("MENSAJE", "En 5 minutos comienza una nueva actividad del aniversario, entra a la app para descubrir cuál será 😎")
            putExtra("ID_ACTIVIDAD", idActividad)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            idActividad,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    millisNotificacion,
                    pendingIntent
                )
            } else {
                val intentPermiso = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                context.startActivity(intentPermiso)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                millisNotificacion,
                pendingIntent
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 7. Vista Previa
@Preview(showBackground = true)
@Composable
fun CronogramaPreview() {
    StorylineAnniversaryTheme {
        CronogramaApp()
    }
}