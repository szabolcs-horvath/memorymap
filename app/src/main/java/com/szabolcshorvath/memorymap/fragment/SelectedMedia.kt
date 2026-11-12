package com.szabolcshorvath.memorymap.fragment

import android.net.Uri
import com.szabolcshorvath.memorymap.data.MediaType

/** A photo or video in the Add form: picked in it, or loaded with the memory it edits. */
data class SelectedMedia(
    val uri: Uri,
    val type: MediaType,
    val deviceId: String,
    val fileSize: Long = 0L,
    val dateTaken: Long = 0L,
    val mediaSignature: String = ""
)
