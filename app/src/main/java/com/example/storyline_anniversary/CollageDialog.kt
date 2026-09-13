package com.example.storyline_anniversary

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class ElementoFotoLienzo(
    val id: Long,
    val uri: Uri,
    offsetXInicial: Float = 0f,
    offsetYInicial: Float = 0f,
    scaleInicial: Float = 1f,
    rotationInicial: Float = 0f
) {
    var offsetX by mutableFloatStateOf(offsetXInicial)
    var offsetY by mutableFloatStateOf(offsetYInicial)
    var scale by mutableFloatStateOf(scaleInicial)
    var rotation by mutableFloatStateOf(rotationInicial)
}

@Composable
fun CollageDialog(
    fotosUris: List<Uri>,
    onDismiss: () -> Unit,
    onCollageGuardado: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var guardando by remember { mutableStateOf(false) }

    // Objeto para capturar el dibujo exacto del ComposableView
    val graphicsLayer = rememberGraphicsLayer()

    // Inicialización de imágenes
    val listaFotos = remember {
        mutableStateListOf<ElementoFotoLienzo>().apply {
            fotosUris.forEachIndexed { index, uri ->
                val desfasamiento = (index * 20 - 20).toFloat()
                add(
                    ElementoFotoLienzo(
                        id = index.toLong(),
                        uri = uri,
                        offsetXInicial = desfasamiento,
                        offsetYInicial = desfasamiento,
                        rotationInicial = if (index % 2 == 0) -6f else 6f
                    )
                )
            }
        }
    }

    var fotoSeleccionadaId by remember { mutableStateOf<Long?>(listaFotos.firstOrNull()?.id) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Usa tu imaginación mi amor",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Arrastra, gira o pellizca las fotos para acomodarlas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Área Interactiva del Lienzo (Capturable con graphicsLayer)
                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .drawWithContent {
                            // Record/Capturar la renderización exacta en la capa
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawContent()
                        }
                        .background(Color(0xFF1E1E2C), RoundedCornerShape(20.dp))
                        .clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    if (listaFotos.isEmpty()) {
                        Text(
                            text = "No hay fotos en el lienzo",
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    } else {
                        listaFotos.forEach { item ->
                            ItemFotoInteractiva(
                                elemento = item,
                                esSeleccionada = item.id == fotoSeleccionadaId && !guardando,
                                onClick = { fotoSeleccionadaId = item.id },
                                onTransform = { zoom, pan, rotationDelta ->
                                    item.scale = (item.scale * zoom).coerceIn(0.4f, 3f)
                                    item.rotation += rotationDelta

                                    // Corrección del vector de movimiento relativo al ángulo actual
                                    val rad = Math.toRadians(item.rotation.toDouble())
                                    val cosA = cos(rad).toFloat()
                                    val sinA = sin(rad).toFloat()

                                    val adjustedPanX = pan.x * cosA + pan.y * sinA
                                    val adjustedPanY = -pan.x * sinA + pan.y * cosA

                                    item.offsetX += adjustedPanX
                                    item.offsetY += adjustedPanY
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Herramientas para la foto seleccionada
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Traer al frente
                    IconButton(
                        onClick = {
                            fotoSeleccionadaId?.let { id ->
                                val index = listaFotos.indexOfFirst { it.id == id }
                                if (index != -1 && index < listaFotos.size - 1) {
                                    val item = listaFotos.removeAt(index)
                                    listaFotos.add(item)
                                }
                            }
                        },
                        enabled = fotoSeleccionadaId != null
                    ) {
                        Icon(Icons.Default.FlipToFront, contentDescription = "Traer al Frente")
                    }

                    // Resetear
                    IconButton(
                        onClick = {
                            fotoSeleccionadaId?.let { id ->
                                listaFotos.find { it.id == id }?.let { item ->
                                    item.offsetX = 0f
                                    item.offsetY = 0f
                                    item.scale = 1f
                                    item.rotation = 0f
                                }
                            }
                        },
                        enabled = fotoSeleccionadaId != null
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Resetear")
                    }

                    // Eliminar foto
                    IconButton(
                        onClick = {
                            fotoSeleccionadaId?.let { id ->
                                listaFotos.removeAll { it.id == id }
                                fotoSeleccionadaId = listaFotos.lastOrNull()?.id
                            }
                        },
                        enabled = fotoSeleccionadaId != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Eliminar",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botones Guardar y Cancelar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !guardando
                    ) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = listaFotos.isNotEmpty() && !guardando,
                        onClick = {
                            guardando = true
                            // Deseleccionamos para que no se guarde el borde azul de selección
                            fotoSeleccionadaId = null

                            coroutineScope.launch {
                                try {
                                    // Renderizar exactamente lo dibujado en la vista
                                    val imageBitmap = graphicsLayer.toImageBitmap()
                                    val bitmap = imageBitmap.asAndroidBitmap()

                                    // Guardar en la galería
                                    val uriResult = CollageUtils.guardarCollageEnGaleriaYApp(context, bitmap)

                                    if (uriResult != null) {
                                        val prefs = context.getSharedPreferences("storyline_prefs", Context.MODE_PRIVATE)
                                        val idNuevo = (System.currentTimeMillis() % 10000).toInt()
                                        prefs.edit().putString("foto_actividad_$idNuevo", uriResult.toString()).apply()

                                        Toast.makeText(context, "¡Collage guardado en Galería!", Toast.LENGTH_LONG).show()
                                        onCollageGuardado()
                                    } else {
                                        Toast.makeText(context, "Error al guardar el collage", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    Toast.makeText(context, "Ocurrió un error al procesar la imagen", Toast.LENGTH_SHORT).show()
                                } finally {
                                    guardando = false
                                }
                            }
                        }
                    ) {
                        if (guardando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Guardar Collage")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemFotoInteractiva(
    elemento: ElementoFotoLienzo,
    esSeleccionada: Boolean,
    onClick: () -> Unit,
    onTransform: (zoom: Float, pan: Offset, rotation: Float) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnTransform by rememberUpdatedState(onTransform)

    Box(
        modifier = Modifier
            .offset { IntOffset(elemento.offsetX.roundToInt(), elemento.offsetY.roundToInt()) }
            .graphicsLayer(
                scaleX = elemento.scale,
                scaleY = elemento.scale,
                rotationZ = elemento.rotation
            )
            .pointerInput(elemento.id) {
                detectTransformGestures { _, pan, zoom, rotation ->
                    currentOnClick()
                    currentOnTransform(zoom, pan, rotation)
                }
            }
            .size(140.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(
                width = if (esSeleccionada) 3.dp else 0.dp,
                color = if (esSeleccionada) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(6.dp)
    ) {
        Image(
            painter = rememberAsyncImagePainter(elemento.uri),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )
    }
}