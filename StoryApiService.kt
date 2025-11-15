package com.AppFlix.i220968_i228810.stories

import com.AppFlix.i220968_i228810.model.Story
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface StoryApiService {

    @Multipart
    @POST("stories.php")
    suspend fun uploadStory(
        @Part media: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("username") username: RequestBody,
        @Part("mediaType") mediaType: RequestBody,
        @Part("createdAt") createdAt: RequestBody,
        @Part("expiresAt") expiresAt: RequestBody,
        @Part("userProfileImageUrl") userProfileImageUrl: RequestBody
    ): Response<Story>

    @GET("stories.php")
    suspend fun getStories(): Response<List<Story>>

    @DELETE("stories.php")
    suspend fun deleteStory(
        @Path("id") storyId: String
    ): Response<Unit>
}
