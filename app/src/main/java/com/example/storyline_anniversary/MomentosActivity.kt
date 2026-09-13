package com.example.storyline_anniversary

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.storyline_anniversary.ui.theme.StorylineAnniversaryTheme
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.absoluteValue

data class MomentoFoto(
    val idActividad: Int,
    val tituloActividad: String,
    val uri: Uri
)

class MomentosActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StorylineAnniversaryTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PantallaMomentos(
                        modifier = Modifier.padding(innerPadding),
                        onVolver = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PantallaMomentos(
    modifier: Modifier = Modifier,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    var listaMomentos by remember { mutableStateOf(obtenerTodosLosMomentosApp(context)) }

    var idsSeleccionados by remember { mutableStateOf(setOf<Int>()) }
    val modoSeleccionActivo = idsSeleccionados.isNotEmpty()
    var indiceFotoSeleccionada by remember { mutableIntStateOf(-1) }
    var mostrarDialogoCollage by remember { mutableStateOf(false) }

    val todosSeleccionados = listaMomentos.isNotEmpty() && idsSeleccionados.size == listaMomentos.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (modoSeleccionActivo) "${idsSeleccionados.size} seleccionados" else "Momentos Guardados",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (modoSeleccionActivo) {
                        IconButton(onClick = { idsSeleccionados = emptySet() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancelar selección"
                            )
                        }
                    } else {
                        IconButton(onClick = onVolver) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    }
                },
                actions = {
                    if (listaMomentos.isNotEmpty()) {
                        if (modoSeleccionActivo) {
                            IconButton(
                                onClick = {
                                    idsSeleccionados = if (todosSeleccionados) {
                                        emptySet()
                                    } else {
                                        listaMomentos.map { it.idActividad }.toSet()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "Seleccionar todo",
                                    tint = if (todosSeleccionados) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                            }

                            IconButton(
                                enabled = idsSeleccionados.isNotEmpty(),
                                onClick = {
                                    val cantidadABorrar = idsSeleccionados.size
                                    idsSeleccionados.forEach { id ->
                                        borrarFotoDePrefsApp(context, id)
                                    }
                                    idsSeleccionados = emptySet()
                                    listaMomentos = obtenerTodosLosMomentosApp(context)
                                    Toast.makeText(context, "$cantidadABorrar foto(s) eliminada(s)", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar seleccionados",
                                    tint = if (idsSeleccionados.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { mostrarDialogoCollage = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Crear Collage",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(
                                onClick = {
                                    exportarTodasLasFotosAGaleriaApp(context, listaMomentos)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DownloadForOffline,
                                    contentDescription = "Descargar todas las fotos"
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (listaMomentos.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Aún no has capturado momentos 📸",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Toma fotos desde el cronograma para guardarlas aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(listaMomentos, key = { it.idActividad }) { momento ->
                        val esSeleccionado = idsSeleccionados.contains(momento.idActividad)

                        TarjetaMomento(
                            momento = momento,
                            esSeleccionado = esSeleccionado,
                            modoSeleccionActivo = modoSeleccionActivo,
                            onClickFoto = {
                                if (modoSeleccionActivo) {
                                    idsSeleccionados = if (esSeleccionado) {
                                        idsSeleccionados - momento.idActividad
                                    } else {
                                        idsSeleccionados + momento.idActividad
                                    }
                                } else {
                                    val idx = listaMomentos.indexOf(momento)
                                    if (idx != -1) {
                                        indiceFotoSeleccionada = idx
                                    }
                                }
                            },
                            onLongClickFoto = {
                                idsSeleccionados = if (esSeleccionado) {
                                    idsSeleccionados - momento.idActividad
                                } else {
                                    idsSeleccionados + momento.idActividad
                                }
                            },
                            onExportar = {
                                exportarFotoAGaleriaApp(context, momento.uri)
                                Toast.makeText(context, "Foto guardada en galería", Toast.LENGTH_SHORT).show()
                            },
                            onBorrar = {
                                borrarFotoDePrefsApp(context, momento.idActividad)
                                idsSeleccionados = idsSeleccionados - momento.idActividad
                                listaMomentos = obtenerTodosLosMomentosApp(context)
                                Toast.makeText(context, "Foto eliminada de la app", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // --- VISOR DE FOTOS PANTALLA COMPLETA ---
            if (indiceFotoSeleccionada in listaMomentos.indices) {
                val pagerState = rememberPagerState(
                    initialPage = indiceFotoSeleccionada,
                    pageCount = { listaMomentos.size }
                )

                Dialog(
                    onDismissRequest = { indiceFotoSeleccionada = -1 },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    val momentoActual = listaMomentos.getOrNull(pagerState.currentPage)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            val momentoPage = listaMomentos[pageIndex]
                            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val scale = 1f - (pageOffset.absoluteValue * 0.15f).coerceIn(0f, 1f)
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = 1f - (pageOffset.absoluteValue * 0.5f).coerceIn(0f, 1f)
                                        rotationY = pageOffset * -15f
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = momentoPage.uri,
                                    contentDescription = momentoPage.tituloActividad,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            onClick = { indiceFotoSeleccionada = -1 }
                                        ),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }

                        // Top bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f))
                                .padding(16.dp)
                                .align(Alignment.TopCenter),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = momentoActual?.tituloActividad ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${pagerState.currentPage + 1} de ${listaMomentos.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }

                            IconButton(
                                onClick = { indiceFotoSeleccionada = -1 },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar imagen",
                                    tint = Color.White
                                )
                            }
                        }

                        // Bottom bar
                        momentoActual?.let { momento ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(16.dp)
                                    .align(Alignment.BottomCenter),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (exportarFotoAGaleriaApp(context, momento.uri)) {
                                            Toast.makeText(context, "Foto guardada en galería", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Guardar en Galería", color = Color.White)
                                }

                                IconButton(
                                    onClick = {
                                        val idABorrar = momento.idActividad
                                        borrarFotoDePrefsApp(context, idABorrar)
                                        listaMomentos = obtenerTodosLosMomentosApp(context)

                                        if (listaMomentos.isEmpty()) {
                                            indiceFotoSeleccionada = -1
                                        }
                                        Toast.makeText(context, "Foto eliminada de la app", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Eliminar foto",
                                        tint = Color(0xFFFFB4AB)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- DIÁLOGO CREAR COLLAGE ---
            if (mostrarDialogoCollage) {
                CollageDialog(
                    fotosUris = listaMomentos.map { it.uri },
                    onDismiss = { mostrarDialogoCollage = false },
                    onCollageGuardado = {
                        mostrarDialogoCollage = false
                        listaMomentos = obtenerTodosLosMomentosApp(context)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TarjetaMomento(
    momento: MomentoFoto,
    esSeleccionado: Boolean,
    modoSeleccionActivo: Boolean,
    onClickFoto: () -> Unit,
    onLongClickFoto: () -> Unit,
    onExportar: () -> Unit,
    onBorrar: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .combinedClickable(
                onClick = { onClickFoto() },
                onLongClick = { onLongClickFoto() }
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (esSeleccionado) 8.dp else 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = momento.uri,
                contentDescription = momento.tituloActividad,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (esSeleccionado) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ) {}
            }

            if (modoSeleccionActivo) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (esSeleccionado) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Estado Selección",
                        tint = if (esSeleccionado) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(24.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = momento.tituloActividad,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    if (!modoSeleccionActivo) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = onExportar) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Guardar en galería",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = onBorrar) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar de la app",
                                    tint = Color(0xFFFFB4AB)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helpers locales renombrados para evitar colisiones con CollageUtils.kt

private fun obtenerFotoUriDePrefsApp(context: Context, idActividad: Int): Uri? {
    val prefs = context.getSharedPreferences("storyline_prefs", Context.MODE_PRIVATE)
    val uriString = prefs.getString("foto_actividad_$idActividad", null)
    return uriString?.let { Uri.parse(it) }
}

fun obtenerTodosLosMomentosApp(context: Context): List<MomentoFoto> {
    val titulosDefault = mapOf(
        1 to "Desayunar con el amor de mi vida",
        2 to "Caminar con mi princesa preciosa en el valle escondido",
        3 to "El mejor almuerzo con mi esposa preciosa",
        4 to "Viajar con mi reina hermosa para tierras altas",
        5 to "Paseo en dualbicicleta con mi alma gemela",
        6 to "Viaje con mi muñequita hermosa hacia…",
        7 to "… la playa la Barquete para ver el atardecer",
        8 to "Cerrar con broche de oro, comer la pizza favorita de mi amada preciosa"
    )

    val momentos = mutableListOf<MomentoFoto>()
    for (id in 1..8) {
        val uri = obtenerFotoUriDePrefsApp(context, id)
        if (uri != null) {
            momentos.add(
                MomentoFoto(
                    idActividad = id,
                    tituloActividad = titulosDefault[id] ?: "Actividad $id",
                    uri = uri
                )
            )
        }
    }
    return momentos
}

fun borrarFotoDePrefsApp(context: Context, idActividad: Int) {
    val prefs = context.getSharedPreferences("storyline_prefs", Context.MODE_PRIVATE)
    prefs.edit().remove("foto_actividad_$idActividad").apply()
}

fun exportarFotoAGaleriaApp(context: Context, uriOriginal: Uri): Boolean {
    return try {
        val resolver = context.contentResolver
        val inputStream: InputStream? = resolver.openInputStream(uriOriginal)

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Momento_Aniversario_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CronogramaAniversario")
            }
        }

        val uriGaleria = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uriGaleria != null && inputStream != null) {
            val outputStream: OutputStream? = resolver.openOutputStream(uriGaleria)
            outputStream?.use { out ->
                inputStream.use { inStream ->
                    inStream.copyTo(out)
                }
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun exportarTodasLasFotosAGaleriaApp(context: Context, listaMomentos: List<MomentoFoto>) {
    var exitosas = 0
    for (momento in listaMomentos) {
        if (exportarFotoAGaleriaApp(context, momento.uri)) {
            exitosas++
        }
    }

    if (exitosas > 0) {
        Toast.makeText(context, "¡Se guardaron $exitosas fotos en la Galería! 🖼️", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "No se pudieron exportar las fotos", Toast.LENGTH_SHORT).show()
    }
}