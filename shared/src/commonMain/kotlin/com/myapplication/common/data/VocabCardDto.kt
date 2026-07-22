package com.myapplication.common.data

import kotlinx.serialization.Serializable

@Serializable
data class VocabCardDto(
    val id: String,
    val spanish: String,
    val english: String,
    val tags: List<String>
)
