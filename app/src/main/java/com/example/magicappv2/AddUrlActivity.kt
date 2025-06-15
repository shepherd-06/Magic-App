package com.example.magicappv2

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.snackbar.Snackbar

class AddUrlActivity : AppCompatActivity() {

    private lateinit var urlInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var saveButton: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_url)

        urlInput = findViewById(R.id.urlInput)
        nameInput = findViewById(R.id.nameInput)
        saveButton = findViewById(R.id.saveButton)
        progressBar = findViewById(R.id.progress_circular)
        progressBar.visibility = View.GONE

        // Load existing URL if exists
        val sharedPref = getSharedPreferences("MagicAppPrefs", Context.MODE_PRIVATE)
        val existingUrl = sharedPref.getString("storedUrl", "")
        val existingName = sharedPref.getString("storedName", "")

        if (existingUrl != "") {
            urlInput.setText(existingUrl)
        }

        if (existingName != "") {
            nameInput.setText(existingName)
        }

        saveButton.setOnClickListener {
            progressBar.visibility = View.VISIBLE

            val url = urlInput.text.toString()
            val name = nameInput.text.toString()

            if (url != "" && name != "") {
                sharedPref.edit {
                    putString("storedUrl", url)
                    putString("storedName", name)
                }
                showSnackbar("Name and URL saved.")
            }
            progressBar.visibility = View.GONE
        }
    }

    fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }
}
