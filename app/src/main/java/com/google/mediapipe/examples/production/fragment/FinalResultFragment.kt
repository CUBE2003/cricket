package com.google.mediapipe.examples.production.fragment

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.mediapipe.examples.production.data.local.AppDatabase
import com.google.mediapipe.examples.production.data.local.Player
import com.google.mediapipe.examples.production.data.local.bitmapToByteArray

import com.google.mediapipe.examples.production.databinding.FinalResultBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL

class FinalResultFragment : Fragment() {

    private var _binding: FinalResultBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageDatabase: AppDatabase // You already have this initialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageDatabase = AppDatabase.getDatabase(requireContext()) // Initialize the database
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FinalResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val playerName = arguments?.getString("playerName") ?: ""
        val playerUrl = arguments?.getString("playerUrl") ?: ""

        binding.textViewName.text = "" // Initially, set an empty text



        if (playerUrl.isNotEmpty()) {
            Glide.with(this).load(playerUrl).into(binding.imageView)

            CoroutineScope(Dispatchers.IO).launch {
                downloadAndStoreImage(playerName, playerUrl)
            }
        }

        requireView().isFocusableInTouchMode = true
        requireView().requestFocus()
        requireView().setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                // Navigate back to CameraFragment
                findNavController().navigateUp()
                true // Event handled
            } else {
                false // Event not handled
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            binding.textViewName.text = playerName // Set the player's name text after 3 seconds
        }, 3000) // Delay for 3 seconds (3000 milliseconds)
    }




    private suspend fun downloadAndStoreImage(playerName: String, playerUrl: String) {
        try {
            // Check if player name already exists in the database
            val existingPlayer = imageDatabase.playerDao().getPlayerByName(playerName)

            if (existingPlayer != null) {
                // Player with the same name already exists, do not download or store
                Log.d("ImageDatabase", "Player with name $playerName already exists in the database. Skipping download and store.")
                return
            }

            // Player with the same name does not exist, proceed with download and store
            val bitmap = BitmapFactory.decodeStream(URL(playerUrl).openConnection().getInputStream())
            val imageByteArray = bitmapToByteArray(bitmap)

            val imageData = Player(image = imageByteArray, name = playerName)
            imageDatabase.playerDao().insertImage(imageData) // Updated

            Log.d("ImageDatabase", "Image inserted successfully")
        } catch (e: Exception) {
            Log.e("ImageDatabase", "Error downloading or storing image: ", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
