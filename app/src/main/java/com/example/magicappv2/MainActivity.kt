package com.example.magicappv2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var anotherBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        anotherBtn = findViewById(R.id.anotherBtn)


        // Setup Hamburger Toggle
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Main Menu"

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_add_url -> {
                    val intent = Intent(this, AddUrlActivity::class.java)
                    startActivity(intent)
                }

                R.id.nav_send_loc -> {
                    val intent = Intent (this, UserLocation::class.java)
                    startActivity(intent)
                }
            }
            drawerLayout.closeDrawers()
            true
        }

        anotherBtn.setOnClickListener {
            val intent = Intent(this, NewDesign::class.java)
            startActivity(intent)
        }
    }

    // Open Drawer on toggle click
    override fun onSupportNavigateUp(): Boolean {
        drawerLayout.open()
        return true
    }
}