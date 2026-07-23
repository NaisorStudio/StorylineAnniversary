package com.example.storyline_anniversary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificacionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val titulo = intent.getStringExtra("TITULO") ?: "Próxima Actividad"
        val mensaje = intent.getStringExtra("MENSAJE") ?: "Tu actividad comenzará en 5 minutos ⏰"
        val idActividad = intent.getIntExtra("ID_ACTIVIDAD", 0)

        mostrarNotificacion(context, titulo, mensaje, idActividad)
    }

    private fun mostrarNotificacion(context: Context, titulo: String, mensaje: String, idNotificacion: Int) {
        val channelId = "canal_cronograma_aniversario"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal de notificación (Requerido para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sonidoUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val canal = NotificationChannel(
                channelId,
                "Recordatorios del Cronograma",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para actividades a punto de iniciar"
                setSound(sonidoUri, null)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(canal)
        }

        val sonidoPredeterminado = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Reemplaza por tu ícono si prefieres
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(sonidoPredeterminado)
            .setAutoCancel(true)

        notificationManager.notify(idNotificacion, builder.build())
    }
}