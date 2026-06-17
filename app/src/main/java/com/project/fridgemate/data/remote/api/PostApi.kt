package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface PostApi {

    @GET("posts")
    suspend fun getPosts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("scope") scope: String? = null
    ): Response<PostListResponse>

    @GET("posts/me")
    suspend fun getMyPosts(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<PostListResponse>

    @GET("posts/by-user/{userId}")
    suspend fun getPostsByUser(
        @Path("userId") userId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<PostListResponse>

    @POST("posts")
    suspend fun createPost(
        @Body request: CreatePostRequest
    ): Response<ApiOkResponse<Any>>

    @PUT("posts/{post_id}")
    suspend fun updatePost(
        @Path("post_id") postId: String,
        @Body request: UpdatePostRequest
    ): Response<ApiOkResponse<Any>>

    @DELETE("posts/{post_id}")
    suspend fun deletePost(
        @Path("post_id") postId: String
    ): Response<ApiOkResponse<Any>>

    @POST("posts/{post_id}/like")
    suspend fun toggleLike(
        @Path("post_id") postId: String
    ): Response<ApiOkResponse<ToggleLikeResponse>>

    @GET("posts/{postId}/comments")
    suspend fun getComments(
        @Path("postId") postId: String
    ): Response<ApiOkResponse<CommentsListResponse>>

    @POST("posts/{postId}/comments")
    suspend fun createComment(
        @Path("postId") postId: String,
        @Body request: CreateCommentRequest
    ): Response<ApiOkResponse<CommentDto>>

    @PUT("posts/{postId}/comments/{commentId}")
    suspend fun updateComment(
        @Path("postId") postId: String,
        @Path("commentId") commentId: String,
        @Body request: UpdateCommentRequest
    ): Response<ApiOkResponse<CommentDto>>

    @DELETE("posts/{postId}/comments/{commentId}")
    suspend fun deleteComment(
        @Path("postId") postId: String,
        @Path("commentId") commentId: String
    ): Response<ApiOkResponse<Any>>

    @Multipart
    @POST("upload")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part
    ): Response<ApiOkResponse<UploadImageResponse>>
}
