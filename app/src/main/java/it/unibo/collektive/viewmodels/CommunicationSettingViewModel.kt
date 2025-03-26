package it.unibo.collektive.viewmodels

class CommunicationSettingViewModel {
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

    }

    /**
     * Function to set time value.
     */
    fun setDistance(value: Float) {

    }
}
