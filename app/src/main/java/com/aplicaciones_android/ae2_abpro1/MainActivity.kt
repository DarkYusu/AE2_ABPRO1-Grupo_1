package com.aplicaciones_android.ae2_abpro1

import android.os.Bundle
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Crear canal de notificaciones para la app (asegurar existencia)
        try {
            val channelId = "reminders_channel"
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existing = nm.getNotificationChannel(channelId)
                if (existing == null) {
                    val channel = NotificationChannel(channelId, "Recordatorios", NotificationManager.IMPORTANCE_HIGH)
                    channel.description = "Notificaciones de recordatorios previos a eventos"
                    nm.createNotificationChannel(channel)
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Fragmento por defecto
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, Calendario())
                .commit()
        }

        // Configurar navegación simple con botones
        val btnIngresar = findViewById<Button>(R.id.btnIngresar)
        val btnRecordatorios = findViewById<Button>(R.id.btnRecordatorios)

        btnIngresar.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainerView, Calendario())
                .commit()
        }

        btnRecordatorios.setOnClickListener {
            // Reutilizar instancia si existe
            val existing = supportFragmentManager.findFragmentByTag("recordatorios") as? Recordatorios
            if (existing != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainerView, existing, "recordatorios")
                    .commit()
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainerView, Recordatorios(), "recordatorios")
                    .commit()
            }
        }

        // Manejar si la actividad fue lanzada con un eventId para resaltar
        handleOpenEventIntent(intent?.getLongExtra("openEventId", -1L) ?: -1L)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val eventId = intent.getLongExtra("openEventId", -1L)
        handleOpenEventIntent(eventId)
    }

    private fun handleOpenEventIntent(eventId: Long) {
        if (eventId <= 0) return
        // Buscar instancia existente por tag
        val existing = supportFragmentManager.findFragmentByTag("recordatorios") as? Recordatorios
        if (existing == null) {
            val frag = Recordatorios()
            supportFragmentManager.beginTransaction().replace(R.id.fragmentContainerView, frag, "recordatorios").commitNow()
            try { frag.highlightEvent(eventId) } catch (_: Exception) {}
        } else {
            // Si existe, asegurarnos que se muestre y llamamos al método
            supportFragmentManager.beginTransaction().replace(R.id.fragmentContainerView, existing, "recordatorios").commitNow()
            try { existing.highlightEvent(eventId) } catch (_: Exception) {}
        }
    }
}