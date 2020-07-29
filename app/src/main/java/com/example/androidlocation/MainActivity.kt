package com.example.androidlocation

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private var foregroundOnlyLocationServiceBound = false
    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null
    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver
    private lateinit var sharedPreferences: SharedPreferences

    private val foregroundOnlServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ForegroundOnlyLocationService.LocalBinder
            foregroundOnlyLocationService = binder.service
            foregroundOnlyLocationServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            foregroundOnlyLocationService = null
            foregroundOnlyLocationServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()
        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        foregroundOnlyLocationButton.setOnClickListener {
            val enabled = SharedPreferenceUtil.getLocationTrackingPref(this)
            if (enabled) {
                foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
            } else {

                if (foregroundPermissionApproved()) {
                    foregroundOnlyLocationService?.subscribeToLocationUpdates()
                        ?: Log.i(TAG, "Service Not Bound")
                } else {
                    requestForegroundPermissions()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateButtonState(SharedPreferenceUtil.getLocationTrackingPref(this))
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        bindService(
            Intent(this, ForegroundOnlyLocationService::class.java),
            foregroundOnlServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager
            .getInstance(applicationContext)
            .registerReceiver(foregroundOnlyBroadcastReceiver,
                IntentFilter(ForegroundOnlyLocationService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager
            .getInstance(applicationContext)
            .unregisterReceiver(foregroundOnlyBroadcastReceiver)
    }

    override fun onStop() {
        super.onStop()
        if (foregroundOnlyLocationServiceBound) {
            unbindService(foregroundOnlServiceConnection)
            foregroundOnlyLocationServiceBound = false
        }

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == SharedPreferenceUtil.KEY_FOREGROUND_ENABLED) {
            updateButtonState(SharedPreferenceUtil.getLocationTrackingPref(this))
        }
    }

    private fun foregroundPermissionApproved(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestForegroundPermissions() {
        val provideRational = foregroundPermissionApproved()

        if (provideRational) {
            Snackbar.make(
                findViewById<View>(R.id.activity_main),
                getString(R.string.permission_rationale),
                Snackbar.LENGTH_LONG
            ).setAction(R.string.ok) {
                ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
                )
            }.show()
        } else {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        when (requestCode) {
            REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE -> when {
                grantResults.isEmpty() -> Log.i(TAG, "User interaction was canceled. ")
                grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                    foregroundOnlyLocationService?.subscribeToLocationUpdates()
                else -> {
                    updateButtonState(false)
                    Snackbar.make(
                        findViewById<View>(R.id.activity_main),
                        getString(R.string.permission_denied_explanation),
                        Snackbar.LENGTH_LONG
                    ).setAction(R.string.settings) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts(
                            "package",
                            BuildConfig.APPLICATION_ID,
                            null
                        )
                        intent.data = uri
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    }.show()
                }
            }
        }
    }

    private fun updateButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            foregroundOnlyLocationButton.text = getString(R.string.stop_location_updates_button_text)
        } else {
            foregroundOnlyLocationButton.text = getString(R.string.start_location_updates_button_text)
        }
    }

    private fun logResultToScreen(output: String) {
        val outputWithPreviousLogs = "$output\n${outputTextView.text}"
        outputTextView.text = outputWithPreviousLogs
    }

    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val location =
                intent.getParcelableExtra<Location>(ForegroundOnlyLocationService.EXTRA_LOCATION)
            if (location != null) {
                logResultToScreen("Foreground location: ${location.toText()}")
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 50
    }
}