package com.hack.messenger

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val prefs = getSharedPreferences("hack_msg", MODE_PRIVATE)
        val nameEdit = findViewById<EditText>(R.id.editName)
        val passEdit = findViewById<EditText>(R.id.editPass)
        val btnSave  = findViewById<Button>(R.id.btnSave)

        nameEdit.setText(prefs.getString("name",""))
        passEdit.setText(prefs.getString("password",""))

        btnSave.setOnClickListener {
            val name = nameEdit.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this,"// введи имя //", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putString("name", name)
                .putString("password", passEdit.text.toString())
                .apply()
            startActivity(Intent(this, ChatsActivity::class.java))
            finish()
        }
    }
}
