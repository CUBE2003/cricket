package com.google.mediapipe.examples.production.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.production.PlayerAdapter
import com.google.mediapipe.examples.production.R
import com.google.mediapipe.examples.production.data.local.AppDatabase

class HistoryScreenFragement : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlayerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.history_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = PlayerAdapter(emptyList())
        recyclerView.adapter = adapter

        // Fetch images from database
        fetchImagesFromDatabase()

        adapter.reverseList()
    }

    private fun fetchImagesFromDatabase() {
        lifecycleScope.launchWhenStarted {
            val images = AppDatabase.getDatabase(requireContext()).playerDao().getAllPlayerImages()
            images.collect { fetchedImages ->
                if (fetchedImages.isNotEmpty()) {
                    // If there are images, update the adapter with fetched images
                    adapter.updateImages(fetchedImages)
                    // Now that the list is populated, reverse it
                    adapter.reverseList()
                } else {
                    // If no images found, show a message or handle it accordingly
                    // For example, you can show a toast message
                    Toast.makeText(requireContext(), "No images found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireView().isFocusableInTouchMode = true
        requireView().requestFocus()
        requireView().setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                findNavController().navigateUp()
                true
            } else {
                false
            }
        }
    }
}