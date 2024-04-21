package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.mediapipe.examples.poselandmarker.databinding.FinalResultBinding

class FinalResultFragment : Fragment() {

    private var _binding: FinalResultBinding? = null
    private val binding get() = _binding!!

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

        // Retrieve arguments passed from CameraFragment
        val playerName = arguments?.getString("playerName")
        val playerUrl = arguments?.getString("playerUrl")

        // Update UI with playerName and playerUrl
        binding.textViewName.text = playerName
        Glide.with(this)
            .load(playerUrl)
            .into(binding.imageView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
