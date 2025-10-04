package com.aplicaciones_android.ae2_abpro1

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.CalendarContract
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFS_NAME = "reminders_prefs"
private const val PREFS_KEY_EVENT_IDS = "event_ids"

class Recordatorios : Fragment() {

    // Activity Result API para permisos de lectura
    private val readPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // si se concede, registrar observer y recargar
            registerCalendarObserver()
            loadAndShow()
        } else {
            Toast.makeText(requireContext(), getString(R.string.msg_perm_lectura_denegada), Toast.LENGTH_LONG).show()
        }
    }

    private var btnActualizar: Button? = null
    private var listView: ListView? = null
    private var tvEmpty: TextView? = null
    private var calendarObserver: ContentObserver? = null

    // Mantener la lista actual de eventIds en orden mostrado para poder resaltar
    private var currentIdsList: List<Long> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recordatorios, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnActualizar = view.findViewById<Button>(R.id.btnActualizar)
        listView = view.findViewById<ListView>(R.id.listRecordatorios)
        tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)

        btnActualizar?.setOnClickListener { loadAndShow() }

        // Registrar observer si ya tenemos permiso; si no, pedirlo
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            registerCalendarObserver()
            loadAndShow()
        } else {
            readPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // desregistrar el observer para evitar fugas
        calendarObserver?.let {
            requireContext().contentResolver.unregisterContentObserver(it)
            calendarObserver = null
        }
        btnActualizar = null
        listView = null
        tvEmpty = null
    }

    private fun registerCalendarObserver() {
        if (calendarObserver != null) return
        calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                loadAndShow()
            }

            override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
                loadAndShow()
            }
        }
        requireContext().contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver!!)
    }

    private fun loadAndShow() {
        // Verificar permiso
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            readPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
            return
        }

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(PREFS_KEY_EVENT_IDS, emptySet()) ?: emptySet()
        if (ids.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            listView?.adapter = null
            currentIdsList = emptyList()
            return
        }

        val items = mutableListOf<String>()
        val idsList = mutableListOf<Long>()
        val foundIds = mutableSetOf<String>()
        val projection = arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
        for (idStr in ids) {
            val id = idStr.toLongOrNull() ?: continue
            val selection = "${CalendarContract.Events._ID} = ?"
            val cursor = requireContext().contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                arrayOf(id.toString()),
                null
            )
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val title = c.getString(0) ?: "(Sin título)"
                    val dt = c.getLong(1)
                    val dateStr = formatDate(dt)
                    items.add("$title — $dateStr")
                    idsList.add(id)
                    foundIds.add(idStr)
                }
            }
        }

        // Si hay eventIds en prefs que ya no existen en el calendario, los quitamos y cancelamos sus alarmas
        val removed = ids.toMutableSet().apply { removeAll(foundIds) }
        if (removed.isNotEmpty()) {
            // cancelar alarmas y notificaciones para cada removed id
            for (r in removed) {
                val eid = r.toLongOrNull() ?: continue
                cancelScheduledNotifications(eid)
            }
            // actualizar SharedPreferences
            val newSet = ids.toMutableSet().apply { removeAll(removed) }
            prefs.edit().putStringSet(PREFS_KEY_EVENT_IDS, newSet).apply()
        }

        if (items.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            listView?.adapter = null
            currentIdsList = emptyList()
        } else {
            tvEmpty?.visibility = View.GONE
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
            listView?.adapter = adapter
            currentIdsList = idsList.toList()

            // Abrir evento en calendario al tocar el elemento
            listView?.setOnItemClickListener { _, _, position, _ ->
                val eventId = idsList.getOrNull(position) ?: return@setOnItemClickListener
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                val intent = Intent(Intent.ACTION_VIEW).setData(uri)
                startActivity(intent)
            }
        }
    }

    private fun cancelScheduledNotifications(eventId: Long) {
        val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nm = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val minutesList = listOf(60, 30, 10)
        for (minutes in minutesList) {
            val requestCode = ((eventId xor minutes.toLong()).and(0xffffffff)).toInt()
            val intent = Intent(requireContext(), NotificationReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(requireContext(), requestCode, intent, flags)
            try {
                am.cancel(pi)
            } catch (_: Exception) {
            }
            // cancelar notificación si está activa
            val notifId = try { (eventId xor minutes.toLong()).toInt() } catch (_: Exception) { minutes }
            try { nm.cancel(notifId) } catch (_: Exception) {}
        }
    }

    // Publico: resalta/un scroll hacia el evento en la lista si está presente; si no está cargada, reintenta
    fun highlightEvent(eventId: Long) {
        val attempt = Runnable {
            val idx = currentIdsList.indexOf(eventId)
            if (idx >= 0) {
                listView?.setSelection(idx)
                // opcional: destacar con un Toast
                Toast.makeText(requireContext(), "Evento resaltado", Toast.LENGTH_SHORT).show()
            } else {
                // intentar recargar y reintentar una vez
                loadAndShow()
                Handler(Looper.getMainLooper()).postDelayed({
                    val idx2 = currentIdsList.indexOf(eventId)
                    if (idx2 >= 0) {
                        listView?.setSelection(idx2)
                        Toast.makeText(requireContext(), "Evento resaltado", Toast.LENGTH_SHORT).show()
                    }
                }, 400)
            }
        }
        Handler(Looper.getMainLooper()).post(attempt)
    }

    private fun formatDate(millis: Long): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            sdf.format(Date(millis))
        } catch (_: Exception) {
            millis.toString()
        }
    }

}
