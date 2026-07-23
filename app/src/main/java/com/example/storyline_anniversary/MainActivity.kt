package com.example.storyline_anniversary

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.storyline_anniversary.ui.theme.StorylineAnniversaryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 1. Configuración Global de la App
object ConfigApp {
    const val VERSION_LOCAL = "02.10.0608.2003"
    // URL Raw permanente de tu Gist control-versionA.json
    const val URL_JSON_CONFIG = "https://gist.githubusercontent.com/naisor/35ffbd135dfd92261679231a64774004/raw/control-versionA.json"
}

// Modelo de Datos
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

// Persistencia en SharedPreferences
fun guardarFotoUriEnPrefs(context: Context, idActividad: Int, uri: Uri) {
    val prefs = context.getSharedPreferences("storyline_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("foto_actividad_$idActividad", uri.toString()).apply()
}

fun obtenerFotoUriDePrefs(context: Context, idActividad: Int): Uri? {
    val prefs = context.getSharedPreferences("storyline_prefs", Context.MODE_PRIVATE)
    val uriString = prefs.getString("foto_actividad_$idActividad", null)
    return uriString?.let { Uri.parse(it) }
}

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

// Comparación de Versiones
fun esVersionObsoleta(versionLocal: String, versionServidor: String): Boolean {
    return try {
        val localNum = versionLocal.replace(".", "").toLong()
        val servidorNum = versionServidor.replace(".", "").toLong()
        localNum < servidorNum
    } catch (e: Exception) {
        false
    }
}

// Conversor de Color Hexadecimal
fun parseColorHex(hex: String): Color {
    return try {
        val cleaned = hex.removePrefix("#")
        val colorInt = cleaned.toLong(16)
        if (cleaned.length == 6) {
            Color(colorInt or 0xFF000000)
        } else {
            Color(colorInt)
        }
    } catch (e: Exception) {
        Color.White
    }
}

// 2. Composable Principal
@Composable
fun CronogramaApp(
    modifier: Modifier = Modifier,
    onRegistrarRecarga: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    var fechaHoraActual by remember { mutableStateOf(LocalDateTime.now()) }
    val fechaEvento = remember { LocalDate.of(2026, 10, 25) }

    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    // Estado para la Actualización Requerida
    var requiereActualizarApk by remember { mutableStateOf(false) }
    var mensajeBloqueo by remember { mutableStateOf("") }
    var urlDescargaApk by remember { mutableStateOf("") }

    // Actividades Predeterminadas (Fallback offline si no hay red)
    fun actividadesPorDefecto(): List<Actividad> {
        return listOf(
            Actividad(1, LocalTime.of(8, 0), LocalTime.of(10, 0), "Preparar café y revisar pendientes", Color(0xFFFFEBEE), obtenerFotoUriDePrefs(context, 1)),
            Actividad(2, LocalTime.of(10, 0), LocalTime.of(14, 0), "Avanzar en el código de la aplicación", Color(0xFFE3F2FD), obtenerFotoUriDePrefs(context, 2)),
            Actividad(3, LocalTime.of(14, 0), LocalTime.of(18, 0), "Diseño de interfaz y pruebas en Android", Color(0xFFE8F5E9), obtenerFotoUriDePrefs(context, 3)),
            Actividad(4, LocalTime.of(18, 0), LocalTime.of(22, 0), "Tiempo libre / Descanso", Color(0xFFF3E5F5), obtenerFotoUriDePrefs(context, 4))
        )
    }

    var actividades by remember { mutableStateOf(actividadesPorDefecto()) }
    var actividadEnFoco by remember { mutableStateOf<Actividad?>(null) }
    var tempUriParaCamara by remember { mutableStateOf<Uri?>(null) }
    var idActividadFotografiando by remember { mutableStateOf<Int?>(null) }

    SolicitarPermisoNotificaciones()

    // Consulta del JSON en Gist para datos y control de versión
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val respuestaJson = URL(ConfigApp.URL_JSON_CONFIG).readText()
                val jsonObject = JSONObject(respuestaJson)

                val versionMinima = jsonObject.optString("version_minima", ConfigApp.VERSION_LOCAL)
                val urlApk = jsonObject.optString("url_apk", "")
                val mensaje = jsonObject.optString("mensaje_bloqueo", "Hay una nueva versión disponible.")

                if (esVersionObsoleta(ConfigApp.VERSION_LOCAL, versionMinima)) {
                    withContext(Dispatchers.Main) {
                        mensajeBloqueo = mensaje
                        urlDescargaApk = urlApk
                        requiereActualizarApk = true
                    }
                } else {
                    val arrayActividades = jsonObject.optJSONArray("actividades")
                    if (arrayActividades != null) {
                        val nuevasActividades = mutableListOf<Actividad>()
                        val tf = DateTimeFormatter.ofPattern("HH:mm")

                        for (i in 0 until arrayActividades.length()) {
                            val item = arrayActividades.getJSONObject(i)
                            val id = item.getInt("id")
                            val inicio = LocalTime.parse(item.getString("horaInicio"), tf)
                            val fin = LocalTime.parse(item.getString("horaFin"), tf)
                            val texto = item.getString("textoEspanol")
                            val color = parseColorHex(item.optString("colorHex", "#FFFFFF"))

                            nuevasActividades.add(
                                Actividad(
                                    id = id,
                                    horaInicio = inicio,
                                    horaFin = fin,
                                    textoEspanol = texto,
                                    colorFondoApp = color,
                                    fotoUri = obtenerFotoUriDePrefs(context, id)
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            actividades = nuevasActividades
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Programación de Notificaciones
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

    LaunchedEffect(Unit) {
        onRegistrarRecarga {
            if (actividadEnFoco != null) {
                actividadEnFoco = actividades.find { it.id == actividadEnFoco?.id }
            }
        }
    }

    val launcherCamara = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { exito ->
        if (exito && tempUriParaCamara != null && idActividadFotografiando != null) {
            val uriGuardada = tempUriParaCamara!!
            val idAct = idActividadFotografiando!!

            guardarFotoUriEnPrefs(context, idAct, uriGuardada)
            actividades = actividades.map {
                if (it.id == idAct) it.copy(fotoUri = uriGuardada) else it
            }
            actividadEnFoco = actividades.find { it.id == idAct }
        }
    }

    // Auto-selección de actividad en foco
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

    // Diálogo emergente de Actualización Obligatoria
    if (requiereActualizarApk) {
        AlertDialog(
            onDismissRequest = { /* Deshabilitado */ },
            title = { Text(text = "¡Actualización Requerida! 🚀") },
            text = { Text(text = mensajeBloqueo) },
            confirmButton = {
                Button(
                    onClick = {
                        if (urlDescargaApk.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlDescargaApk))
                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Actualizar Ahora")
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        )
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp))

                Text(
                    text = "Cronograma Especial",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colorTitulo,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

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
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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

            // Muestra de versión en la parte inferior
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "v${ConfigApp.VERSION_LOCAL}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Light
                ),
                color = colorSubtitulo,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// 3. Tarjeta de Actividad
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

                EfectoRevelarMistico(
                    revelado = debeRevelarEspanol,
                    textoFicticio = textoFicticioEstatico,
                    textoEspanol = actividad.textoEspanol,
                    estaCompletada = estaCompletada
                )
            }

            IconButton(
                onClick = { onTomarFotoClick() },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Tomar foto",
                    tint = if (actividad.fotoUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// 4. Animación Mística
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

// 5. Idioma Místico
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

// 6. Notificaciones
@Composable
fun SolicitarPermisoNotificaciones() {
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
        val fechaHoraInicio = LocalDateTime.of(fechaEvento, horaInicio)
        val fechaHoraNotificacion = fechaHoraInicio.minusMinutes(5)

        val millisNotificacion = fechaHoraNotificacion
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (millisNotificacion <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NotificacionReceiver::class.java).apply {
            putExtra("TITULO", "¡Próxima Actividad!")
            putExtra("MENSAJE", "En 5 minutos comienza una nueva actividad, entra a la app para descubrir cuál será 😎")
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

@Preview(showBackground = true)
@Composable
fun CronogramaPreview() {
    StorylineAnniversaryTheme {
        CronogramaApp()
    }
}