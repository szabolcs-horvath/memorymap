package com.szabolcshorvath.memorymap.fakes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract

/**
 * The photo picker without the system's screen: opening it returns [nextPick] at once, as if the user had chosen
 * those photos (gallery URIs, for example from `TestData.galleryPhoto`). The app then handles them as it handles
 * real picks. An empty [nextPick] is a cancelled picker. Every test runs in a new process, so it starts empty.
 */
object FakeMediaPicker : ActivityResultContract<PickVisualMediaRequest, List<Uri>>() {
    @Volatile
    var nextPick: List<Uri> = emptyList()

    override fun getSynchronousResult(context: Context, input: PickVisualMediaRequest): SynchronousResult<List<Uri>> =
        SynchronousResult(nextPick)

    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent =
        error("The fake photo picker answers at once and never opens a screen")

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> = emptyList()
}
