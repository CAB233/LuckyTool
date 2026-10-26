package com.luckyzyx.luckytool.data

import android.net.Uri
import com.canhub.cropper.CropImageOptions

data class CropImageContractOptions(
    val uri: Uri?,
    val cropImageOptions: CropImageOptions,
)
