package com.aplicaciones_android.ae2_abpro1

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import java.text.SimpleDateFormat
import java.util.Locale

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [Calendario.newInstance] factory method to
 * create an instance of this fragment.
 */
class Calendario : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null


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
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_calendario, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val button = view.findViewById<Button>(R.id.boton_calendario)
        val titulo = view.findViewById<EditText>(R.id.inputTitulo)
        val ubicacion = view.findViewById<EditText>(R.id.inputLugar)
        val horaInicio = view.findViewById<EditText>(R.id.inputFechaHora)
        // val horaFin = view.findViewById<EditText>(R.id.inputHoraFin)

        button.setOnClickListener {
            val formato = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val inicioTexto = horaInicio.text.toString()
            val inicioMillis = try { formato.parse(inicioTexto)?.time } catch (e: Exception) { null }

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, titulo.text.toString())
                putExtra(CalendarContract.Events.EVENT_LOCATION, ubicacion.text.toString())
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, inicioMillis ?: System.currentTimeMillis())
                putExtra(CalendarContract.Events.DESCRIPTION, finMillis ?: (inicioMillis ?: System.currentTimeMillis()) + 60*60*1000)
            }
            startActivity(intent)

        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment Calendario.
         */
        // TODO: Rename and change types and number of parameters
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