package com.example.storyline_anniversary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class VerificadorVersionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val urlConBuster = "${ConfigApp.URL_API_GIST}?t=${System.currentTimeMillis()}"
                val url = URL(urlConBuster)
                val conexion = url.openConnection() as HttpURLConnection

                conexion.useCaches = false
                conexion.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                conexion.setRequestProperty("Pragma", "no-cache")
                conexion.setRequestProperty("User-Agent", "Android-App")
                conexion.connectTimeout = 5000
                conexion.readTimeout = 5000

                if (conexion.responseCode == 200) {
                    val respuestaRaw = conexion.inputStream.bufferedReader().use { it.readText() }
                    val jsonGistApi = JSONObject(respuestaRaw)
                    val filesObject = jsonGistApi.getJSONObject("files")
                    val nombreArchivo = filesObject.keys().next()
                    val contenidoString = filesObject.getJSONObject(nombreArchivo).getString("content")

                    val jsonObject = JSONObject(contenidoString)
                    val versionMinima = jsonObject.optString("version_minima", ConfigApp.VERSION_LOCAL)
                    val mensaje = jsonObject.optString("mensaje_bloqueo", "Hay una nueva actualización disponible.")

                    // Verificar si la versión instalada está obsoleta
                    if (esVersionObsoleta(ConfigApp.VERSION_LOCAL, versionMinima)) {
                        mostrarNotificacionActualizacion(context, mensaje)
                    }
                }
                conexion.disconnect()
                Result.success()
            } catch (e: Exception) {
                Log.e("WORKER_VERSION", "Error al verificar versión en segundo plano: ${e.message}")
                Result.retry()
            }
        }
    }

    private fun mostrarNotificacionActualizacion(context: Context, mensaje: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "canal_actualizaciones_app"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Actualizaciones de la Aplicación",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones cuando hay una nueva versión disponible para descargar."
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Al tocar la notificación se abre la MainActivity donde sale el diálogo de descarga
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificacion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.logo) // Asegúrate de usar un icono válido de res/drawable
            .setContentTitle("¡Nueva versión disponible! 🚀")
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1001, notificacion)
    }
}