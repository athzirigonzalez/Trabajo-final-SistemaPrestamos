package com.example.sistemaprestamos

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoanFormActivity : AppCompatActivity() {

    private val formatoMoneda = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loan_form)

        val sessionPrefs = getSharedPreferences("LoanSession", Context.MODE_PRIVATE)
        val usuarioActivo = sessionPrefs.getString("usuario_activo", "") ?: ""

        val etClientName = findViewById<TextInputEditText>(R.id.etClientName)
        val etAmount = findViewById<TextInputEditText>(R.id.etAmount)
        val rgFrecuencia = findViewById<RadioGroup>(R.id.rgFrecuencia)
        val etTermMonths = findViewById<TextInputEditText>(R.id.etTermMonths)
        val tvResumenInteres = findViewById<TextView>(R.id.tvResumenInteresForm)
        val btnSubmitLoan = findViewById<Button>(R.id.btnSubmitLoan)

        if (usuarioActivo.isNotEmpty()) {
            etClientName.setText(usuarioActivo)
        }

        fun actualizarVistaInteres() {
            val monto = etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
            val plazo = etTermMonths.text.toString().trim().toIntOrNull() ?: 0
            val frecuencia = obtenerFrecuenciaSeleccionada(rgFrecuencia)
            val tasa = calcularTasaInteres(frecuencia, plazo)
            val total = monto * (1.0 + tasa / 100.0)

            if (frecuencia == "Semanal" && plazo in 1..13) {
                tvResumenInteres.setBackgroundColor(Color.parseColor("#FEF3C7"))
                tvResumenInteres.setTextColor(Color.parseColor("#92400E"))
                tvResumenInteres.text =
                    "Interés aplicado: 17% (15% base + 2% por ser menor a 14 semanas)" +
                            if (monto > 0) "\nTotal estimado a pagar: ${formatoMoneda.format(total)}" else ""
            } else {
                tvResumenInteres.setBackgroundColor(Color.parseColor("#EFF6FF"))
                tvResumenInteres.setTextColor(Color.parseColor("#1E3A8A"))
                tvResumenInteres.text =
                    "Interés aplicado: 15% estándar" +
                            if (monto > 0) "\nTotal estimado a pagar: ${formatoMoneda.format(total)}" else ""
            }
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actualizarVistaInteres()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        etAmount.addTextChangedListener(watcher)
        etTermMonths.addTextChangedListener(watcher)
        rgFrecuencia.setOnCheckedChangeListener { _, _ -> actualizarVistaInteres() }
        actualizarVistaInteres()

        btnSubmitLoan.setOnClickListener {
            val cliente = etClientName.text.toString().trim()
            val montoTexto = etAmount.text.toString().trim()
            val plazoTexto = etTermMonths.text.toString().trim()

            if (cliente.isEmpty() || montoTexto.isEmpty() || plazoTexto.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos de la solicitud", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val monto = montoTexto.toDoubleOrNull() ?: 0.0
            val plazo = plazoTexto.toIntOrNull() ?: 0

            if (monto <= 0.0 || plazo <= 0) {
                Toast.makeText(this, "Ingresa un monto y plazo válidos mayores a 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val frecuencia = obtenerFrecuenciaSeleccionada(rgFrecuencia)
            val tasaInteres = calcularTasaInteres(frecuencia, plazo)

            guardarSolicitudParaManager(
                usuarioCuenta = usuarioActivo.ifEmpty { cliente },
                cliente = cliente,
                monto = monto,
                frecuencia = frecuencia,
                plazo = plazo,
                tasaInteres = tasaInteres
            )

            Toast.makeText(
                this,
                "Solicitud enviada ($frecuencia • $plazo pagos • Interés ${tasaInteres.toInt()}%)",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun obtenerFrecuenciaSeleccionada(rg: RadioGroup): String {
        return when (rg.checkedRadioButtonId) {
            R.id.rbSemanal -> "Semanal"
            R.id.rbQuincenal -> "Quincenal"
            else -> "Mensual"
        }
    }

    private fun calcularTasaInteres(frecuencia: String, plazo: Int): Double {
        return if (frecuencia == "Semanal" && plazo in 1..13) 17.0 else 15.0
    }

    private fun guardarSolicitudParaManager(
        usuarioCuenta: String,
        cliente: String,
        monto: Double,
        frecuencia: String,
        plazo: Int,
        tasaInteres: Double
    ) {
        val prefs = getSharedPreferences("SolicitudesDB", Context.MODE_PRIVATE)
        val listaActual = JSONArray(prefs.getString("lista_solicitudes", "[]"))

        val fechaHoy = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val folio = "#SOL-${100 + listaActual.length() + 1}"

        val nuevaSolicitud = JSONObject().apply {
            put("folio", folio)
            put("usuario_cuenta", usuarioCuenta)
            put("cliente", cliente)
            put("monto", monto)
            put("frecuencia", frecuencia)
            put("plazo", plazo)
            put("tasa_interes", tasaInteres)
            put("fecha", fechaHoy)
            put("estado", "PENDIENTE")
            put("abonado", 0.0)
            put("pagos_realizados", 0)
        }

        val listaActualizada = JSONArray().apply { put(nuevaSolicitud) }
        for (i in 0 until listaActual.length()) {
            listaActualizada.put(listaActual.getJSONObject(i))
        }

        prefs.edit()
            .putBoolean("inicializado", true)
            .putString("lista_solicitudes", listaActualizada.toString())
            .apply()
    }
}