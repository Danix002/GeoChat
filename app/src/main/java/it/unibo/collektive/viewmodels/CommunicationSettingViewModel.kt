package it.unibo.collektive.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

class CommunicationSettingViewModel : ViewModel(){
    private var distance: Float = 0.0F
    private var time: Float = 0.0F

    /**
     * Function to format distance in meters or kilometers.
     */
    fun formatDistance(value: Float): String {
        return if (value >= 1000) {
            "${(value / 1000).toInt()} km"
        } else {
            "${value.toInt()} mt"
        }
    }

    /**
     * Function to format time in seconds or minutes.
     */
    fun formatTime(value: Float): String {
        return if (value.toInt() == 60) {
            "${(value / 60).toInt()} min"
        } else {
            "${value.toInt()} sc"
        }
    }

    /**
     * Function to set time value.
     */
    fun setTime(value: Float) {
        this.time = value
    }

    /**
     * Function to set distance value.
     */
    fun setDistance(value: Float) {
        this.distance = value
    }

    /**
     * Function to get time value.
     */
    fun getTime(): Float {
        return this.time
    }

    /**
     * Function to get distance value.
     */
    fun getDistance(): Float {
        return this.distance
    }
}
