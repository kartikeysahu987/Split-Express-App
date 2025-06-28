package com.example.splitexpress.network

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import java.util.*

// ---- USER DATA CLASSES ----

data class SignupRequest(
    val first_name: String,
    val last_name: String,
    val email: String,
    val password: String,
    val phone: String,
    val user_type: String
)

data class SignupResponse(
    val message: String,
    val user_id: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val message: String,
    val token: String,
    val refresh_token: String,
    val user: User
)

data class User(
    val first_name: String?,
    val last_name: String?,
    val email: String?,
    val phone: String?,
    val user_type: String?,
    val user_id: String?
)

data class UsersResponse(
    val total_count: Int,
    val user_items: List<User>
)

// ---- OTP DATA CLASSES ----

data class OTPRequest(
    val email: String
)

data class OTPResponse(
    val message: String,
    val email: String
)

data class OTPVerification(
    val email: String,
    val otp: String
)

data class OTPVerificationResponse(
    val message: String,
    val token: String,
    val refresh_token: String,
    val user: User
)

// ---- TRIP DATA CLASSES ----

data class CreateTripRequest(
    val trip_name: String,
    val description: String?,
    val members: List<String>
)

data class CreateTripResponse(
    val message: String,
    val tripID: String
)

data class Trip(
    val _id: String,
    val trip_id: String,
    val trip_name: String,
    val description: String?,
    val members: List<String>,
    val creator_id: String,
    val invite_code: String,
    val created_at: String
)

data class TripsResponse(
    val total_count: Int,
    @SerializedName("trips")
    val user_items: List<Trip>  // Keep user_items but map it to "trips" from backend
)

data class GetMembersRequest(
    val invite_code: String
)

data class MembersResponse(
    val trip_id: String,
    val trip_name: String,
    val free_members: List<String>,
    val not_free_members: List<String>,
    val total_members: Int,
    val total_free: Int,
    val total_not_free: Int
)

data class LinkMemberRequest(
    val invite_code: String,
    val name: String
)

data class LinkMemberResponse(
    val message: String,
    val trip_id: String,
    val trip_name: String,
    val free_members: List<String>,
    val not_free_members: List<String>,
    val total_members: Int,
    val total_free: Int,
    val total_not_free: Int
)

// ---- TRANSACTION DATA CLASSES ----

data class PayRequest(
    val trip_id: String,
    val payer_name: String,
    val reciever_name: String,
    val amount: String,
    val description: String
)

data class SettleRequest(
    val trip_id: String,
    val payer_name: String,
    val reciever_name: String,
    val amount: String,
    val description: String?
)

data class Transaction(
    val _id: String,
    val trip_id: String,
    @SerializedName("payername")
    val payer_name: String,
    @SerializedName("recivername")
    val reciever_name: String,
    val amount: String,
    val description: String?,
    val type: String,
    val created_at: String
)

data class TransactionResponse(
    val message: String,
    val transaction_id: String,
    val transaction: Transaction
)

data class GetTransactionsRequest(
    val trip_id: String
)

data class TransactionsResponse(
    val total_count: Int,
    val transactions: List<Transaction>
)

data class GetSettlementsRequest(
    val trip_id: String
)

data class Settlement(
    val from: String,
    val to: String,
    val amount: String
)

data class SettlementsResponse(
    val settlements: List<Settlement>
)

data class GetCasualNameRequest(
    val trip_id: String
)

data class GetCasualNameResponse(
    val casual_name: String,
    val trip_id: String
)


// ---- RETROFIT SERVICE ----

interface ApiService {

    // User endpoints
    @POST("users/signup")
    suspend fun signup(@Body request: SignupRequest): Response<SignupResponse>

    @POST("users/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @GET("users")
    suspend fun getUsers(@Header("token") token: String): Response<UsersResponse>

    @GET("users/{user_id}")
    suspend fun getUser(
        @Path("user_id") userId: String,
        @Header("token") token: String
    ): Response<User>

    // OTP endpoints
    @POST("users/getotp")
    suspend fun getOTP(@Body request: OTPRequest): Response<OTPResponse>

    @POST("users/verifyotp")
    suspend fun verifyOTP(@Body request: OTPVerification): Response<OTPVerificationResponse>

    // Trip endpoints
    @POST("trip/create")
    suspend fun createTrip(
        @Header("token") token: String,
        @Body request: CreateTripRequest
    ): Response<CreateTripResponse>

    @GET("trip/getalltrip")
    suspend fun getAllTrips(
        @Header("token") token: String,
//        @Query("recordPerPage") recordPerPage: Int = 10,
//        @Query("page") page: Int = 1
    ): Response<TripsResponse>

    @GET("trip/getallmytrip")
    suspend fun getAllMyTrips(
        @Header("token") token: String,
        @Query("recordPerPage") recordPerPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<TripsResponse>

    @POST("trip/getmembers")
    suspend fun getMembers(
        @Header("token") token: String,
        @Body request: GetMembersRequest
    ): Response<MembersResponse>

    @POST("trip/linkmember")
    suspend fun linkMember(
        @Header("token") token: String,
        @Body request: LinkMemberRequest
    ): Response<LinkMemberResponse>

    // Transaction endpoints
    @POST("trip/pay")
    suspend fun pay(
        @Header("token") token: String,
        @Body request: PayRequest
    ): Response<TransactionResponse>

    @POST("trip/settle")
    suspend fun settle(
        @Header("token") token: String,
        @Body request: SettleRequest
    ): Response<TransactionResponse>

    @POST("trip/getAllTransaction")
    suspend fun getAllTransactions(
        @Header("token") token: String,
        @Body request: GetTransactionsRequest,
        @Query("recordPerPage") recordPerPage: Int = 10,
        @Query("page") page: Int = 1
    ): Response<TransactionsResponse>

    @POST("trip/getsettlements")
    suspend fun getSettlements(
        @Header("token") token: String,
        @Body request: GetSettlementsRequest
    ): Response<SettlementsResponse>

    @POST("trip/getcausualnamebyuid")
    suspend fun getCasualNameByUID(
        @Header("token") token: String,
        @Body request: GetCasualNameRequest
    ): Response<GetCasualNameResponse>
}

// ---- RETROFIT INSTANCE ----

object RetrofitInstance {

    private val interceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .build()

    private const val BASE_URL = "https://split-go.onrender.com/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}