package com.aplicaciones_android.ae2_abpro1

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import androidx.fragment.app.Fragment
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"
private const val PREFS_NAME = "reminders_prefs"
private const val PREFS_KEY_EVENT_IDS = "event_ids"
private const val TAG = "AE2Rem"

class Calendario : Fragment() {
    private var param1: String? = null
    private var param2: String? = null

    // Variables para reintentar después de pedir permiso
    private var pendingInicioMillis: Long? = null
    private var pendingTitulo: String? = null
    private var pendingUbicacion: String? = null
    private var pendingDescripcion: String? = null

    // Flags para evitar abrir dos veces los pickers
    private var isDatePickerShowing = false
    private var isTimePickerShowing = false

    // Activity Result API para permisos de escritura en calendario
    private val writePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val inicio = pendingInicioMillis
            val t = pendingTitulo
            val u = pendingUbicacion
            val desc = pendingDescripcion
            if (inicio != null && t != null) {
                insertEventToCalendar(inicio, t, u ?: "", desc ?: "")
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.msg_perm_denegado_cal), Toast.LENGTH_LONG).show()
            pendingInicioMillis?.let { inicio ->
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    data = CalendarContract.Events.CONTENT_URI
                    putExtra(CalendarContract.Events.TITLE, pendingTitulo)
                    putExtra(CalendarContract.Events.EVENT_LOCATION, pendingUbicacion)
                    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, inicio)
                    putExtra(CalendarContract.Events.DESCRIPTION, pendingDescripcion)
                }
                startActivity(intent)
            }
        }
        // limpiar pendientes
        pendingInicioMillis = null
        pendingTitulo = null
        pendingUbicacion = null
        pendingDescripcion = null
    }

    // Activity Result API para permiso de notificaciones (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // si concedido, programar usando los últimos datos pendientes
            val eId = lastScheduledEventId
            val start = lastScheduledInicio
            val t = lastScheduledTitle
            if (eId != null && start != null && t != null) {
                scheduleNotifications(eId, start, t)
            }
        } else {
            // si no concede, simplemente no programamos notificaciones
            Toast.makeText(requireContext(), "Permiso de notificaciones denegado", Toast.LENGTH_LONG).show()
        }
        // limpiar
        lastScheduledEventId = null
        lastScheduledInicio = null
        lastScheduledTitle = null
    }

    // Launcher adicional para la prueba de notificación (solicitar permiso y luego ejecutar action de prueba)
    private val testNotifyPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // reenviar broadcast de prueba inmediatamente
            try {
                val testIntent = Intent(requireContext(), NotificationReceiver::class.java).apply {
                    putExtra("title", "Notificación de prueba")
                    putExtra("minutesBefore", 0)
                    putExtra("eventId", 0L)
                }
                requireContext().sendBroadcast(testIntent)
                Toast.makeText(requireContext(), "Broadcast de prueba enviado (tras permiso)", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Broadcast de prueba enviado tras permiso")
            } catch (e: Exception) {
                Log.d(TAG, "Error reenviando broadcast de prueba: ${e.message}")
            }
        } else {
            Toast.makeText(requireContext(), "Permiso de notificaciones no concedido. No se enviará notificación de prueba.", Toast.LENGTH_LONG).show()
        }
    }

    // Variables para programación pendiente de notificaciones
    private var lastScheduledEventId: Long? = null
    private var lastScheduledInicio: Long? = null
    private var lastScheduledTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendario, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val button = view.findViewById<Button>(R.id.boton_calendario)
        val titulo = view.findViewById<EditText>(R.id.inputTitulo)
        val ubicacion = view.findViewById<EditText>(R.id.inputLugar)
        val fecha = view.findViewById<EditText>(R.id.inputFecha)
        val hora = view.findViewById<EditText>(R.id.inputHora)
        val descripcion = view.findViewById<EditText>(R.id.descripcionEvento)

        // Intento desactivar el teclado por código para compatibilidad
        try {
            fecha.showSoftInputOnFocus = false
            hora.showSoftInputOnFocus = false
        } catch (_: Exception) {
            // metodo no disponible en versiones antiguas -> se ignora
        }

        val now = Calendar.getInstance()
        var selYear = now.get(Calendar.YEAR)
        var selMonth = now.get(Calendar.MONTH)
        var selDay = now.get(Calendar.DAY_OF_MONTH)
        var selHour = now.get(Calendar.HOUR_OF_DAY)
        var selMinute = now.get(Calendar.MINUTE)

        val formatoFecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun showDatePicker() {
            if (isDatePickerShowing) return
            isDatePickerShowing = true
            val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
                selYear = y
                selMonth = m
                selDay = d
                val tmp = Calendar.getInstance()
                tmp.set(selYear, selMonth, selDay)
                fecha.setText(formatoFecha.format(tmp.time))
            }, selYear, selMonth, selDay)
            dp.setOnDismissListener { isDatePickerShowing = false }
            dp.show()
        }

        fun showTimePicker() {
            if (isTimePickerShowing) return
            isTimePickerShowing = true
            val tp = TimePickerDialog(requireContext(), { _, h, min ->
                selHour = h
                selMinute = min
                hora.setText(String.format(Locale.getDefault(), "%02d:%02d", selHour, selMinute))
            }, selHour, selMinute, true)
            tp.setOnDismissListener { isTimePickerShowing = false }
            tp.show()
        }

        fecha.setOnClickListener { showDatePicker() }
        hora.setOnClickListener { showTimePicker() }

        // Botón de prueba de notificación (envía broadcast inmediato y programa una alarma a 10s)
        val btnTest = view.findViewById<Button>(R.id.btn_test_notif)
        btnTest.setOnClickListener {
            // preparar intent de prueba
            val testIntent = Intent(requireContext(), NotificationReceiver::class.java).apply {
                putExtra("title", "Notificación de prueba")
                putExtra("minutesBefore", 0)
                putExtra("eventId", 0L)
            }
            // Antes de enviar, en Android 13+ verificar permiso POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    // solicitar permiso y el launcher reenviará el broadcast si se concede
                    testNotifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    try {
                        requireContext().sendBroadcast(testIntent)
                        Toast.makeText(requireContext(), "Broadcast de prueba enviado", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Broadcast de prueba enviado")
                    } catch (e: Exception) {
                        Log.d(TAG, "Error enviando broadcast de prueba: ${e.message}")
                    }

                    // programar alarma a 10 segundos
                    val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    val triggerAt = System.currentTimeMillis() + 10_000L
                    val requestCode = (System.currentTimeMillis() % Integer.MAX_VALUE).toInt()
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    val pi = PendingIntent.getBroadcast(requireContext(), requestCode, testIntent, flags)

                    try {
                        am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                        Toast.makeText(requireContext(), "Alarma de prueba programada a 10s", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "Alarma de prueba programada para ${Date(triggerAt)} requestCode=$requestCode")
                    } catch (e: Exception) {
                        Log.d(TAG, "Error programando alarma de prueba: ${e.message}")
                    }
                }
            } else {
                // Android < 13: enviar y programar directamente
                try {
                    requireContext().sendBroadcast(testIntent)
                    Toast.makeText(requireContext(), "Broadcast de prueba enviado", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Broadcast de prueba enviado")
                } catch (e: Exception) {
                    Log.d(TAG, "Error enviando broadcast de prueba: ${e.message}")
                }

                val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val triggerAt = System.currentTimeMillis() + 10_000L
                val requestCode = (System.currentTimeMillis() % Integer.MAX_VALUE).toInt()
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                val pi = PendingIntent.getBroadcast(requireContext(), requestCode, testIntent, flags)

                try {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    Toast.makeText(requireContext(), "Alarma de prueba programada a 10s", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Alarma de prueba programada para ${Date(triggerAt)} requestCode=$requestCode")
                } catch (e: Exception) {
                    Log.d(TAG, "Error programando alarma de prueba: ${e.message}")
                }
            }
        }

        button.setOnClickListener {
            val startCal = Calendar.getInstance()
            startCal.set(selYear, selMonth, selDay, selHour, selMinute, 0)
            val inicioMillis = startCal.timeInMillis

            val t = titulo.text.toString()
            val u = ubicacion.text.toString()
            val desc = descripcion.text.toString()

            // Guardar datos en variables pendientes por si pedimos permiso
            pendingInicioMillis = inicioMillis
            pendingTitulo = t
            pendingUbicacion = u
            pendingDescripcion = desc

            // Verificar permiso de escritura en calendario
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                insertEventToCalendar(inicioMillis, t, u, desc)
            } else {
                // pedir permiso con Activity Result API
                writePermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
            }
        }
    }

    private fun insertEventToCalendar(inicioMillis: Long, titulo: String, ubicacion: String, descripcion: String) {
        val calId = getPrimaryCalendarId()
        if (calId == -1L) {
            Toast.makeText(requireContext(), getString(R.string.msg_no_calendario), Toast.LENGTH_LONG).show()
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, titulo)
                putExtra(CalendarContract.Events.EVENT_LOCATION, ubicacion)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, inicioMillis)
                putExtra(CalendarContract.Events.DESCRIPTION, descripcion)
            }
            startActivity(intent)
            return
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, inicioMillis)
            put(CalendarContract.Events.DTEND, inicioMillis + 60 * 60 * 1000)
            put(CalendarContract.Events.TITLE, titulo)
            put(CalendarContract.Events.EVENT_LOCATION, ubicacion)
            put(CalendarContract.Events.DESCRIPTION, descripcion)
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }

        try {
            val uri = requireContext().contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (uri != null) {
                val eventId = ContentUris.parseId(uri)
                saveEventId(eventId)
                // Log para depuración: evento insertado
                Log.d(TAG, "Evento insertado eventId=$eventId inicio=${Date(inicioMillis)} titulo=$titulo")
                // Programar notificaciones 60, 30, 10 minutos antes (pedir permiso si es necesario)
                // Guardamos datos por si tenemos que pedir permiso de notificaciones
                lastScheduledEventId = eventId
                lastScheduledInicio = inicioMillis
                lastScheduledTitle = titulo
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+: requerimos permiso POST_NOTIFICATIONS
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        scheduleNotifications(eventId, inicioMillis, titulo)
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    scheduleNotifications(eventId, inicioMillis, titulo)
                }

                Toast.makeText(requireContext(), getString(R.string.msg_guardado), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.msg_no_guardado), Toast.LENGTH_LONG).show()
            }
        } catch (_: SecurityException) {
            Toast.makeText(requireContext(), getString(R.string.msg_perm_denegado_escritura), Toast.LENGTH_LONG).show()
        }
    }

    private fun scheduleNotifications(eventId: Long, inicioMillis: Long, title: String) {
        // incluir 0 para notificación al inicio del evento
        val minutesList = listOf(60, 30, 10, 0)
        val now = System.currentTimeMillis()
        val am = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (minutes in minutesList) {
            val triggerAt = inicioMillis - minutes * 60_000L
            if (triggerAt <= now) {
                Log.d(TAG, "Skipping scheduling for $minutes min before: triggerAt in past: ${Date(triggerAt)}")
                continue // no programar alarmas en el pasado
            }

            val intent = Intent(requireContext(), NotificationReceiver::class.java).apply {
                putExtra("title", title)
                putExtra("minutesBefore", minutes)
                putExtra("eventId", eventId)
            }

            val requestCode = ((eventId xor minutes.toLong()).and(0xffffffff)).toInt()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val pi = PendingIntent.getBroadcast(requireContext(), requestCode, intent, flags)

            Log.d(TAG, "Scheduling alarm: eventId=$eventId minutesBefore=$minutes triggerAt=${Date(triggerAt)} requestCode=$requestCode")
            try {
                am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to schedule alarm for requestCode=$requestCode: ${e.message}")
            }
        }
    }

    private fun getPrimaryCalendarId(): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val cursor = requireContext().contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }
        return -1L
    }

    private fun saveEventId(eventId: Long) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(PREFS_KEY_EVENT_IDS, mutableSetOf()) ?: mutableSetOf()
        val newSet = existing.toMutableSet()
        newSet.add(eventId.toString())
        prefs.edit().putStringSet(PREFS_KEY_EVENT_IDS, newSet).apply()
    }

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            Calendario().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}