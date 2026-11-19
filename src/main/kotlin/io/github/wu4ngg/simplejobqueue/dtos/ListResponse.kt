package io.github.wu4ngg.simplejobqueue.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ApiListResponse<T>(val items: List<T>)