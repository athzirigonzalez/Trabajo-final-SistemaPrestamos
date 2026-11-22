package com.example.sistemaprestamos

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    private var esModoAdmin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val prefsUsuarios = getSharedPreferences("UsuariosGuardados", Context.MODE_PRIVATE)

        // Cuentas iniciales por defecto si es la primera vez
        if (!prefsUsuarios.contains("admin_admin")) {
            prefsUsuarios.edit().putString("admin_admin", "1234").apply()
        }
        if (!prefsUsuarios.contains("cliente_cliente")) {
            prefsUsuarios.edit().putString("cliente_cliente", "1234").apply()
        }

        val rgRol = findViewById<RadioGroup>(R.id.rgRolLogin)
        val tvBadgeRol = findViewById<TextView>(R.id.tvLoginBadgeRol)
        val tvAyuda = findViewById<TextView>(R.id.tvLoginAyuda)
        val etUsuario = findViewById<EditText>(R.id.etLoginUsuario)
        val etPassword = findViewById<EditText>(R.id.etLoginPassword)
        val tvMensaje = findViewById<TextView>(R.id.tvLoginMensaje)
        val btnIngresar = findViewById<Button>(R.id.btnIngresar)
        val btnCrearUsuario = findViewById<Button>(R.id.btnCrearUsuario)

        rgRol.setOnCheckedChangeListener { _, checkedId ->
            tvMensaje.visibility = View.GONE
            esModoAdmin = (checkedId == R.id.rbRolAdmin)

            if (esModoAdmin) {
                tvBadgeRol.text = "PORTAL DE ADMINISTRADOR (MANAGER)"
                btnIngresar.text = "Ingresar como Administrador"
                btnIngresar.setBackgroundColor(Color.parseColor("#0F172A"))
                btnCrearUsuario.text = "Registrar Administrador"
                tvAyuda.text = "Cuenta demo administrador: admin / 1234"
            } else {
                tvBadgeRol.text = "PORTAL DE CLIENTES"
                btnIngresar.text = "Ingresar como Cliente"
                btnIngresar.setBackgroundColor(Color.parseColor("#2563EB"))
                btnCrearUsuario.text = "Registrar Cuenta de Cliente"
                tvAyuda.text = "Cuenta demo cliente: cliente / 1234"
            }
        }

        // REGISTRAR SEGÚN EL ROL SELECCIONADO
        btnCrearUsuario.setOnClickListener {
            val usuario = etUsuario.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (usuario.isEmpty() || password.isEmpty()) {
                mostrarMensaje(tvMensaje, "Escribe un nombre de usuario y contraseña", "#EF4444")
                return@setOnClickListener
            }

            val prefijo = if (esModoAdmin) "admin_" else "cliente_"
            val etiquetaRol = if (esModoAdmin) "Administrador" else "Cliente"

            if (prefsUsuarios.contains("$prefijo$usuario")) {
                mostrarMensaje(
                    tvMensaje,
                    "Ese $etiquetaRol ya está registrado. Presiona Ingresar.",
                    "#D97706"
                )
                return@setOnClickListener
            }

            prefsUsuarios.edit().putString("$prefijo$usuario", password).apply()
            mostrarMensaje(
                tvMensaje,
                "$etiquetaRol '$usuario' registrado correctamente. Ya puedes ingresar.",
                "#10B981"
            )
            Toast.makeText(this, "$etiquetaRol guardado", Toast.LENGTH_SHORT).show()
        }

        // INGRESAR SEGÚN EL ROL SELECCIONADO
        btnIngresar.setOnClickListener {
            val usuario = etUsuario.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (usuario.isEmpty() || password.isEmpty()) {
                mostrarMensaje(tvMensaje, "Ingresa tu usuario y contraseña", "#EF4444")
                return@setOnClickListener
            }

            val prefijo = if (esModoAdmin) "admin_" else "cliente_"
            val rolGuardar = if (esModoAdmin) "ADMIN" else "CLIENTE"
            val passwordGuardada = prefsUsuarios.getString("$prefijo$usuario", null)

            if (passwordGuardada != null && passwordGuardada == password) {
                getSharedPreferences("LoanSession", Context.MODE_PRIVATE)
                    .edit()
                    .putString("usuario_activo", usuario)
                    .putString("rol_activo", rolGuardar)
                    .apply()

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                val tipoTexto = if (esModoAdmin) "administrador" else "cliente"
                mostrarMensaje(tvMensaje, "Usuario o contraseña de $tipoTexto incorrectos", "#EF4444")
            }
        }
    }

    private fun mostrarMensaje(tv: TextView, texto: String, colorHex: String) {
        tv.text = texto
        tv.setTextColor(Color.parseColor(colorHex))
        tv.visibility = View.VISIBLE
    }
}