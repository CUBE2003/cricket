package com.google.mediapipe.examples.production.data.remote


import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface PoseDataApi {
    @POST("/process_data")  // Replace '/pose-data' with your server's endpoint
    fun sendPoseData(@Body poseData: PoseData): Call<ServerResponse>
}


// Step 1: Define your PoseData class
data class PoseData(
    val landmarks: List<List<Int>>,
    val stance: String
)

