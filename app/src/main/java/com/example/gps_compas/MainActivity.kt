package com.example.gpscompass

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import android.widget.ImageView
import androidx.annotation.RequiresPermission
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var tvSpeed: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // must match activity_main.xml

//// Keep screen on
//        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//
//// Remove after 5 minutes
//        Handler(Looper.getMainLooper()).postDelayed({
//            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        }, 5 * 60 * 1000)

        tvSpeed = findViewById(R.id.tvSpeed)
        tvDirection = findViewById(R.id.tvDirection)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 2000L
        ).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                for (location in locationResult.locations) {
                    updateUI(location)
                }
            }
        }

        // Request location permission
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            startLocationUpdates()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun updateUI(location: Location) {
        val speedMps = location.speed
        val speedKmh = speedMps * 3.6
        val speedKnots = speedMps * 1.94384

        tvSpeed.text = "Speed: %.1f knots".format( speedKnots)
        tvDirection.text = "Direction: %.0f°".format(location.bearing)
//        tvCoords.text = "Lat: %.5f, Lng: %.5f".format(location.latitude, location.longitude)

        tvLatitude.text = "Lat: %.5f".format(location.latitude)
        tvLongitude.text = "Lng: %.5f".format(location.longitude)

        val arrow = findViewById<ImageView>(R.id.directionArrow)
        arrow.rotation = location.bearing   // or azimuth
    }



    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }



    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }
}
