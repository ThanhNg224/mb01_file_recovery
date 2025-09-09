package com.thanhng224.app.repository

import com.thanhng224.app.model.ProductDetailsDto
import com.thanhng224.app.util.ApiState

interface Repository {
    suspend fun getProductDetails(): ApiState<ProductDetailsDto>
}