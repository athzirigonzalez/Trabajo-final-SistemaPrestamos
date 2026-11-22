package com.example.sistemaprestamos

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RegistrarAbonoActivity : AppCompatActivity() {

    data class CuentaPrestamo(
        val indiceJson: Int,
        val folio: String,
        val cliente: String,
        val montoCapital: Double,
        val frecuencia: String,
        val plazoTotal: Int,
        val tasaInteres: Double,
        var totalAbonado: Double,
        var pagosRealizados: Int
    ) {
        val totalConInteres: Double
            get() = montoCapital * (1.0 + tasaInteres / 100.0)

        val saldoRestante: Double
            get() = (totalConInteres - totalAbonado).coerceAtLeast(0.0)

        val cuotaSugerida: Double
            get() = if (plazoTotal > 0) totalConInteres / plazoTotal else totalConInteres

        override fun toString(): String {
            val formato = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
            return "$cliente ($folio) • Restante: ${formato.format(saldoRestante)}"
        }
    }

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    private val listaCuentas = mutableListOf<CuentaPrestamo>()
    private var cuentaSeleccionada: CuentaPrestamo? = null
    private var esAdmin = true
    private var usuarioActivo = ""

    private val cuentasDestino = listOf(
        "BBVA Empresarial •••• 4821",
        "Santander Cobranza •••• 9014",
        "Banorte Concentradora •••• 3310",
        "Efectivo en Caja / Sucursal",
        "Transferencia SPEI Directa"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrar_abono)

        val sessionPrefs = getSharedPreferences("LoanSession", Context.MODE_PRIVATE)
        val rol = sessionPrefs.getString("rol_activo", "ADMIN") ?: "ADMIN"
        usuarioActivo = sessionPrefs.getString("usuario_activo", "") ?: ""
        esAdmin = (rol == "ADMIN")

        cargarCuentasActivas()

        val spCliente = findViewById<Spinner>(R.id.spCuentaCliente)
        val spDestino = findViewById<Spinner>(R.id.spCuentaDestino)
        val etCantidad = findViewById<EditText>(R.id.etCantidadAbonar)
        val btnRealizarAbono = findViewById<Button>(R.id.btnRealizarAbono)
        val tvInfo = findViewById<TextView>(R.id.tvInfoCuotaSugerida)

        val adapterClientes = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listaCuentas
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spCliente.adapter = adapterClientes

        val adapterDestino = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            cuentasDestino
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spDestino.adapter = adapterDestino

        if (listaCuentas.isEmpty()) {
            tvInfo.text = "No tienes préstamos aprobados activos disponibles para abonar."
            btnRealizarAbono.isEnabled = false
            btnRealizarAbono.alpha = 0.5f
        } else {
            spCliente.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    cuentaSeleccionada = listaCuentas[position]
                    actualizarInfoSugerida()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        btnRealizarAbono.setOnClickListener {
            val cuenta = cuentaSeleccionada ?: return@setOnClickListener
            val montoTexto = etCantidad.text.toString().trim()

            if (montoTexto.isEmpty()) {
                Toast.makeText(this, "Ingresa la cantidad a abonar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cantidad = montoTexto.toDoubleOrNull() ?: 0.0
            if (cantidad <= 0.0) {
                Toast.makeText(this, "La cantidad debe ser mayor a $0.00", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (cuenta.saldoRestante <= 0.0) {
                Toast.makeText(this, "Este préstamo ya está liquidado", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (cantidad > cuenta.saldoRestante) {
                Toast.makeText(
                    this,
                    "El abono supera el saldo restante (${formatoMoneda.format(cuenta.saldoRestante)})",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            cuenta.totalAbonado += cantidad
            cuenta.pagosRealizados += 1

            val cuentaDestinoElegida = spDestino.selectedItem.toString()
            val fechaHora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val folioRecibo = "#REC-${(1000..9999).random()}"
            val etiquetaPeriodo = construirEtiquetaPeriodo(cuenta)

            guardarActualizacionEnDB(cuenta)
            guardarReciboHistorial(
                folioRecibo = folioRecibo,
                usuarioCuenta = usuarioActivo,
                cliente = "${cuenta.cliente} (${cuenta.folio})",
                cuentaDestino = cuentaDestinoElegida,
                periodo = etiquetaPeriodo,
                cantidad = cantidad,
                fecha = fechaHora,
                restante = cuenta.saldoRestante
            )

            mostrarReciboEnPantalla(
                folioRecibo = folioRecibo,
                cliente = "${cuenta.cliente} (${cuenta.folio})",
                cuentaDestino = cuentaDestinoElegida,
                periodo = etiquetaPeriodo,
                cantidad = cantidad,
                fecha = fechaHora,
                restante = cuenta.saldoRestante
            )

            etCantidad.text.clear()
            adapterClientes.notifyDataSetChanged()
            actualizarInfoSugerida()
            renderizarHistorialRecibos()

            Toast.makeText(this, "Abono registrado • $etiquetaPeriodo", Toast.LENGTH_SHORT).show()
        }

        renderizarHistorialRecibos()

        findViewById<Button>(R.id.btnVolverRegistrarAbono).setOnClickListener {
            finish()
        }
    }

    private fun construirEtiquetaPeriodo(cuenta: CuentaPrestamo): String {
        val nombreUnidad = when (cuenta.frecuencia) {
            "Semanal" -> "Semana"
            "Quincenal" -> "Quincena"
            else -> "Mensualidad"
        }
        return if (cuenta.saldoRestante == 0.0) {
            "$nombreUnidad ${cuenta.pagosRealizados} de ${cuenta.plazoTotal} (Liquidado)"
        } else {
            "$nombreUnidad ${cuenta.pagosRealizados} de ${cuenta.plazoTotal}"
        }
    }

    private fun actualizarInfoSugerida() {
        val cuenta = cuentaSeleccionada ?: return
        val tvInfo = findViewById<TextView>(R.id.tvInfoCuotaSugerida)

        val nombreUnidad = when (cuenta.frecuencia) {
            "Semanal" -> "Semana"
            "Quincenal" -> "Quincena"
            else -> "Mensualidad"
        }

        if (cuenta.saldoRestante <= 0.0) {
            tvInfo.text = "Préstamo LIQUIDADO en ${cuenta.pagosRealizados} pagos (${cuenta.frecuencia})."
        } else {
            val siguientePago = cuenta.pagosRealizados + 1
            tvInfo.text =
                "Modalidad: ${cuenta.frecuencia} (${cuenta.tasaInteres.toInt()}% int.)  •  Corresponde: $nombreUnidad $siguientePago de ${cuenta.plazoTotal}\n" +
                        "Cuota sugerida: ${formatoMoneda.format(cuenta.cuotaSugerida)}  |  Restante: ${formatoMoneda.format(cuenta.saldoRestante)}"
        }
    }

    private fun cargarCuentasActivas() {
        listaCuentas.clear()
        val prefs = getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
        val listaJson = JSONArray(prefs.getString("lista_solicitudes", "[]"))

        for (i in 0 until listaJson.length()) {
            val obj = listaJson.getJSONObject(i)
            if (obj.optString("estado") == "ACEPTADA") {
                val usuarioCuenta = obj.optString("usuario_cuenta", "")
                val clienteNombre = obj.optString("cliente", "Cliente")

                val correspondeAlUsuario = esAdmin ||
                        usuarioCuenta.equals(usuarioActivo, ignoreCase = true) ||
                        clienteNombre.equals(usuarioActivo, ignoreCase = true)

                if (correspondeAlUsuario) {
                    val freq = obj.optString("frecuencia", "Mensual")
                    val plazo = obj.optInt("plazo", 1)
                    val tasa = obj.optDouble(
                        "tasa_interes",
                        if (freq == "Semanal" && plazo < 14) 17.0 else 15.0
                    )

                    listaCuentas.add(
                        CuentaPrestamo(
                            indiceJson = i,
                            folio = obj.optString("folio", "#SOL-${100 + i}"),
                            cliente = clienteNombre,
                            montoCapital = obj.optDouble("monto", 0.0),
                            frecuencia = freq,
                            plazoTotal = plazo,
                            tasaInteres = tasa,
                            totalAbonado = obj.optDouble("abonado", 0.0),
                            pagosRealizados = obj.optInt("pagos_realizados", 0)
                        )
                    )
                }
            }
        }

        if (esAdmin && listaCuentas.isEmpty()) {
            listaCuentas.add(
                CuentaPrestamo(-1, "#PR-001", "Carlos Mendoza", 8000.0, "Semanal", 8, 17.0, 2340.0, 2)
            )
            listaCuentas.add(
                CuentaPrestamo(-1, "#PR-002", "Laura Gómez", 12000.0, "Semanal", 14, 15.0, 4600.0, 2)
            )
            listaCuentas.add(
                CuentaPrestamo(-1, "#PR-003", "Roberto Silva", 15000.0, "Mensual", 12, 15.0, 1437.50, 1)
            )
        }
    }

    private fun guardarActualizacionEnDB(cuenta: CuentaPrestamo) {
        if (cuenta.indiceJson < 0) return
        val prefs = getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
        val listaJson = JSONArray(prefs.getString("lista_solicitudes", "[]"))
        if (cuenta.indiceJson < listaJson.length()) {
            val obj = listaJson.getJSONObject(cuenta.indiceJson)
            obj.put("abonado", cuenta.totalAbonado)
            obj.put("pagos_realizados", cuenta.pagosRealizados)
            listaJson.put(cuenta.indiceJson, obj)
            prefs.edit().putString("lista_solicitudes", listaJson.toString()).apply()
        }
    }

    private fun mostrarReciboEnPantalla(
        folioRecibo: String,
        cliente: String,
        cuentaDestino: String,
        periodo: String,
        cantidad: Double,
        fecha: String,
        restante: Double
    ) {
        val cardRecibo = findViewById<View>(R.id.cardReciboDigital)
        cardRecibo.visibility = View.VISIBLE

        findViewById<TextView>(R.id.tvReciboFolio).text = "Folio: $folioRecibo"
        findViewById<TextView>(R.id.tvReciboCantidad).text = formatoMoneda.format(cantidad)
        findViewById<TextView>(R.id.tvReciboPeriodo).text = "Pago registrado: $periodo"
        findViewById<TextView>(R.id.tvReciboCliente).text = "Cliente: $cliente"
        findViewById<TextView>(R.id.tvReciboCuentaDestino).text = "Cuenta Abonada: $cuentaDestino"
        findViewById<TextView>(R.id.tvReciboFecha).text = "Fecha y Hora: $fecha"
        findViewById<TextView>(R.id.tvReciboRestante).text = formatoMoneda.format(restante)

        val scroll = findViewById<ScrollView>(R.id.scrollRegistrarAbono)
        scroll.post {
            scroll.smoothScrollTo(0, cardRecibo.top)
        }
    }

    private fun guardarReciboHistorial(
        folioRecibo: String,
        usuarioCuenta: String,
        cliente: String,
        cuentaDestino: String,
        periodo: String,
        cantidad: Double,
        fecha: String,
        restante: Double
    ) {
        val prefs = getSharedPreferences("RecibosDB", Context.MODE_PRIVATE)
        val actuales = JSONArray(prefs.getString("lista_recibos", "[]"))

        val nuevo = JSONObject().apply {
            put("folio", folioRecibo)
            put("usuario_cuenta", usuarioCuenta)
            put("cliente", cliente)
            put("cuenta", cuentaDestino)
            put("periodo", periodo)
            put("cantidad", cantidad)
            put("fecha", fecha)
            put("restante", restante)
        }

        val actualizada = JSONArray().apply { put(nuevo) }
        for (i in 0 until actuales.length()) {
            actualizada.put(actuales.getJSONObject(i))
        }

        prefs.edit().putString("lista_recibos", actualizada.toString()).apply()
    }

    private fun renderizarHistorialRecibos() {
        val prefs = getSharedPreferences("RecibosDB", Context.MODE_PRIVATE)
        val recibos = JSONArray(prefs.getString("lista_recibos", "[]"))
        val container = findViewById<LinearLayout>(R.id.containerHistorialRecibos)
        val tvSinRecibos = findViewById<TextView>(R.id.tvSinRecibos)

        container.removeAllViews()
        var visibles = 0

        for (i in 0 until recibos.length()) {
            val r = recibos.getJSONObject(i)
            val usuarioRecibo = r.optString("usuario_cuenta", "")
            val clienteRecibo = r.optString("cliente", "")

            val mostrar = esAdmin ||
                    usuarioRecibo.equals(usuarioActivo, ignoreCase = true) ||
                    clienteRecibo.contains(usuarioActivo, ignoreCase = true)

            if (!mostrar) continue
            visibles++

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val fila1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val tvTitulo = TextView(this).apply {
                text = "${r.optString("folio")} • $clienteRecibo"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvMonto = TextView(this).apply {
                text = "+ ${formatoMoneda.format(r.optDouble("cantidad"))}"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }

            fila1.addView(tvTitulo)
            fila1.addView(tvMonto)

            val tvDetalle = TextView(this).apply {
                text = "${r.optString("periodo")}  •  ${r.optString("fecha")}\n" +
                        "Cuenta: ${r.optString("cuenta")}  |  Restante: ${formatoMoneda.format(r.optDouble("restante"))}"
                setTextColor(Color.parseColor("#475569"))
                textSize = 12f
                setPadding(0, 4, 0, 0)
            }

            val linea = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                ).apply { topMargin = 14 }
                setBackgroundColor(Color.parseColor("#E2E8F0"))
            }

            item.addView(fila1)
            item.addView(tvDetalle)
            item.addView(linea)
            container.addView(item)
        }

        tvSinRecibos.visibility = if (visibles == 0) View.VISIBLE else View.GONE
    }
}