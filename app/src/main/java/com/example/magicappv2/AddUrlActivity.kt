package com.example.magicappv2

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class AddUrlActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_url)

        urlInput = findViewById(R.id.urlInput)
        saveButton = findViewById(R.id.saveButton)

        // Load existing URL if exists
        val sharedPref = getSharedPreferences("MagicAppPrefs", Context.MODE_PRIVATE)
        val existingUrl = sharedPref.getString("storedUrl", "")
        urlInput.setText(existingUrl)

        saveButton.setOnClickListener {
            val url = urlInput.text.toString()
            sharedPref.edit().putString("storedUrl", url).apply()
        }
    }
}
