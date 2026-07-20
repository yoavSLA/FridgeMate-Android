package com.project.fridgemate.data.remote.api

import com.project.fridgemate.data.remote.dto.ApiOkResponse
import com.project.fridgemate.data.remote.dto.InventoryItemDto
import com.project.fridgemate.data.remote.dto.PaginatedResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

interface InventoryItemApi {

    @GET("fridges/{fridgeId}/items")
    suspend fun getItems(
        @Path("fridgeId") fridgeId: String
    ): Response<PaginatedResponse<InventoryItemDto>>

    // Body is built manually (see InventoryItemRepository) so an explicit JSON null can be sent
    // to unassign an owner — Gson's default converter omits null fields from request bodies.
    @PATCH("fridges/{fridgeId}/items/{itemId}/owner")
    suspend fun assignOwner(
        @Path("fridgeId") fridgeId: String,
        @Path("itemId") itemId: String,
        @Body body: RequestBody
    ): Response<ApiOkResponse<InventoryItemDto>>
}
