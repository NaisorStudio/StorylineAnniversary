package com.example.storyline_anniversary

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificacionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val titulo = intent.getStringExtra("TITULO") ?: "Notificación"
        val mensaje = intent.getStringExtra("MENSAJE") ?: ""
        val idActividad = intent.getIntExtra("ID_ACTIVIDAD", 0)

        val canalId = "canal_actividades_aniversario_v2"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val sonidoUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.imyour}")

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                canalId,
                "Notificaciones de Cronograma",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(sonidoUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(canal)
        }

        val intentAbrirApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            idActividad,
            intentAbrirApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, canalId)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(sonidoUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        notificationManager.notify(idActividad, builder.build())
    }
}