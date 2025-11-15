package com.AppFlix.i220968_i228810.posts

import com.AppFlix.i220968_i228810.model.Post
import com.AppFlix.i220968_i228810.model.PostComment
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

data class PostDto(
    val id: String,
    val userId: String,
    val username: String,
    val userProfileImageUrl: String,
    val mediaUrl: String,
    val caption: String,
    val likesCount: Int,
    val commentsCount: Int,
    val createdAt: Long,
    val likedByUser: Boolean
)

interface PostApiService {

    // POST http://10.0.2.2/social_api/posts.php
    @Multipart
    @POST("posts.php")
    suspend fun uploadPost(
        @Part media: MultipartBody.Part,
        @Part("userId") userId: RequestBody,
        @Part("username") username: RequestBody,
        @Part("caption") caption: RequestBody,
        @Part("createdAt") createdAt: RequestBody,
        @Part("userProfileImageUrl") userProfileImageUrl: RequestBody
    ): Response<Post>

    // GET http://10.0.2.2/social_api/posts.php?userId=...
    @GET("posts.php")
    suspend fun getPosts(
        @Query("userId") userId: String
    ): Response<List<PostDto>>

    // POST http://10.0.2.2/social_api/likes.php
    @FormUrlEncoded
    @POST("likes.php")
    suspend fun toggleLike(
        @Field("postId") postId: String,
        @Field("userId") userId: String,
        @Field("like") like: Boolean
    ): Response<LikeResponse>

    @GET("comments.php")
    suspend fun getComments(
        @Query("postId") postId: String
    ): Response<List<PostComment>>

    @FormUrlEncoded
    @POST("comments.php")
    suspend fun addComment(
        @Field("postId") postId: String,
        @Field("userId") userId: String,
        @Field("username") username: String,
        @Field("userProfileImageUrl") userProfileImageUrl: String,
        @Field("text") text: String,
        @Field("createdAt") createdAt: Long
    ): Response<PostComment>
}
