package pl.sokolowskib.aidemo.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import pl.sokolowskib.aidemo.data.DetectionResult

class PhotoViewModel(app: Application) : AndroidViewModel(app) {

    val latestPhoto = MutableLiveData<Bitmap>()
    val latestResizedPhoto = MutableLiveData<Bitmap>()
    val photoScale = MutableLiveData<Float>()
    val realTimeScale = MutableLiveData<Float>()
    val detectionResults = MutableLiveData<ArrayList<DetectionResult>>()
}