package com.google.mediapipe.examples.production

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min



class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {


    interface OverlayViewListener {
        fun onPixelLandmarksUpdated(pixelLandmarks: List<Pair<Float, Float>>)
    }

    private var overlayViewListener: OverlayViewListener? = null

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1


    fun setOverlayViewListener(listener: OverlayViewListener) {
        overlayViewListener = listener
    }

    // List containing all the 33 landmarks

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor,
                        normalizedLandmark.y() * imageHeight * scaleFactor,
                        pointPaint

                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        poseLandmarkerResult.landmarks().get(0).get(it!!.start())
                            .x() * imageWidth * scaleFactor,
                        poseLandmarkerResult.landmarks().get(0).get(it.start())
                            .y() * imageHeight * scaleFactor,
                        poseLandmarkerResult.landmarks().get(0).get(it.end())
                            .x() * imageWidth * scaleFactor,
                        poseLandmarkerResult.landmarks().get(0).get(it.end())
                            .y() * imageHeight * scaleFactor,
                        linePaint
                    )
                }
            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        val pixelLandmarks = mutableListOf<List<Pair<Float, Float>>>()


        // Convert normalized coordinates to pixel values
        for (i in 0 until poseLandmarkerResults.landmarks().size) {
            val landmarkPoints = mutableListOf<Pair<Float, Float>>()
            for (j in 0 until poseLandmarkerResults.landmarks()[i].size) {
                val landmark = poseLandmarkerResults.landmarks()[i].get(j)
                val x = landmark.x() * imageWidth * scaleFactor
                val y = landmark.y() * imageHeight * scaleFactor
                landmarkPoints.add(Pair(x, y))
            }
            pixelLandmarks.add(landmarkPoints)
        }

        // Flatten the nested list structure into a single list
        val finalLandmarkResult = pixelLandmarks.flatten()

        // Notify the listener about the updated pixel landmarks
        overlayViewListener?.onPixelLandmarksUpdated(finalLandmarkResult)


        // Log pixel coordinates
        for ((index, point) in finalLandmarkResult.withIndex()) {
//            println("Landmark $index: (${point.first}, ${point.second})")
        }



        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }

            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
    }
}