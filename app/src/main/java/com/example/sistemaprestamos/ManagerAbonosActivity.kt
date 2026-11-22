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
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ManagerAbonosActivity : AppCompatActivity() {

    data class RegistroAbono(
        val numero: Int,
        val fecha: String,
        val montoAbonado: Double,
        val parteInteres: Double,
        val parteCapital: Double,
        val saldoRestante: Double
    )

    data class PrestamoCliente(
        val nombre: String,
        val capitalOriginal: Double,
        val tasaInteres: Double,
        var diasAtraso: Int,
        val abonos: MutableList<RegistroAbono>
    ) {
        override fun toString(): String = nombre
    }

    private val multaPorDiaAtraso = 50.0
    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))
    private val listaPrestamos = mutableListOf<PrestamoCliente>()
    private var prestamoActual: PrestamoCliente? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manager_abonos)

        cargarPrestamos()

        val spinner = findViewById<Spinner>(R.id.spinnerAbonosCliente)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listaPrestamos
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prestamoActual = listaPrestamos[position]
                actualizarResumenVisual()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val etMonto = findViewById<EditText>(R.id.etMontoAbono)
        val etAtraso = findViewById<EditText>(R.id.etDiasAtraso)

        findViewById<Button>(R.id.btnRegistrarAbono).setOnClickListener {
            val prestamo = prestamoActual ?: return@setOnClickListener
            val montoTexto = etMonto.text.toString().trim()
            val atrasoTexto = etAtraso.text.toString().trim()

            if (atrasoTexto.isNotEmpty()) {
                prestamo.diasAtraso = atrasoTexto.toIntOrNull() ?: 0
            }

            if (montoTexto.isEmpty()) {
                actualizarResumenVisual()
                Toast.makeText(this, "Atrasos y multas actualizados", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevoAbono = montoTexto.toDoubleOrNull() ?: 0.0
            if (nuevoAbono <= 0.0) {
                Toast.makeText(this, "Ingresa un monto mayor a 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalConInteres = prestamo.capitalOriginal * (1.0 + prestamo.tasaInteres / 100.0)
            val totalAbonadoPrevio = prestamo.abonos.sumOf { it.montoAbonado }
            val saldoActual = totalConInteres - totalAbonadoPrevio

            if (nuevoAbono > saldoActual) {
                Toast.makeText(
                    this,
                    "El abono supera el saldo restante (${formatoMoneda.format(saldoActual)})",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            agregarAbonoAPrestamo(prestamo, nuevoAbono)
            etMonto.text.clear()
            actualizarResumenVisual()
            Toast.makeText(this, "Abono aplicado correctamente", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnVolverAbonos).setOnClickListener {
            finish()
        }
    }

    private fun cargarPrestamos() {
        listaPrestamos.clear()
        val prefsSol = getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
        val listaJson = JSONArray(prefsSol.getString("lista_solicitudes", "[]"))

        for (i in 0 until listaJson.length()) {
            val obj = listaJson.getJSONObject(i)
            if (obj.optString("estado") == "ACEPTADA") {
                val cliente = obj.optString("cliente", "Cliente")
                val folio = obj.optString("folio", "#SOL-${100 + i}")
                val monto = obj.optDouble("monto", 0.0)
                val tasa = obj.optDouble("tasa_interes", 15.0)
                val abonado = obj.optDouble("abonado", 0.0)

                val p = PrestamoCliente("$cliente ($folio)", monto, tasa, 0, mutableListOf())
                if (abonado > 0.0) {
                    agregarAbonoAPrestamo(p, abonado)
                }
                listaPrestamos.add(p)
            }
        }

        val p1 = PrestamoCliente("Carlos Mendoza (#PR-001)", 10000.0, 15.0, 0, mutableListOf())
        agregarAbonoAPrestamo(p1, 2500.0, "05/11/2026")
        agregarAbonoAPrestamo(p1, 2000.0, "15/11/2026")
        listaPrestamos.add(p1)

        val p2 = PrestamoCliente("Laura Gómez (#PR-002)", 15000.0, 20.0, 4, mutableListOf())
        agregarAbonoAPrestamo(p2, 2875.0, "10/11/2026")
        listaPrestamos.add(p2)
    }

    private fun agregarAbonoAPrestamo(
        prestamo: PrestamoCliente,
        monto: Double,
        fechaCustom: String? = null
    ) {
        val totalConInteres = prestamo.capitalOriginal * (1.0 + prestamo.tasaInteres / 100.0)
        val abonadoAntes = prestamo.abonos.sumOf { it.montoAbonado }
        val saldoNuevo = (totalConInteres - (abonadoAntes + monto)).coerceAtLeast(0.0)

        val propInteres = if ((100.0 + prestamo.tasaInteres) > 0) {
            prestamo.tasaInteres / (100.0 + prestamo.tasaInteres)
        } else 0.0
        val parteInteres = monto * propInteres
        val parteCapital = monto - parteInteres

        val fecha = fechaCustom ?: SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        prestamo.abonos.add(
            RegistroAbono(
                numero = prestamo.abonos.size + 1,
                fecha = fecha,
                montoAbonado = monto,
                parteInteres = parteInteres,
                parteCapital = parteCapital,
                saldoRestante = saldoNuevo
            )
        )
    }

    private fun actualizarResumenVisual() {
        val prestamo = prestamoActual ?: return

        val interesTotal = prestamo.capitalOriginal * (prestamo.tasaInteres / 100.0)
        val totalConInteres = prestamo.capitalOriginal + interesTotal
        val totalAbonado = prestamo.abonos.sumOf { it.montoAbonado }
        val saldoDespuesAbonos = (totalConInteres - totalAbonado).coerceAtLeast(0.0)
        val multa = if (prestamo.diasAtraso > 0) prestamo.diasAtraso * multaPorDiaAtraso else 0.0
        val totalFinalConMultas = saldoDespuesAbonos + multa

        val porcentaje = if (totalConInteres > 0) {
            ((totalAbonado / totalConInteres) * 100).toInt().coerceIn(0, 100)
        } else 0

        findViewById<TextView>(R.id.tvClienteNombre).text = "Cliente: ${prestamo.nombre}"
        findViewById<TextView>(R.id.tvCapitalOriginal).text = formatoMoneda.format(prestamo.capitalOriginal)
        findViewById<TextView>(R.id.tvInteresGenerado).text =
            "${formatoMoneda.format(interesTotal)} (${prestamo.tasaInteres.toInt()}%)"
        findViewById<TextView>(R.id.tvTotalConInteres).text = formatoMoneda.format(totalConInteres)
        findViewById<TextView>(R.id.tvTotalAbonado).text = formatoMoneda.format(totalAbonado)
        findViewById<TextView>(R.id.tvSaldoDespuesAbonos).text = formatoMoneda.format(saldoDespuesAbonos)

        findViewById<TextView>(R.id.tvPorcentajePagado).text = "$porcentaje%"
        findViewById<ProgressBar>(R.id.progressBarPago).progress = porcentaje

        val tvBadge = findViewById<TextView>(R.id.tvBadgeEstado)
        val tvDiasAtraso = findViewById<TextView>(R.id.tvDiasAtraso)
        val tvMulta = findViewById<TextView>(R.id.tvMultaAplicada)
        val tvTotalFinal = findViewById<TextView>(R.id.tvTotalFinalConMultas)

        if (prestamo.diasAtraso > 0) {
            tvBadge.text = "CON ATRASO"
            tvBadge.setBackgroundColor(Color.parseColor("#EF4444"))
            tvDiasAtraso.text = "${prestamo.diasAtraso} días"
            tvDiasAtraso.setTextColor(Color.parseColor("#EF4444"))
            tvMulta.text = "${formatoMoneda.format(multa)} ($${multaPorDiaAtraso.toInt()}/día)"
            tvMulta.setTextColor(Color.parseColor("#EF4444"))
        } else {
            tvBadge.text = if (saldoDespuesAbonos == 0.0) "LIQUIDADO" else "AL CORRIENTE"
            tvBadge.setBackgroundColor(Color.parseColor("#10B981"))
            tvDiasAtraso.text = "0 días"
            tvDiasAtraso.setTextColor(Color.parseColor("#0F172A"))
            tvMulta.text = "$0.00 (No aplica)"
            tvMulta.setTextColor(Color.parseColor("#10B981"))
        }

        tvTotalFinal.text = formatoMoneda.format(totalFinalConMultas)
        renderizarHistorial(prestamo)
    }

    private fun renderizarHistorial(prestamo: PrestamoCliente) {
        val container = findViewById<LinearLayout>(R.id.containerHistorialAbonos)
        val tvSinAbonos = findViewById<TextView>(R.id.tvSinAbonos)
        container.removeAllViews()

        if (prestamo.abonos.isEmpty()) {
            tvSinAbonos.visibility = View.VISIBLE
            return
        }
        tvSinAbonos.visibility = View.GONE

        prestamo.abonos.asReversed().forEach { abono ->
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 16, 0, 16)
            }

            val encabezado = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            val tvTitulo = TextView(this).apply {
                text = "Abono #${abono.numero}  •  ${abono.fecha}"
                setTextColor(Color.parseColor("#0F172A"))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvMonto = TextView(this).apply {
                text = "+ ${formatoMoneda.format(abono.montoAbonado)}"
                setTextColor(Color.parseColor("#10B981"))
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
            }

            encabezado.addView(tvTitulo)
            encabezado.addView(tvMonto)

            val tvDetalle = TextView(this).apply {
                text = "Interés: ${formatoMoneda.format(abono.parteInteres)}  |  Capital: ${formatoMoneda.format(abono.parteCapital)}"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 12f
                setPadding(0, 6, 0, 2)
            }

            val tvSaldoItem = TextView(this).apply {
                text = "Total después de este abono: ${formatoMoneda.format(abono.saldoRestante)}"
                setTextColor(Color.parseColor("#334155"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
            }

            val divisor = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                ).apply { topMargin = 16 }
                setBackgroundColor(Color.parseColor("#E2E8F0"))
            }

            fila.addView(encabezado)
            fila.addView(tvDetalle)
            fila.addView(tvSaldoItem)
            fila.addView(divisor)
            container.addView(fila)
        }
    }
}