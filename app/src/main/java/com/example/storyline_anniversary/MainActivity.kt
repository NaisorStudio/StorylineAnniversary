package com.example.storyline_anniversary

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.work.*
import coil.compose.AsyncImage
import com.example.storyline_anniversary.ui.theme.StorylineAnniversaryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

// 1. Configuración Global de la App
object ConfigApp {
    const val VERSION_LOCAL = "02.10.0608.2026"
    const val URL_API_GIST = "https://api.github.com/gists/35ffbd135dfd92261679231a64774004"
    val FECHA_OBJETIVO_GIST: LocalDateTime = LocalDateTime.of(2026, 10, 25, 0, 0, 15)
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

// Gestión dinámica de Nombre e Ícono vía Activity-Alias
fun actualizarIconoYNombreApp(context: Context, versionLocal: String) {
    try {
        val pm = context.packageManager
        val esVersion2003 = versionLocal == "02.10.0608.2003"

        val aliasDefault = ComponentName(context, "${context.packageName}.MainActivityDefault")
        val aliasTimer = ComponentName(context, "${context.packageName}.MainActivityTimer")

        val estadoTimerActual = pm.getComponentEnabledSetting(aliasTimer)
        val estadoDefaultActual = pm.getComponentEnabledSetting(aliasDefault)

        if (esVersion2003) {
            if (estadoTimerActual != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    aliasTimer,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.SYNCHRONOUS
                )
                pm.setComponentEnabledSetting(
                    aliasDefault,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.SYNCHRONOUS
                )
            }
        } else {
            if (estadoDefaultActual != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    aliasDefault,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.SYNCHRONOUS
                )
                pm.setComponentEnabledSetting(
                    aliasTimer,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.SYNCHRONOUS
                )
            }
        }
    } catch (e: Exception) {
        Log.e("DEBUG_ALIAS", "Error al actualizar alias: ${e.message}")
    }
}

// Programación de la verificación periódica (WorkManager)
fun programarVerificacionSegundoPlano(context: Context) {
    val restricciones = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val trabajoVerificacion = PeriodicWorkRequestBuilder<VerificadorVersionWorker>(15, TimeUnit.MINUTES)
        .setConstraints(restricciones)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "VerificadorVersionPeriodico",
        ExistingPeriodicWorkPolicy.KEEP,
        trabajoVerificacion
    )
}

// 2. Pantalla Splash Animada
@Composable
fun SplashScreenMistico(onAnimacionTerminada: () -> Unit) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1.1f,
                animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
            )
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
            )
        }

        delay(5000)
        onAnimacionTerminada()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo Naisor Studio",
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Cronograma Primer Aniversario",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.alpha(alpha.value)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Naisor Studio © 2026",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.alpha(alpha.value)
            )

            Text(
                text = "v${ConfigApp.VERSION_LOCAL}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light
                ),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier.alpha(alpha.value)
            )
        }
    }
}

// 3. Actividad Principal
class MainActivity : ComponentActivity() {
    private var recargarFotosCallback by mutableStateOf({})

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_StorylineAnniversary)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        actualizarIconoYNombreApp(this, ConfigApp.VERSION_LOCAL)
        programarVerificacionSegundoPlano(this)

        setContent {
            StorylineAnniversaryTheme {
                val esVersionEspecial = ConfigApp.VERSION_LOCAL == "02.10.0608.2003"
                var mostrandoSplash by remember { mutableStateOf(!esVersionEspecial) }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (mostrandoSplash) {
                        SplashScreenMistico(
                            onAnimacionTerminada = {
                                mostrandoSplash = false
                            }
                        )
                    } else {
                        InterfazSegunVersion(
                            modifier = Modifier.padding(innerPadding),
                            onRegistrarRecarga = { callback ->
                                recargarFotosCallback = callback
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recargarFotosCallback()
    }

    override fun onStop() {
        super.onStop()
        val trabajoUnico = OneTimeWorkRequestBuilder<VerificadorVersionWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueue(trabajoUnico)
    }
}

// Utilidades y Funciones de Persistencia
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

fun esVersionObsoleta(versionLocal: String, versionServidor: String): Boolean {
    return try {
        val partesLocal = versionLocal.split(".").map { it.toIntOrNull() ?: 0 }
        val partesServidor = versionServidor.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(partesLocal.size, partesServidor.size)
        for (i in 0 until maxLength) {
            val numLocal = partesLocal.getOrElse(i) { 0 }
            val numServidor = partesServidor.getOrElse(i) { 0 }

            if (numLocal < numServidor) return true
            if (numLocal > numServidor) return false
        }
        false
    } catch (e: Exception) {
        Log.e("DEBUG_VERSION", "Error al comparar versiones: ${e.message}")
        false
    }
}

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

suspend fun descargarEInstalarApk(
    context: Context,
    urlApk: String,
    onProgreso: (Float) -> Unit,
    onCompletado: (Uri) -> Unit,
    onError: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val directorioDestino = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.getExternalFilesDir(null)

            val archivoApk = File(directorioDestino, "StoryAnniversary.apk")
            if (archivoApk.exists()) {
                archivoApk.delete()
            }

            val uriDescarga = Uri.parse(urlApk)
            val request = DownloadManager.Request(uriDescarga).apply {
                setTitle("Descargando actualización")
                setDescription("Descargando nueva versión de la aplicación...")
                setMimeType("application/vnd.android.package-archive")
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(archivoApk))
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            var descargando = true
            var contadorTiempoEspera = 0

            while (descargando) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val bytesDescargados = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotales = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val estado = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                    if (bytesTotales > 0) {
                        val progreso = bytesDescargados.toFloat() / bytesTotales.toFloat()
                        withContext(Dispatchers.Main) {
                            onProgreso(progreso)
                        }
                    }

                    when (estado) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            descargando = false
                            val apkUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                archivoApk
                            )
                            withContext(Dispatchers.Main) {
                                onCompletado(apkUri)
                            }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            descargando = false
                            val razon = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            val mensajeDetalle = when (razon) {
                                404 -> "Error 404: La URL del APK no existe."
                                403 -> "Error 403: Acceso denegado al APK."
                                else -> "Error en la descarga. Código: $razon"
                            }
                            withContext(Dispatchers.Main) {
                                onError(mensajeDetalle)
                            }
                        }
                        DownloadManager.STATUS_PENDING -> {
                            contadorTiempoEspera++
                            if (contadorTiempoEspera > 40) {
                                descargando = false
                                downloadManager.remove(downloadId)
                                withContext(Dispatchers.Main) {
                                    onError("La descarga no responde. Revisa la conexión.")
                                }
                            }
                        }
                    }
                } else {
                    descargando = false
                    withContext(Dispatchers.Main) {
                        onError("No se pudo obtener el estado de la descarga.")
                    }
                }
                cursor.close()
                delay(500)
            }
        } catch (e: Exception) {
            Log.e("DEBUG_VERSION", "Error al descargar APK: ${e.message}")
            withContext(Dispatchers.Main) {
                onError("Excepción en la descarga: ${e.localizedMessage}")
            }
        }
    }
}

fun lanzarInstalacionApk(context: Context, uriApk: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uriApk, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(intent)
}

// 4. Composable Principal
@Composable
fun CronogramaApp(
    modifier: Modifier = Modifier,
    actividadesActualizadas: List<Actividad>? = null,
    onRegistrarRecarga: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    var fechaHoraActual by remember { mutableStateOf(LocalDateTime.now()) }
    val fechaEvento = remember { LocalDate.of(2026, 10, 25) }

    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.getDefault())
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())

    fun actividadesPorDefecto(): List<Actividad> {
        return listOf(
            Actividad(1, LocalTime.of(8, 0), LocalTime.of(10, 0), "Desayunar con el amor de mi vida", Color(0xFFFFEBEE), obtenerFotoUriDePrefs(context, 1)),
            Actividad(2, LocalTime.of(10, 1), LocalTime.of(11, 0), "Caminar con mi princesa preciosa en el valle escondido", Color(0xFFE8F5E9), obtenerFotoUriDePrefs(context, 2)),
            Actividad(3, LocalTime.of(11, 1), LocalTime.of(12, 0), "El mejor almuerzo con mi esposa preciosa", Color(0xFFFFF3E0), obtenerFotoUriDePrefs(context, 3)),
            Actividad(4, LocalTime.of(12, 1), LocalTime.of(14, 0), "Viajar con mi reina hermosa para tierras altas", Color(0xFFE3F2FD), obtenerFotoUriDePrefs(context, 4)),
            Actividad(5, LocalTime.of(14, 1), LocalTime.of(16, 0), "Paseo en dualbicicleta con mi alma gemela", Color(0xFFF3E5F5), obtenerFotoUriDePrefs(context, 5)),
            Actividad(6, LocalTime.of(16, 1), LocalTime.of(17, 45), "Viaje con mi muñequita hermosa hacia…", Color(0xFFFFF8E1), obtenerFotoUriDePrefs(context, 6)),
            Actividad(7, LocalTime.of(17, 46), LocalTime.of(18, 30), "… la playa la Barquete para ver el atardecer", Color(0xFFE0F7FA), obtenerFotoUriDePrefs(context, 7)),
            Actividad(8, LocalTime.of(18, 31), LocalTime.of(19, 30), "Cerrar con broche de oro, comer la pizza favorita de mi amada preciosa", Color(0xFFFCE4EC), obtenerFotoUriDePrefs(context, 8))
        )
    }

    var actividades by remember { mutableStateOf(actividadesPorDefecto()) }
    var actividadEnFoco by remember { mutableStateOf<Actividad?>(null) }
    var tempUriParaCamara by remember { mutableStateOf<Uri?>(null) }
    var idActividadFotografiando by remember { mutableStateOf<Int?>(null) }

    SolicitarPermisoNotificaciones()

    LaunchedEffect(actividadesActualizadas) {
        if (!actividadesActualizadas.isNullOrEmpty()) {
            actividades = actividadesActualizadas
            if (actividadEnFoco != null) {
                actividadEnFoco = actividades.find { it.id == actividadEnFoco?.id }
            }
        }
    }

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
                    text = "Cronograma Primer Aniversario",
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

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Logo Naisor Studio",
                        modifier = Modifier.size(44.dp)
                    )

                    Text(
                        text = "Naisor Studio © 2026",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colorSubtitulo
                    )

                    Text(
                        text = "v${ConfigApp.VERSION_LOCAL}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Light
                        ),
                        color = colorSubtitulo
                    )
                }
            }
        }
    }
}

// Componente Tarjeta de Actividad
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

// Animación Mística
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

// Idioma Místico
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

// Permisos y Notificaciones
@Composable
fun SolicitarPermisoNotificaciones() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { }
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
            putExtra("MENSAJE", "En 5 minutos comienza una nueva actividad, entra a la app para descubrir cuál será")
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

// SEGMENTO CONDICIONAL Y PANTALLA TEMPORIZADOR
@Composable
fun InterfazSegunVersion(
    modifier: Modifier = Modifier,
    onRegistrarRecarga: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var requiereActualizarApk by remember { mutableStateOf(false) }
    var mensajeBloqueo by remember { mutableStateOf("") }
    var urlDescargaApk by remember { mutableStateOf("") }
    var estaDescargando by remember { mutableStateOf(false) }
    var progresoDescarga by remember { mutableFloatStateOf(0f) }
    var apkListaParaInstalarUri by remember { mutableStateOf<Uri?>(null) }

    var actividadesServidor by remember { mutableStateOf<List<Actividad>?>(null) }

    suspend fun consultarActualizaciones() {
        withContext(Dispatchers.IO) {
            try {
                val urlConBuster = "${ConfigApp.URL_API_GIST}?t=${System.currentTimeMillis()}"
                val url = URL(urlConBuster)
                val conexion = url.openConnection() as HttpURLConnection

                conexion.useCaches = false
                conexion.setDefaultUseCaches(false)
                conexion.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                conexion.setRequestProperty("Pragma", "no-cache")
                conexion.setRequestProperty("Expires", "0")
                conexion.setRequestProperty("User-Agent", "Android-App")

                conexion.connectTimeout = 4000
                conexion.readTimeout = 4000
                conexion.requestMethod = "GET"

                if (conexion.responseCode == 200) {
                    val respuestaRaw = conexion.inputStream.bufferedReader().use { it.readText() }

                    val jsonGistApi = JSONObject(respuestaRaw)
                    val filesObject = jsonGistApi.getJSONObject("files")
                    val nombreArchivo = filesObject.keys().next()
                    val contenidoString = filesObject.getJSONObject(nombreArchivo).getString("content")

                    val jsonObject = JSONObject(contenidoString)

                    val versionMinima = jsonObject.optString("version_minima", ConfigApp.VERSION_LOCAL)
                    val urlApk = jsonObject.optString("url_apk", "")
                    val mensaje = jsonObject.optString("mensaje_bloqueo", "Hay una nueva versión disponible.")

                    // Lee la fecha de activación si la agregas al JSON. Si no la incluyes, usa ConfigApp.FECHA_OBJETIVO_GIST por defecto.
                    val fechaActivacionStr = jsonObject.optString("fecha_activacion_actualizacion", "")
                    val fechaActivacion = try {
                        if (fechaActivacionStr.isNotEmpty()) {
                            LocalDateTime.parse(fechaActivacionStr)
                        } else {
                            ConfigApp.FECHA_OBJETIVO_GIST
                        }
                    } catch (e: Exception) {
                        ConfigApp.FECHA_OBJETIVO_GIST
                    }

                    val estaObsoleta = esVersionObsoleta(ConfigApp.VERSION_LOCAL, versionMinima)
                    val yaLlegoLaHora = !LocalDateTime.now().isBefore(fechaActivacion)

                    val arrayActividades = jsonObject.optJSONArray("actividades")
                    val nuevasActividades = mutableListOf<Actividad>()

                    if (arrayActividades != null) {
                        val tf = DateTimeFormatter.ofPattern("H:mm")

                        for (i in 0 until arrayActividades.length()) {
                            val item = arrayActividades.getJSONObject(i)
                            val id = item.getInt("id")
                            val inicio = LocalTime.parse(item.getString("horaInicio").trim(), tf)
                            val fin = LocalTime.parse(item.getString("horaFin").trim(), tf)
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
                    }

                    withContext(Dispatchers.Main) {
                        if (nuevasActividades.isNotEmpty() && yaLlegoLaHora) {
                            actividadesServidor = nuevasActividades
                        }

                        // Muestra el cuadro de dialogo de actualización solo si la versión local es menor
                        // Y ADEMÁS ya transcurrió la fecha de activación especificada.
                        if (estaObsoleta && yaLlegoLaHora) {
                            mensajeBloqueo = mensaje
                            urlDescargaApk = urlApk
                            requiereActualizarApk = true
                        }
                    }
                }
                conexion.disconnect()
            } catch (e: Exception) {
                Log.e("DEBUG_VERSION", "Error al sincronizar JSON: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            consultarActualizaciones()
            delay(3000)
        }
    }

    val versionEspecialTemporizador = "02.10.0608.2003"
    val soloMostrarTemporizador = ConfigApp.VERSION_LOCAL == versionEspecialTemporizador

    Box(modifier = modifier.fillMaxSize()) {
        if (soloMostrarTemporizador) {
            PantallaSoloTemporizador()
        } else {
            CronogramaApp(
                actividadesActualizadas = actividadesServidor,
                onRegistrarRecarga = onRegistrarRecarga
            )
        }

        if (requiereActualizarApk) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text(text = "¡Nueva versión disponible!") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = mensajeBloqueo)

                        if (estaDescargando) {
                            Text(
                                text = "Descargando: ${(progresoDescarga * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            LinearProgressIndicator(
                                progress = { progresoDescarga },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    if (apkListaParaInstalarUri != null) {
                        Button(
                            onClick = {
                                lanzarInstalacionApk(context, apkListaParaInstalarUri!!)
                            }
                        ) {
                            Text("Actualizar e Instalar")
                        }
                    } else if (!estaDescargando) {
                        Button(
                            onClick = {
                                if (urlDescargaApk.isNotEmpty()) {
                                    estaDescargando = true
                                    coroutineScope.launch {
                                        descargarEInstalarApk(
                                            context = context,
                                            urlApk = urlDescargaApk,
                                            onProgreso = { progreso ->
                                                progresoDescarga = progreso
                                            },
                                            onCompletado = { uriApk ->
                                                estaDescargando = false
                                                apkListaParaInstalarUri = uriApk
                                                lanzarInstalacionApk(context, uriApk)
                                            },
                                            onError = { mensajeError ->
                                                estaDescargando = false
                                                Toast.makeText(context, mensajeError, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    }
                                } else {
                                    Toast.makeText(context, "La URL de descarga no es válida", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text("Descargar actualización")
                        }
                    }
                },
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            )
        }
    }
}

@Composable
fun PantallaSoloTemporizador(
    modifier: Modifier = Modifier
) {
    val fechaObjetivo = remember { ConfigApp.FECHA_OBJETIVO_GIST }
    var tiempoRestante by remember { mutableStateOf(Duration.between(LocalDateTime.now(), fechaObjetivo)) }
    var contadorFinalizado by remember { mutableStateOf(tiempoRestante.isNegative || tiempoRestante.isZero) }

    LaunchedEffect(Unit) {
        while (true) {
            val ahora = LocalDateTime.now()
            val duracion = Duration.between(ahora, fechaObjetivo)

            if (duracion.isNegative || duracion.isZero) {
                tiempoRestante = Duration.ZERO
                contadorFinalizado = true
            } else {
                tiempoRestante = duracion
            }
            delay(1000)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "EfectoPulsacionTemporizador")
    val escalaAnimada by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EscalaLabelTemporizador"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "El GRAN ANIVERSARIO",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(escalaAnimada)
            ) {
                if (!contadorFinalizado) {
                    val dias = tiempoRestante.toDays()
                    val horas = tiempoRestante.toHours() % 24
                    val minutos = tiempoRestante.toMinutes() % 60
                    val segundos = tiempoRestante.seconds % 60

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%02dd : %02dh : %02dm : %02ds", dias, horas, minutos, segundos),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Faltan para el evento",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    Text(
                        text = "Es hora de actualizar al evento",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            lineHeight = 40.sp
                        ),
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(50.dp))

            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "Logo Naisor Studio",
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Naisor Studio © 2026",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "v${ConfigApp.VERSION_LOCAL}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Light
                ),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CronogramaPreview() {
    StorylineAnniversaryTheme {
        InterfazSegunVersion()
    }
}