package com.aplicaciones_android.ae2_abpro1

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.provider.CalendarContract
import android.content.ContentUris
import android.util.Log
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG_REC = "AE2RemReceiver"

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val titleExtra = intent.getStringExtra("title")
            val minutesBefore = intent.getIntExtra("minutesBefore", 0)
            val eventId = intent.getLongExtra("eventId", -1L)

            Log.d(TAG_REC, "onReceive: eventId=$eventId minutesBefore=$minutesBefore title=$titleExtra")
            // Toast para depuración rápida (puede ser útil en pruebas)
            try { Toast.makeText(context, "Notificación recibida: $minutesBefore min antes", Toast.LENGTH_SHORT).show() } catch (_: Exception) {}

            val channelId = "reminders_channel"
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
                channel.description = "Notificaciones de recordatorios previos a eventos"
                nm.createNotificationChannel(channel)
            }

            // Obtener detalles del evento si tenemos eventId
            var contentTitle = titleExtra ?: context.getString(R.string.app_name)
            var contentText = when (minutesBefore) {
                60 -> "Falta 1 hora para el evento"
                30 -> "Faltan 30 minutos para el evento"
                10 -> "Faltan 10 minutos para el evento"
                else -> "Próximo evento"
            }
            var bigText = contentText

            if (eventId != -1L) {
                try {
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                    val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.EVENT_LOCATION)
                    val cursor = context.contentResolver.query(uri, projection, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val evTitle = c.getString(0) ?: contentTitle
                            val evDt = c.getLong(1)
                            val evLoc = c.getString(2) ?: ""
                            contentTitle = evTitle
                            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                            val whenStr = try { sdf.format(Date(evDt)) } catch (_: Exception) { evDt.toString() }
                            bigText = "$contentText\n$whenStr ${if (evLoc.isNotEmpty()) "• $evLoc" else ""}"
                            contentText = "$whenStr ${if (evLoc.isNotEmpty()) "• $evLoc" else ""}"
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG_REC, "error reading event details: ${e.message}")
                }
            }

            // Intent para abrir la aplicación y llevar al Recordatorios para ese eventId
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (eventId != -1L) putExtra("openEventId", eventId)
            }
            val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            val contentPi = PendingIntent.getActivity(context, ((eventId xor minutesBefore.toLong()).and(0xffffffff)).toInt(), openIntent, piFlags)

            val notif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentPi)
                .build()

            val notifId = if (eventId != -1L) (eventId xor minutesBefore.toLong()).toInt() else minutesBefore
            nm.notify(notifId, notif)
        } catch (e: Exception) {
            Log.e(TAG_REC, "onReceive error: ${e.message}")
        }
    }
}
