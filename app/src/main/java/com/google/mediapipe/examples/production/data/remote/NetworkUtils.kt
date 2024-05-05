import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.mediapipe.examples.production.data.remote.PoseDataApi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// NetworkUtils.kt
object NetworkUtils {
    private val gson: Gson = GsonBuilder().create()

    private val loggingInterceptor = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://cricmatch-31599808da29.herokuapp.com")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()

    val poseDataApi: PoseDataApi = retrofit.create(PoseDataApi::class.java)
}
