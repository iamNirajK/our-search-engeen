package com.example.data.model

data class BlockedItem(
    val url: String,
    val domain: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)
