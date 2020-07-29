package com.example.androidlocation

import android.content.Context
import android.location.Location
import androidx.core.content.edit

fun Location?.toText(): String {
    return if (this != null) {
        "(${this.latitude}, ${this.longitude})"
    } else {
        "Unknown Location"
    }
}

object SharedPreferenceUtil {
    const val KEY_FOREGROUND_ENABLED = "location_foreground_tracking"

    fun getLocationTrackingPref(context: Context): Boolean {
        return context.getSharedPreferences(
            context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)
            .getBoolean(KEY_FOREGROUND_ENABLED, false)
    }

    fun saveLocationTrackingPref(context: Context, requestingLocationUpdates: Boolean) {
        context.getSharedPreferences(
            context.getString(R.string.preference_file_key), Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_FOREGROUND_ENABLED, requestingLocationUpdates)
            }
    }
}