package com.google.mediapipe.examples.production.fragment

import NetworkUtils.poseDataApi
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.examples.production.MainViewModel
import com.google.mediapipe.examples.production.OverlayView
import com.google.mediapipe.examples.production.PoseLandmarkerHelper
import com.google.mediapipe.examples.production.R
import com.google.mediapipe.examples.production.data.remote.PoseData
import com.google.mediapipe.examples.production.data.remote.ServerResponse
import com.google.mediapipe.examples.production.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt


class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener,
    OverlayView.OverlayViewListener {

    private lateinit var overlayView: OverlayView
    private var latestPixelLandmarks: List<Pair<Float, Float>> = emptyList()

    private var initialLandmarks: List<Pair<Float, Float>> = emptyList() // For initial storage
    private var initialStance: String = ""




    companion object {
        private const val TAG = "Pose Landmarker"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the PoseLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)

            // Close the PoseLandmarkerHelper and release resources
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        fragmentCameraBinding.overlay.setOverlayViewListener(this)


        val startButton: Button = view.findViewById(R.id.Start_button)
        val stopButton: Button = view.findViewById(R.id.Stop_button)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)

        // Enable start button and disable stop button initially
        startButton.isEnabled = true
        stopButton.isEnabled = false


        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }

        // Create the PoseLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }

        var startTime: Long = 0 // Variable to track start button press time
        startButton.setOnClickListener {
            // Disable start button and enable stop button
            startButton.isEnabled = false
            stopButton.isEnabled = true

            startTime = SystemClock.elapsedRealtime() // Record start time
            // Start your camera operations here if needed

            // Store initial landmarks and stance
            initialLandmarks = latestPixelLandmarks.toList()
            initialStance = calculateStance(initialLandmarks)
        }


        stopButton.setOnClickListener {
            // Enable start button and disable stop button
            startButton.isEnabled = true
            stopButton.isEnabled = false

            val elapsedTime = SystemClock.elapsedRealtime() - startTime
            if (elapsedTime < 2000) { // Threshold of 2 seconds
//                Toast.makeText(requireContext(), "Too fast", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_camera_to_try_again)
                return@setOnClickListener // Skip further processing if too fast
            }







            if (latestPixelLandmarks.isNotEmpty()) {
                // Handle the latest pixel landmarks here
                for ((index, point) in latestPixelLandmarks.withIndex()) {
                    Log.d(TAG, "Latest Landmark $index: (${point.first}, ${point.second})")
                }

                // Calculate the stance based on the latest pixel landmarks

                val landmarksIntFormat = latestPixelLandmarks.mapIndexed { index, (x, y) ->
                    listOf(index, x.roundToInt(), y.roundToInt())
                }

//                val stance = calculateStance(latestPixelLandmarks)


                if (latestPixelLandmarks.isNotEmpty()) {
                    val similarityThreshold = 9 // Adjust as needed
                    if (calculateSimilarity(
                            initialLandmarks, latestPixelLandmarks,
                            fragmentCameraBinding.viewFinder.width,
                            fragmentCameraBinding.viewFinder.height
                        ) >= similarityThreshold
                    ) {
//                        Toast.makeText(
//                            requireContext(),
//                            "Landmarks too similar. Try again.",
//                            Toast.LENGTH_SHORT
//                        ).show()
                        findNavController().navigate(R.id.action_camera_to_try_again)

                    } else {
                        val poseData = PoseData(landmarksIntFormat, initialStance)
                        if(initialStance==""){
                            findNavController().navigate(R.id.action_camera_to_try_again)
                            return@setOnClickListener
                        }
                        progressBar.visibility = View.VISIBLE
                        val call = poseDataApi.sendPoseData(poseData)

                        call.enqueue(object : Callback<ServerResponse> {
                            override fun onResponse(
                                call: Call<ServerResponse>,
                                response: Response<ServerResponse>
                            ) {
                                if (response.isSuccessful) {
                                    val serverResponse = response.body()
                                    if (serverResponse != null) {
                                        // Handle the server response here
                                        val playerName = serverResponse.name
                                        val playerUrl = serverResponse.url

                                        // Create a Bundle to hold arguments
                                        val args = Bundle()
                                        args.putString("playerName", playerName)
                                        args.putString("playerUrl", playerUrl)
                                        findNavController().navigate(
                                            R.id.action_camera_to_final_result,
                                            args
                                        )

// Navigate with the arguments
                                        Log.d(
                                            TAG,
                                            "Player Name: $playerName, Player URL: $playerUrl"
                                        )
                                    } else {
                                        Log.e(TAG, "Server response body is null")
                                    }
//                                    progressBar.visibility = View.GONE
                                } else {
                                    Log.e(TAG, "Server response is not successful")
                                }
                            }

                            override fun onFailure(call: Call<ServerResponse>, t: Throwable) {
//                                progressBar.visibility = View.GONE
                                Log.e(TAG, "Failed to send pose data to server: ${t.message}")
                            }
                        })
                    }


                }
            }


            // Attach listeners to UI control widgets
            initBottomSheetControls()

            // Hide the bottom sheet layout
        }
        fragmentCameraBinding.bottomSheetLayout.bottomSheetLayout.visibility = View.GONE

    }


    private fun calculateStance(pixelLandmarks: List<Pair<Float, Float>>): String {
        var stance = ""
        // Identify if the batsman is left-handed or right-handed
        if (pixelLandmarks.size >= 25) { // Assuming landmarks are of a sufficient number
            val lmList = pixelLandmarks

            if (lmList[16].first > lmList[24].first &&
                abs(lmList[15].first - lmList[16].first) + abs(lmList[15].second - lmList[16].second) <
                abs(lmList[24].second - lmList[26].second)
            ) {
                // right wrist more right than right hip and distance between wrists
                // should be less than distance between hip and knee
                Log.d(TAG, "Stance left-handed")
                stance = "Left-handed"
                // Update UI or perform other actions for left-handed stance
            } else if (lmList[15].first < lmList[23].first &&
                abs(lmList[15].first - lmList[16].first) + abs(lmList[15].second - lmList[16].second) <
                abs(lmList[23].second - lmList[25].second)
            ) {
                // left wrist more left than left hip
                Log.d(TAG, "Stance right-handed")
                stance = "Right-handed"
                // Update UI or perform other actions for right-handed stance
            }
        } else {
            Log.d(TAG, "Insufficient landmarks for stance calculation.")
        }
        return stance
    }


    private fun calculateSimilarity(landmarks1: List<Pair<Float, Float>>,
                                    landmarks2: List<Pair<Float, Float>>,
                                    viewWidth: Int, viewHeight: Int): Double {
        if (landmarks1.size != landmarks2.size) {
            return 0.0 // Automatically different if sizes don't match
        }

        var totalDistance = 0.0
        for (i in landmarks1.indices) {
            val dx = landmarks1[i].first - landmarks2[i].first
            val dy = landmarks1[i].second - landmarks2[i].second
            totalDistance += sqrt(dx * dx + dy * dy)
        }

        val averageDistance = totalDistance / landmarks1.size
        val maxPossibleDistance = sqrt((viewWidth * viewWidth + viewHeight * viewHeight).toDouble())

        // Normalize and invert to get similarity in range [0, 1]
        return 1.0 - (averageDistance / maxPossibleDistance)
    }


    private fun initBottomSheetControls() {
        // init bottom sheet settings

        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
            )

        // When clicked, lower pose detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseDetectionConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseDetectionConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower pose tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower pose presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence >= 0.2) {
                poseLandmarkerHelper.minPosePresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence <= 0.8) {
                poseLandmarkerHelper.minPosePresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference.
        // Current options are CPU and GPU
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    try {
                        poseLandmarkerHelper.currentDelegate = p2
                        updateControlsUi()
                    } catch (e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "PoseLandmarkerHelper has not been initialized yet.")
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for object detection
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel,
            false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    p2: Int,
                    p3: Long
                ) {
                    poseLandmarkerHelper.currentModel = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset Poselandmarker
    // helper.
    private fun updateControlsUi() {
        if (this::poseLandmarkerHelper.isInitialized) {
            fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
                String.format(
                    Locale.US,
                    "%.2f",
                    poseLandmarkerHelper.minPoseDetectionConfidence
                )
            fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
                String.format(
                    Locale.US,
                    "%.2f",
                    poseLandmarkerHelper.minPoseTrackingConfidence
                )
            fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
                String.format(
                    Locale.US,
                    "%.2f",
                    poseLandmarkerHelper.minPosePresenceConfidence
                )

            // Needs to be cleared instead of reinitialized because the GPU
            // delegate needs to be initialized on the thread using it when applicable
            backgroundExecutor.execute {
                poseLandmarkerHelper.clearPoseLandmarker()
                poseLandmarkerHelper.setupPoseLandmarker()
            }
            fragmentCameraBinding.overlay.clear()
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectPose(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if (this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after pose have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    override fun onResults(
        resultBundle: PoseLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    override fun onPixelLandmarksUpdated(pixelLandmarks: List<Pair<Float, Float>>) {
        // Update the latest pixel landmarks when the listener is notified
        latestPixelLandmarks = pixelLandmarks
        // Log the updated pixel landmarks
        for ((index, point) in latestPixelLandmarks.withIndex()) {
            Log.d(TAG, "Updated Pixel Landmark $index: (${point.first}, ${point.second})")
        }
    }


}
