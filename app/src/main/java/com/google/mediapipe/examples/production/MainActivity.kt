package com.google.mediapipe.examples.production

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.cricmatch.beta.examples.production.R
import com.cricmatch.beta.examples.production.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController

        val bottomNav = findViewById<BottomNavigationView>(R.id.navigation)
        bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNav.menu.findItem(destination.id)?.isChecked = true
        }
    }

    override fun onBackPressed() {
        val navController = findNavController(R.id.fragment_container)
        val currentDestination = navController.currentDestination?.id

        if (currentDestination == R.id.accounts_fragment ||
            currentDestination == R.id.camera_fragment ||
            currentDestination == R.id.history_fragment
        ) {
            // If the current destination is one of the bottom navigation destinations,
            // navigate up in the navigation controller
            navController.navigateUp()
        } else {
            super.onBackPressed()
        }
    }
}
