package com.example.storyline_anniversary

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.os.Environment

enum class EstiloCollage(val titulo: String) {
    GRID_ELEGANTE("Cuadrícula Moderna"),
    MOSAICO_DIVERTIMENTO("Mosaico Dinámico"),
    MARCOS_POLAROID("Estilo Polaroid"),
    TIRA_FOTOGRAFICA("Tira de Fotos (Film)"),
    PANORAMICO_DESTACADO("Destacado Central")
}

object CollageUtils {

    suspend fun generarBitmapCollage(
        context: Context,
        uris: List<Uri>,
        estilo: EstiloCollage,
        anchoAncho: Int = 1080,
        altoAlto: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val imageLoader = ImageLoader(context)
            val bitmaps = uris.mapNotNull { uri ->
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                if (result is SuccessResult) result.drawable.toBitmap() else null
            }

            if (bitmaps.isEmpty()) return@withContext null

            val resultado = Bitmap.createBitmap(anchoAncho, altoAlto, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(resultado)
            val paint = Paint().apply { isAntiAlias = true }

            when (estilo) {
                EstiloCollage.GRID_ELEGANTE -> renderGridElegante(canvas, bitmaps, anchoAncho, altoAlto, paint)
                EstiloCollage.MOSAICO_DIVERTIMENTO -> renderMosaico(canvas, bitmaps, anchoAncho, altoAlto, paint)
                EstiloCollage.MARCOS_POLAROID -> renderPolaroid(canvas, bitmaps, anchoAncho, altoAlto, paint)
                EstiloCollage.TIRA_FOTOGRAFICA -> renderTiraFilm(canvas, bitmaps, anchoAncho, altoAlto, paint)
                EstiloCollage.PANORAMICO_DESTACADO -> renderDestacado(canvas, bitmaps, anchoAncho, altoAlto, paint)
            }

            return@withContext resultado
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun renderGridElegante(canvas: Canvas, bitmaps: List<Bitmap>, w: Int, h: Int, paint: Paint) {
        canvas.drawColor(Color.WHITE)
        val espaciado = 16f
        val cols = if (bitmaps.size > 2) 2 else 1
        val filas = Math.ceil(bitmaps.size.toDouble() / cols).toInt()
        val celdaW = (w - (espaciado * (cols + 1))) / cols
        val celdaH = (h - (espaciado * (filas + 1))) / filas

        bitmaps.forEachIndexed { i, bmp ->
            val col = i % cols
            val fila = i / cols
            val left = espaciado + col * (celdaW + espaciado)
            val top = espaciado + fila * (celdaH + espaciado)
            val rect = RectF(left, top, left + celdaW, top + celdaH)
            dibujarBitmapRecortado(canvas, bmp, rect, paint)
        }
    }

    private fun renderMosaico(canvas: Canvas, bitmaps: List<Bitmap>, w: Int, h: Int, paint: Paint) {
        canvas.drawColor(Color.parseColor("#121212"))
        val pad = 12f
        if (bitmaps.size == 1) {
            dibujarBitmapRecortado(canvas, bitmaps[0], RectF(pad, pad, w - pad, h - pad), paint)
        } else {
            val mitadW = w / 2f
            dibujarBitmapRecortado(canvas, bitmaps[0], RectF(pad, pad, mitadW - pad, h - pad), paint)
            val hSub = (h - (pad * (bitmaps.size))) / (bitmaps.size - 1)
            for (i in 1 until bitmaps.size) {
                val top = pad + (i - 1) * (hSub + pad)
                dibujarBitmapRecortado(canvas, bitmaps[i], RectF(mitadW + pad, top, w - pad, top + hSub), paint)
            }
        }
    }

    private fun renderPolaroid(canvas: Canvas, bitmaps: List<Bitmap>, w: Int, h: Int, paint: Paint) {
        canvas.drawColor(Color.parseColor("#F5F2EB"))
        bitmaps.forEachIndexed { index, bmp ->
            canvas.save()
            val angulos = floatArrayOf(-8f, 6f, -4f, 10f, -12f)
            val angulo = angulos[index % angulos.size]
            val cardW = w * 0.45f
            val cardH = cardW * 1.2f
            val cx = (w / 4f) + (index % 2) * (w / 2f)
            val cy = (h / 4f) + (index / 2) * (h / 2f)

            canvas.rotate(angulo, cx, cy)
            val rectCard = RectF(cx - cardW / 2, cy - cardH / 2, cx + cardW / 2, cy + cardH / 2)

            paint.color = Color.WHITE
            canvas.drawRoundRect(rectCard, 12f, 12f, paint)

            val photoPad = 16f
            val rectPhoto = RectF(rectCard.left + photoPad, rectCard.top + photoPad, rectCard.right - photoPad, rectCard.bottom - (photoPad * 4))
            dibujarBitmapRecortado(canvas, bmp, rectPhoto, paint)
            canvas.restore()
        }
    }

    private fun renderTiraFilm(canvas: Canvas, bitmaps: List<Bitmap>, w: Int, h: Int, paint: Paint) {
        canvas.drawColor(Color.parseColor("#1A1A1A"))
        paint.color = Color.DKGRAY
        val padH = w * 0.15f
        val rectTira = RectF(padH, 0f, w - padH, h.toFloat())
        canvas.drawRect(rectTira, paint)

        val itemH = (h - 40f) / bitmaps.size
        bitmaps.forEachIndexed { i, bmp ->
            val top = 20f + i * itemH
            val rectFoto = RectF(padH + 20f, top + 10f, w - padH - 20f, top + itemH - 10f)
            dibujarBitmapRecortado(canvas, bmp, rectFoto, paint)
        }
    }

    private fun renderDestacado(canvas: Canvas, bitmaps: List<Bitmap>, w: Int, h: Int, paint: Paint) {
        canvas.drawColor(Color.parseColor("#FAFAFA"))
        val pad = 10f
        val altoPrincipal = h * 0.6f
        dibujarBitmapRecortado(canvas, bitmaps[0], RectF(pad, pad, w - pad, altoPrincipal - pad), paint)

        val restantes = bitmaps.drop(1)
        if (restantes.isNotEmpty()) {
            val anchoSub = (w - (pad * (restantes.size + 1))) / restantes.size
            restantes.forEachIndexed { idx, bmp ->
                val left = pad + idx * (anchoSub + pad)
                dibujarBitmapRecortado(canvas, bmp, RectF(left, altoPrincipal + pad, left + anchoSub, h - pad), paint)
            }
        }
    }

    private fun dibujarBitmapRecortado(canvas: Canvas, bmp: Bitmap, dest: RectF, paint: Paint) {
        val srcRatio = bmp.width.toFloat() / bmp.height.toFloat()
        val destRatio = dest.width() / dest.height()
        val srcRect = if (srcRatio > destRatio) {
            val newW = bmp.height * destRatio
            val left = (bmp.width - newW) / 2f
            android.graphics.Rect(left.toInt(), 0, (left + newW).toInt(), bmp.height)
        } else {
            val newH = bmp.width / destRatio
            val top = (bmp.height - newH) / 2f
            android.graphics.Rect(0, top.toInt(), bmp.width, (top + newH).toInt())
        }
        canvas.drawBitmap(bmp, srcRect, dest, paint)
    }

    suspend fun guardarCollageEnGaleriaYApp(context: Context, bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        val nombreArchivo = "Collage_${System.currentTimeMillis()}.jpg"
        var uriResultado: Uri? = null

        try {
            // 1. Guardar en carpeta interna de la App
            val dirInterno = File(context.getExternalFilesDir(null), "Pictures")
            if (!dirInterno.exists()) dirInterno.mkdirs()
            val archivoInterno = File(dirInterno, nombreArchivo)
            FileOutputStream(archivoInterno).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // 2. Guardar en Galería Pública del Dispositivo (MediaStore)
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/StorylineAnniversary")
                }
            }

            val resolver = context.contentResolver
            val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            imageUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                uriResultado = uri
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext uriResultado
    }
}