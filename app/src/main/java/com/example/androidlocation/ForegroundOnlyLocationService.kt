package com.example.androidlocation

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*

class ForegroundOnlyLocationService : Service() {

    private var serviceRunningInBackground = false
    private var configurationChange = false
    private var currentLocation: Location? = null

    private lateinit var notificationManager: NotificationManager
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest().apply {
            interval = 600
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                if (locationResult?.lastLocation != null) {

                    currentLocation = locationResult.lastLocation

                    if (serviceRunningInBackground) {
                        notificationManager.notify(
                            NOTIFICATION_ID, generateNotification(currentLocation))
                    } else {
                        val intent = Intent(ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
                        intent.putExtra(EXTRA_LOCATION, currentLocation)
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                    }
                }
            }
        }


    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val cancelTracking = intent.getBooleanExtra(
            EXTRA_CANCEL_TRACKING_LOCATION_FROM_NOTIFICATION, false)
        if (cancelTracking) {
            unsubscribeToLocationUpdates()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        stopForeground(true)
        serviceRunningInBackground = false
        configurationChange = false
        return LocalBinder()
    }

    override fun onRebind(intent: Intent?) {
        stopForeground(true)
        serviceRunningInBackground = false
        configurationChange = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (!configurationChange && SharedPreferenceUtil.getLocationTrackingPref(this)) {
            serviceRunningInBackground = true
            val notification = generateNotification(currentLocation)
            startForeground(NOTIFICATION_ID, notification)
        }
        return true
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChange = true
    }

    fun subscribeToLocationUpdates() {
        SharedPreferenceUtil.saveLocationTrackingPref(this, true)
        startService(Intent(applicationContext, ForegroundOnlyLocationService::class.java))

        try {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.myLooper())
        } catch (exception: SecurityException) {
            SharedPreferenceUtil.saveLocationTrackingPref(this, false)
            Log.e(TAG, "Lost location permissions. Couldn't remove updates. $exception")
        }
    }

    fun unsubscribeToLocationUpdates() {
        try {
            val removeTask = fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            removeTask.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    stopSelf()
                    Log.i(TAG, "Location updates removed.")
                } else {
                    Log.i(TAG, "Lost location permissions. Couldn't remove updates.")
                }
            }

            SharedPreferenceUtil.saveLocationTrackingPref(this, false)
        } catch (exception: SecurityException) {
            SharedPreferenceUtil.saveLocationTrackingPref(this, true)
            Log.e(TAG, "Lost location permissions. Couldn't remove updates. $exception")
        }
    }

    fun generateNotification(location: Location?): Notification {
        val titleText = getString(R.string.app_name)
        val mainContentText = location?.toText() ?: getString(R.string.no_location_text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, titleText, NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(titleText)
            .bigText(mainContentText)

        val launchIntent = Intent(this, MainActivity::class.java)

        val cancelIntent = Intent(this, ForegroundOnlyLocationService::class.java)
        cancelIntent.putExtra(EXTRA_CANCEL_TRACKING_LOCATION_FROM_NOTIFICATION, true)

        val activityPendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, 0)
        val servicePendingIntent = PendingIntent.getService(
            this, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)

        return notificationBuilder.apply {
            setContentTitle(titleText)
            setContentText(mainContentText)
            setStyle(bigTextStyle)
            setSmallIcon(R.mipmap.ic_launcher)
            setDefaults(NotificationCompat.DEFAULT_ALL)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setOngoing(true)
            addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
                activityPendingIntent)
            addAction(R.drawable.ic_cancel, getString(R.string.stop_location_updates_button_text),
                servicePendingIntent)
        }.build()
    }


    inner class LocalBinder : Binder() {
        val service: ForegroundOnlyLocationService
            get() = this@ForegroundOnlyLocationService
    }

    companion object {
        private const val TAG = "LocationService"
        private const val PACKAGE_NAME = "com.example.android.android_location"
        private const val NOTIFICATION_CHANNEL_ID = "android_location_01"
        private const val NOTIFICATION_ID = 5050
        private const val EXTRA_CANCEL_TRACKING_LOCATION_FROM_NOTIFICATION =
            "$PACKAGE_NAME.extra.CANCEL_TRACKING_LOCATION_FROM_NOTIFICATION"
        internal const val ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST =
            "$PACKAGE_NAME.action.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST"
        internal const val EXTRA_LOCATION = "$PACKAGE_NAME.extra.LOCATION"
    }
}