package com.niquewrld.conversationalai

import android.content.Context
import com.google.gson.annotations.SerializedName

data class Model(
    val name: String = "",
    val filename: String = "",
    val link: String = "",
    @SerializedName("download_size")
    val downloadSize: String = "",
    val description: String = "",
    val isUncensored: Boolean = false,
    @SerializedName("template_id")
    val templateId: Int = 0,
    val isVision: Boolean = false,
    val isTools: Boolean = false,
    @SerializedName("isimageGen")
    val isImageGen: Boolean = false,
    val isMediapipe: Boolean = false,
    val isGGUF: Boolean = false
) : java.io.Serializable

enum class ModelType {
    Mediapipe,
    GGUF
}

class ModelRepository(private val context: Context, private val viewModel: MainViewModel) {
    // Model repository implementation stub
}
