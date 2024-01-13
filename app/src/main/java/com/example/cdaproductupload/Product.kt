package com.example.cdaproductupload

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val price: Float,
    val offerPercentage: Float? = null,
    val description: String? = null,
    val sizes: List<String>? = null,
    val images: List<String>?=null
)