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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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
            hideKeyboard()

            val url = urlInput.text.toString().trim().removeSuffix("/")
            val name = nameInput.text.toString().trim()

            if (url.isNotEmpty() && name.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    val isValid = validateUrl("$url/api/status")
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        if (isValid) {
                            sharedPref.edit {
                                putString("storedUrl", url)
                                putString("storedName", name)
                            }
                            showSnackbar("Name and URL saved successfully.")
                        } else {
                            showSnackbar("URL validation failed. Cannot save.")
                        }
                    }
                }
            } else {
                progressBar.visibility = View.GONE
                showSnackbar("Please fill in both fields.")
            }
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun validateUrl(fullUrl: String): Boolean {
        return try {
            val url = URL(fullUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == 200
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
