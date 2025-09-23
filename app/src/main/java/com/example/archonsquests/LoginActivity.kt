package com.example.archonsquests

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.SharedPreferences
import com.example.archonsquests.databinding.ActivityLoginBinding
import android.widget.Toast

class LoginActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)

        binding.loginButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                saveName(name)
                openMainActivity()
            } else {
                Toast.makeText(this, "Введите имя", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveName(name: String) {
        val editor = sharedPreferences.edit()
        editor.putString("user_name", name)
        editor.apply()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}

