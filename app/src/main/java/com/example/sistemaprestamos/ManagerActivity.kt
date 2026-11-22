package com.example.sistemaprestamos

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

class ManagerActivity : AppCompatActivity() {

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    private var clienteAceptadoActual = "Carlos Mendoza (#PR-001)"
    private var montoAceptadoActual = 10000.0
    private var tasaAceptadaActual = 15.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager)

        inicializarEjemploSiVacio()
        cargarYRenderizarSolicitudes()

        findViewById<Button>(R.id.btnVerAbonos).setOnClickListener {
            val intent = Intent(this, ManagerAbonosActivity::class.java).apply {
                putExtra("CLIENTE_NOMBRE", clienteAceptadoActual)
                putExtra("MONTO_PRESTAMO", montoAceptadoActual)
                putExtra("TASA_INTERES", tasaAceptadaActual)
                putExtra("DIAS_ATRASO", 0)
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnMgrVolver).setOnClickListener {
            finish()
        }
    }

    private fun inicializarEjemploSiVacio() {
        val prefs = getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("inicializado", false)) {
            val ejemplo = JSONObject().apply {
                put("folio", "#SOL-101")
                put("usuario_cuenta", "cliente")
                put("cliente", "Sofía Ramírez")
                put("monto", 12000.0)
                put("frecuencia", "Quincenal")
                put("plazo", 8)
                put("tasa_interes", 15.0)
                put("fecha", "21/11/2026")
                put("estado", "PENDIENTE")
                put("abonado", 0.0)
                put("pagos_realizados", 0)
            }
            val array = JSONArray().apply { put(ejemplo) }
            prefs.edit()
                .putBoolean("inicializado", true)
                .putString("lista_solicitudes", array.toString())
                .apply()
        }
    }

    private fun cargarYRenderizarSolicitudes() {
        val prefs = getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
        val listaJson = JSONArray(prefs.getString("lista_solicitudes", "[]"))
        val container = findViewById<LinearLayout>(R.id.containerSolicitudes)
        container.removeAllViews()

        var pendientes = 0
        var ultimaAceptada: JSONObject? = null

        for (i in 0 until listaJson.length()) {
            val sol = listaJson.getJSONObject(i)
            val estado = sol.optString("estado", "PENDIENTE")
            if (estado == "PENDIENTE") pendientes++
            if (estado == "ACEPTADA" && ultimaAceptada == null) {
                ultimaAceptada = sol
            }
            container.addView(crearTarjetaSolicitud(sol, i, listaJson))
        }

        findViewById<TextView>(R.id.tvContadorSolicitudes).text =
            "Solicitudes pendientes por revisar: $pendientes  •  Total: ${listaJson.length()}"

        if (ultimaAceptada != null) {
            mostrarDetalleCompletoAceptado(ultimaAceptada, hacerScroll = false)
        }
    }

    private fun crearTarjetaSolicitud(
        sol: JSONObject,
        indice: Int,
        listaCompleta: JSONArray
    ): LinearLayout {
        val folio = sol.optString("folio", "#SOL")
        val cliente = sol.optString("cliente", "Cliente")
        val monto = sol.optDouble("monto", 0.0)
        val frecuencia = sol.optString("frecuencia", "Mensual")
        val plazo = sol.optInt("plazo", 1)
        val fecha = sol.optString("fecha", "")
        val estado = sol.optString("estado", "PENDIENTE")
        val tasa = sol.optDouble(
            "tasa_interes",
            if (frecuencia == "Semanal" && plazo < 14) 17.0 else 15.0
        )

        val fondoCard = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 32f
            setColor(Color.WHITE)
            setStroke(
                3,
                when (estado) {
                    "ACEPTADA" -> Color.parseColor("#10B981")
                    "NEGADA" -> Color.parseColor("#EF4444")
                    else -> Color.parseColor("#CBD5E1")
                }
            )
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = fondoCard
            setPadding(36, 30, 36, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 22 }
        }

        val filaTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvNombre = TextView(this).apply {
            text = "$cliente ($folio)"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badgeColor = when (estado) {
            "ACEPTADA" -> "#10B981"
            "NEGADA" -> "#EF4444"
            else -> "#F59E0B"
        }
        val badgeFondo = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f
            setColor(Color.parseColor(badgeColor))
        }

        val tvBadge = TextView(this).apply {
            text = estado
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            background = badgeFondo
            setPadding(20, 8, 20, 8)
        }

        filaTop.addView(tvNombre)
        filaTop.addView(tvBadge)

        val tvInfo = TextView(this).apply {
            text = "Monto: ${formatoMoneda.format(monto)}  •  $frecuencia ($plazo pagos)  •  Interés: ${tasa.toInt()}%\nEnviada: $fecha"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setPadding(0, 10, 0, 14)
        }

        val filaBotones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnAceptar = Button(this).apply {
            text = if (estado == "ACEPTADA") "Ver Detalle" else "Aceptar"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#10B981"))
            layoutParams = LinearLayout.LayoutParams(0, 105, 1f).apply { rightMargin = 12 }
            setOnClickListener {
                sol.put("estado", "ACEPTADA")
                listaCompleta.put(indice, sol)
                guardarCambiosSolicitudes(listaCompleta)
                guardarEnSesionActiva(sol)
                cargarYRenderizarSolicitudes()
                mostrarDetalleCompletoAceptado(sol, hacerScroll = true)
                Toast.makeText(
                    this@ManagerActivity,
                    "Solicitud de $cliente ACEPTADA",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val btnNegar = Button(this).apply {
            text = "Negar"
            setTextColor(Color.WHITE)
            textSize = 13f
            isAllCaps = false
            setBackgroundColor(Color.parseColor("#EF4444"))
            layoutParams = LinearLayout.LayoutParams(0, 105, 1f).apply { leftMargin = 12 }
            setOnClickListener {
                sol.put("estado", "NEGADA")
                listaCompleta.put(indice, sol)
                guardarCambiosSolicitudes(listaCompleta)
                cargarYRenderizarSolicitudes()
                Toast.makeText(
                    this@ManagerActivity,
                    "Solicitud de $cliente NEGADA",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        filaBotones.addView(btnAceptar)
        filaBotones.addView(btnNegar)

        card.addView(filaTop)
        card.addView(tvInfo)
        card.addView(filaBotones)
        return card
    }

    private fun guardarCambiosSolicitudes(lista: JSONArray) {
        getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
            .edit()
            .putString("lista_solicitudes", lista.toString())
            .apply()
    }

    private fun guardarEnSesionActiva(sol: JSONObject) {
        getSharedPreferences("LoanSession", Context.MODE_PRIVATE)
            .edit()
            .putString("ultimo_cliente", sol.optString("cliente"))
            .putString("ultimo_monto", sol.optDouble("monto", 0.0).toString())
            .putString("ultimo_plazo", sol.optInt("plazo", 1).toString())
            .putString("ultima_frecuencia", sol.optString("frecuencia", "Mensual"))
            .apply()
    }

    private fun mostrarDetalleCompletoAceptado(sol: JSONObject, hacerScroll: Boolean) {
        val folio = sol.optString("folio", "#SOL")
        val cliente = sol.optString("cliente", "Cliente")
        val monto = sol.optDouble("monto", 0.0)
        val frecuencia = sol.optString("frecuencia", "Mensual")
        val plazo = sol.optInt("plazo", 1)
        val fecha = sol.optString("fecha", "")

        val tasaPorcentaje = sol.optDouble(
            "tasa_interes",
            if (frecuencia == "Semanal" && plazo < 14) 17.0 else 15.0
        )

        val interesGenerado = monto * (tasaPorcentaje / 100.0)
        val totalAPagar = monto + interesGenerado
        val cuotaPorPeriodo = if (plazo > 0) totalAPagar / plazo else 0.0
        val nuevaCarteraTotal = 150000.0 + monto

        clienteAceptadoActual = "$cliente ($folio)"
        montoAceptadoActual = monto
        tasaAceptadaActual = tasaPorcentaje

        val etiquetaPeriodo = when (frecuencia) {
            "Semanal" -> "semanales"
            "Quincenal" -> "quincenales"
            else -> "mensuales"
        }

        val etiquetaCuota = when (frecuencia) {
            "Semanal" -> "Pago Semanal"
            "Quincenal" -> "Pago Quincenal"
            else -> "Pago Mensual"
        }

        findViewById<TextView>(R.id.tvMgrCliente).text = "Cliente: $cliente ($folio)"
        val badge = findViewById<TextView>(R.id.tvMgrEstadoBadge)
        badge.text = "ACEPTADA"
        badge.setBackgroundColor(Color.parseColor("#10B981"))

        findViewById<TextView>(R.id.tvMgrFolioFecha).text = "Fecha de solicitud: $fecha"
        findViewById<TextView>(R.id.tvMgrFrecuencia).text = "Modalidad de Pago: $frecuencia"
        findViewById<TextView>(R.id.tvMgrPlazo).text = "Plazo Solicitado: $plazo pagos $etiquetaPeriodo"
        findViewById<TextView>(R.id.tvMgrMontoBase).text = "Capital Solicitado: ${formatoMoneda.format(monto)}"
        findViewById<TextView>(R.id.tvMgrInteres).text =
            "Interés Generado (${tasaPorcentaje.toInt()}%): ${formatoMoneda.format(interesGenerado)}"
        findViewById<TextView>(R.id.tvMgrTotalPago).text = "Total a Pagar: ${formatoMoneda.format(totalAPagar)}"
        findViewById<TextView>(R.id.tvMgrMensualidad).text = "$etiquetaCuota: ${formatoMoneda.format(cuotaPorPeriodo)}"
        findViewById<TextView>(R.id.tvMgrNuevoSaldo).text = "Nueva Cartera Total: ${formatoMoneda.format(nuevaCarteraTotal)}"

        if (hacerScroll) {
            val scroll = findViewById<ScrollView>(R.id.scrollManager)
            val titulo = findViewById<TextView>(R.id.tvTituloDetalleAceptado)
            scroll.post {
                scroll.smoothScrollTo(0, titulo.top)
            }
        }
    }
}

