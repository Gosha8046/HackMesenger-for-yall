package com.hack.messenger

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check if profile exists
        val prefs = getSharedPreferences("hack_msg", MODE_PRIVATE)
        val name = prefs.getString("name", "")
        if (name.isNullOrEmpty()) {
            startActivity(Intent(this, ProfileActivity::class.java))
        } else {
            startActivity(Intent(this, ChatsActivity::class.java))
        }
        finish()
    }
}
