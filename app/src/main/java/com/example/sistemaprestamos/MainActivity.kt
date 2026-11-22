package com.example.sistemaprestamos

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("LoanSession", Context.MODE_PRIVATE)
        val usuario = prefs.getString("usuario_activo", null)

        if (usuario.isNullOrEmpty()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val rol = prefs.getString("rol_activo", "CLIENTE")?.trim() ?: "CLIENTE"
        val esManager = rol.equals("ADMIN", ignoreCase = true) ||
                rol.equals("ADMINISTRADOR", ignoreCase = true) ||
                rol.equals("MANAGER", ignoreCase = true)

        val tvRolBadge = findViewById<TextView>(R.id.tvMainRolBadge)
        val tvBienvenida = findViewById<TextView>(R.id.tvMainBienvenida)
        val tvDescripcion = findViewById<TextView>(R.id.tvMainDescripcion)

        val btnNuevoPrestamo = findViewById<Button>(R.id.btnNuevoPrestamo)
        val btnDetalleMiPrestamo = findViewById<Button>(R.id.btnDetalleMiPrestamo)
        val btnVerDashboard = findViewById<Button>(R.id.btnVerDashboard)
        val btnVerManager = findViewById<Button>(R.id.btnVerManager)
        val btnRegistrarAbonoMenu = findViewById<Button>(R.id.btnRegistrarAbonoMenu)
        val btnCerrarSesion = findViewById<Button>(R.id.btnCerrarSesion)

        tvBienvenida.text = "Hola, $usuario"

        if (esManager) {
            // PERFIL MANAGER / ADMINISTRADOR:
            // Tiene Cartera, Gerencia y Abonos/Recibos; sin opciones de solicitar préstamo
            tvRolBadge.text = "ADMINISTRADOR / MANAGER"
            tvRolBadge.setBackgroundColor(Color.parseColor("#10B981"))
            tvDescripcion.text = "Gestión de solicitudes, cartera de clientes y cobranza"

            btnNuevoPrestamo.visibility = View.GONE
            btnDetalleMiPrestamo.visibility = View.GONE

            btnVerDashboard.visibility = View.VISIBLE
            btnVerManager.visibility = View.VISIBLE
            btnRegistrarAbonoMenu.visibility = View.VISIBLE
            btnRegistrarAbonoMenu.text = "Realizar Abono y Recibos"
            btnCerrarSesion.visibility = View.VISIBLE
        } else {
            // PERFIL CLIENTE:
            // Únicamente Solicitar Préstamo, Ver Detalle de mi Préstamo y Cerrar Sesión
            tvRolBadge.text = "CUENTA DE CLIENTE"
            tvRolBadge.setBackgroundColor(Color.parseColor("#2563EB"))
            tvDescripcion.text = "Solicita tu préstamo o consulta el estado de tu cuenta"

            btnNuevoPrestamo.visibility = View.VISIBLE
            btnDetalleMiPrestamo.visibility = View.VISIBLE

            btnVerDashboard.visibility = View.GONE
            btnVerManager.visibility = View.GONE
            btnRegistrarAbonoMenu.visibility = View.GONE
            btnCerrarSesion.visibility = View.VISIBLE
        }

        btnNuevoPrestamo.setOnClickListener {
            startActivity(Intent(this, LoanFormActivity::class.java))
        }

        btnDetalleMiPrestamo.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        btnVerDashboard.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }

        btnVerManager.setOnClickListener {
            startActivity(Intent(this, ManagerActivity::class.java))
        }

        btnRegistrarAbonoMenu.setOnClickListener {
            startActivity(Intent(this, RegistrarAbonoActivity::class.java))
        }

        btnCerrarSesion.setOnClickListener {
            prefs.edit().remove("usuario_activo").remove("rol_activo").apply()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}