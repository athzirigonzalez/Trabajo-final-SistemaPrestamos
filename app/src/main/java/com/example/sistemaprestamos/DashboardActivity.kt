package com.example.sistemaprestamos

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.text.NumberFormat
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    data class ClientePrestamo(
        val folio: String,
        val nombre: String,
        val montoAutorizado: Double,
        val frecuencia: String,
        val plazo: Int,
        val tasaInteres: Double,
        val totalAbonado: Double,
        val diasAtraso: Int,
        val proximoPago: String,
        val estadoSolicitud: String
    )

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    private val multaPorDia = 50.0

    private var todosLosClientes = listOf<ClientePrestamo>()
    private var folioSeleccionado: String = ""
    private var esAdmin = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val sessionPrefs = getSharedPreferences("LoanSession", Context.MODE_PRIVATE)
        val rol = sessionPrefs.getString("rol_activo", "ADMIN") ?: "ADMIN"
        val usuarioActivo = sessionPrefs.getString("usuario_activo", "") ?: ""
        esAdmin = (rol == "ADMIN")

        val etBuscar = findViewById<EditText>(R.id.etBuscarCliente)
        val tvTotalCount = findViewById<TextView>(R.id.tvTotalClientesCount)

        if (!esAdmin) {
            etBuscar.visibility = View.GONE
            (tvTotalCount.parent?.parent?.parent as? View)?.visibility = View.GONE
        }

        todosLosClientes = construirCarteraClientes(esAdmin, usuarioActivo)
        if (todosLosClientes.isNotEmpty()) {
            folioSeleccionado = todosLosClientes[0].folio
            mostrarDatosCliente(todosLosClientes[0])
        }

        actualizarContadoresCartera(todosLosClientes)
        renderizarCartera(todosLosClientes, scrollAlSeleccionar = false)

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtro = s.toString().trim().lowercase(Locale.getDefault())
                val filtrados = if (filtro.isEmpty()) {
                    todosLosClientes
                } else {
                    todosLosClientes.filter {
                        it.nombre.lowercase(Locale.getDefault()).contains(filtro) ||
                                it.folio.lowercase(Locale.getDefault()).contains(filtro)
                    }
                }
                renderizarCartera(filtrados, scrollAlSeleccionar = false)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<Button>(R.id.btnDashVolver).setOnClickListener {
            finish()
        }
    }

    private fun construirCarteraClientes(esAdmin: Boolean, usuarioActivo: String): List<ClientePrestamo> {
        val lista = mutableListOf<ClientePrestamo>()
        val prefsSol = getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
        val listaJson = JSONArray(prefsSol.getString("lista_solicitudes", "[]"))

        for (i in 0 until listaJson.length()) {
            val obj = listaJson.getJSONObject(i)
            val usuarioCuenta = obj.optString("usuario_cuenta", "")
            val clienteNombre = obj.optString("cliente", "Cliente")
            val estadoSol = obj.optString("estado", "PENDIENTE")

            val perteneceAlCliente = !esAdmin && (
                    usuarioCuenta.equals(usuarioActivo, ignoreCase = true) ||
                            clienteNombre.equals(usuarioActivo, ignoreCase = true)
                    )

            if ((esAdmin && estadoSol == "ACEPTADA") || perteneceAlCliente) {
                val frecuencia = obj.optString("frecuencia", "Mensual")
                val plazo = obj.optInt("plazo", 1)
                val tasaPorc = obj.optDouble(
                    "tasa_interes",
                    if (frecuencia == "Semanal" && plazo < 14) 17.0 else 15.0
                )

                lista.add(
                    ClientePrestamo(
                        folio = obj.optString("folio", "#SOL-${100 + i}"),
                        nombre = clienteNombre,
                        montoAutorizado = obj.optDouble("monto", 0.0),
                        frecuencia = frecuencia,
                        plazo = plazo,
                        tasaInteres = tasaPorc / 100.0,
                        totalAbonado = obj.optDouble("abonado", 0.0),
                        diasAtraso = 0,
                        proximoPago = if (estadoSol == "ACEPTADA") "Próximo periodo" else "En revisión",
                        estadoSolicitud = estadoSol
                    )
                )
            }
        }

        if (esAdmin) {
            lista.add(
                ClientePrestamo("#PR-001", "Carlos Mendoza", 10000.0, "Semanal", 10, 0.17, 4500.0, 0, "05/12/2026", "ACEPTADA")
            )
            lista.add(
                ClientePrestamo("#PR-002", "Laura Gómez", 15000.0, "Semanal", 14, 0.15, 2875.0, 4, "Vencido", "ACEPTADA")
            )
            lista.add(
                ClientePrestamo("#PR-003", "Roberto Silva", 8000.0, "Mensual", 4, 0.15, 9200.0, 0, "Completado", "ACEPTADA")
            )
        }

        return lista
    }

    private fun actualizarContadoresCartera(clientes: List<ClientePrestamo>) {
        var alCorriente = 0
        var conAdeudo = 0

        clientes.forEach { c ->
            val totalConInteres = c.montoAutorizado * (1.0 + c.tasaInteres)
            val saldo = (totalConInteres - c.totalAbonado).coerceAtLeast(0.0)
            if (saldo > 0.0 && c.diasAtraso > 0) conAdeudo++ else alCorriente++
        }

        findViewById<TextView>(R.id.tvTotalClientesCount).text = clientes.size.toString()
        findViewById<TextView>(R.id.tvAlCorrienteCount).text = alCorriente.toString()
        findViewById<TextView>(R.id.tvConAdeudoCount).text = conAdeudo.toString()
    }

    private fun renderizarCartera(clientes: List<ClientePrestamo>, scrollAlSeleccionar: Boolean) {
        val container = findViewById<LinearLayout>(R.id.containerCarteraClientes)
        container.removeAllViews()

        if (clientes.isEmpty()) {
            val tvVacio = TextView(this).apply {
                text = if (esAdmin) {
                    "No se encontraron clientes con ese nombre o folio."
                } else {
                    "Aún no tienes préstamos registrados. Ve a 'Solicitar Préstamo' para crear uno."
                }
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                setPadding(16, 24, 16, 24)
            }
            container.addView(tvVacio)
            return
        }

        clientes.forEach { cliente ->
            val totalConInteres = cliente.montoAutorizado * (1.0 + cliente.tasaInteres)
            val saldoRestante = (totalConInteres - cliente.totalAbonado).coerceAtLeast(0.0)
            val seleccionado = cliente.folio == folioSeleccionado

            val (textoEstado, colorEstado) = when {
                cliente.estadoSolicitud == "PENDIENTE" -> "PENDIENTE" to "#F59E0B"
                cliente.estadoSolicitud == "NEGADA" -> "NEGADA" to "#EF4444"
                saldoRestante == 0.0 -> "LIQUIDADO" to "#2563EB"
                cliente.diasAtraso > 0 -> "ADEUDO (${cliente.diasAtraso}d)" to "#EF4444"
                else -> "AL CORRIENTE" to "#10B981"
            }

            val fondoTarjeta = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 32f
                setColor(if (seleccionado) Color.parseColor("#EFF6FF") else Color.WHITE)
                setStroke(
                    if (seleccionado) 4 else 2,
                    if (seleccionado) Color.parseColor("#2563EB") else Color.parseColor("#E2E8F0")
                )
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = fondoTarjeta
                setPadding(36, 30, 36, 30)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 20 }
                setOnClickListener {
                    folioSeleccionado = cliente.folio
                    mostrarDatosCliente(cliente)
                    renderizarCartera(clientes, scrollAlSeleccionar = true)
                }
            }

            val colIzq = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvNombre = TextView(this).apply {
                text = cliente.nombre
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
            }

            val tvSub = TextView(this).apply {
                text = "${cliente.folio}  •  ${cliente.frecuencia} (${(cliente.tasaInteres * 100).toInt()}% int.)"
                setTextColor(Color.parseColor("#475569"))
                textSize = 13f
                setPadding(0, 6, 0, 0)
            }

            colIzq.addView(tvNombre)
            colIzq.addView(tvSub)

            val fondoBadge = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor(colorEstado))
            }

            val tvBadge = TextView(this).apply {
                text = textoEstado
                setTextColor(Color.WHITE)
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                background = fondoBadge
                setPadding(22, 10, 22, 10)
            }

            card.addView(colIzq)
            card.addView(tvBadge)
            container.addView(card)
        }

        if (scrollAlSeleccionar) {
            val scroll = findViewById<ScrollView>(R.id.scrollDashboard)
            val seccionDetalle = findViewById<TextView>(R.id.tvSeccionDetalle)
            scroll.post { scroll.smoothScrollTo(0, seccionDetalle.top) }
        }
    }

    private fun mostrarDatosCliente(cliente: ClientePrestamo) {
        val totalConInteres = cliente.montoAutorizado * (1.0 + cliente.tasaInteres)
        val cuota = if (cliente.plazo > 0) totalConInteres / cliente.plazo else 0.0
        val saldoRestante = (totalConInteres - cliente.totalAbonado).coerceAtLeast(0.0)
        val multa = if (cliente.diasAtraso > 0) cliente.diasAtraso * multaPorDia else 0.0
        val adeudoExigible = if (cliente.diasAtraso > 0) cuota + multa else 0.0

        val porcentaje = if (totalConInteres > 0) {
            ((cliente.totalAbonado / totalConInteres) * 100).toInt().coerceIn(0, 100)
        } else 0

        findViewById<TextView>(R.id.tvDashClienteNombre).text = cliente.nombre
        findViewById<TextView>(R.id.tvDashClienteFolio).text =
            "Folio: ${cliente.folio}  •  Interés aplicado: ${(cliente.tasaInteres * 100).toInt()}%"
        findViewById<TextView>(R.id.tvDashSaldoRestante).text = formatoMoneda.format(saldoRestante)
        findViewById<TextView>(R.id.tvDashPorcentaje).text = "$porcentaje%"
        findViewById<ProgressBar>(R.id.progressDashPago).progress = porcentaje

        findViewById<TextView>(R.id.tvDashMontoAutorizado).text =
            formatoMoneda.format(cliente.montoAutorizado)
        findViewById<TextView>(R.id.tvDashPlazo).text = "${cliente.plazo} pagos (${cliente.frecuencia})"
        findViewById<TextView>(R.id.tvDashTotalConInteres).text =
            formatoMoneda.format(totalConInteres)
        findViewById<TextView>(R.id.tvDashTotalAbonado).text =
            formatoMoneda.format(cliente.totalAbonado)
        findViewById<TextView>(R.id.tvDashMensualidad).text = formatoMoneda.format(cuota)
        findViewById<TextView>(R.id.tvDashProximoPago).text = cliente.proximoPago

        val tvBadge = findViewById<TextView>(R.id.tvDashEstadoBadge)
        val tvDetalle = findViewById<TextView>(R.id.tvDashEstadoDetalle)
        val tvDiasAtraso = findViewById<TextView>(R.id.tvDashDiasAtraso)
        val tvMulta = findViewById<TextView>(R.id.tvDashMulta)
        val tvAdeudoVencido = findViewById<TextView>(R.id.tvDashAdeudoVencido)

        when {
            cliente.estadoSolicitud == "PENDIENTE" -> {
                tvBadge.text = "PENDIENTE"
                tvBadge.setBackgroundColor(Color.parseColor("#F59E0B"))
                tvDetalle.text = "Solicitud enviada. En espera de aprobación por el Administrador."
                tvDetalle.setTextColor(Color.parseColor("#D97706"))
                tvDiasAtraso.text = "0 días"
                tvMulta.text = "$0.00"
                tvAdeudoVencido.text = "$0.00"
            }
            cliente.estadoSolicitud == "NEGADA" -> {
                tvBadge.text = "NEGADA"
                tvBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                tvDetalle.text = "Esta solicitud fue rechazada por Gerencia."
                tvDetalle.setTextColor(Color.parseColor("#EF4444"))
                tvDiasAtraso.text = "0 días"
                tvMulta.text = "$0.00"
                tvAdeudoVencido.text = "$0.00"
            }
            saldoRestante == 0.0 -> {
                tvBadge.text = "LIQUIDADO"
                tvBadge.setBackgroundColor(Color.parseColor("#2563EB"))
                tvDetalle.text = "Préstamo pagado en su totalidad. Sin adeudos."
                tvDetalle.setTextColor(Color.parseColor("#2563EB"))
                tvDiasAtraso.text = "0 días"
                tvMulta.text = "$0.00"
                tvAdeudoVencido.text = "$0.00"
            }
            cliente.diasAtraso > 0 -> {
                tvBadge.text = "CON ADEUDO"
                tvBadge.setBackgroundColor(Color.parseColor("#EF4444"))
                tvDetalle.text = "Presenta ${cliente.diasAtraso} días de atraso. Multa: $${multaPorDia.toInt()}/día."
                tvDetalle.setTextColor(Color.parseColor("#EF4444"))
                tvDiasAtraso.text = "${cliente.diasAtraso} días"
                tvMulta.text = formatoMoneda.format(multa)
                tvAdeudoVencido.text = formatoMoneda.format(adeudoExigible)
            }
            else -> {
                tvBadge.text = "AL CORRIENTE"
                tvBadge.setBackgroundColor(Color.parseColor("#10B981"))
                tvDetalle.text = "Préstamo aprobado. Pagos al corriente."
                tvDetalle.setTextColor(Color.parseColor("#10B981"))
                tvDiasAtraso.text = "0 días"
                tvMulta.text = "$0.00"
                tvAdeudoVencido.text = "$0.00"
            }
        }
    }
}