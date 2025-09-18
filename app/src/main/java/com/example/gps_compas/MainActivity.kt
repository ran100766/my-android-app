package com.example.gpscompass

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import android.widget.ImageView
import androidx.annotation.RequiresPermission
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import com.example.gps_compas.AzimuthMarkerView
import com.example.gps_compas.Marker
import android.view.animation.Animation
class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentDegree = 0f  // <-- declare here


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


        val latJ = 31.7795   // Jerusalem
        val lonJ = 35.2339

        val latH = 32.17094   // Home
        val lonH = 34.83833

        var (distanceJ, bearingJ) = calculateDistanceAndBearing(location.latitude, location.longitude, latJ, lonJ)

//        println("Distance: %.2f km".format(distanceJ / 1000)) // in kilometers
//        println("Bearing: %.1f°".format(bearingJ))

        var (distanceH, bearingH) = calculateDistanceAndBearing(location.latitude, location.longitude, latH, lonH)
//        println("Distance: %.2f km".format(distanceH / 1000)) // in kilometers
//        println("Bearing: %.1f°".format(bearingH))




        val arrowStatic = false

        if (arrowStatic)
        {
            val arrow = findViewById<ImageView>(R.id.directionArrow)
            val animator = ObjectAnimator.ofFloat(arrow, "rotation", arrow.rotation, location.bearing)
            animator.duration = 300  // milliseconds
            animator.interpolator = LinearInterpolator()
            animator.start()
        }
        else
        {
            val compassBackground = findViewById<ImageView>(R.id.compassBackground)

            val rotateAnimation = RotateAnimation(
                currentDegree,
                -location.bearing,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
            )
            rotateAnimation.duration = 500   // smoother transition
            rotateAnimation.fillAfter = true

            compassBackground.startAnimation(rotateAnimation)

            currentDegree = -location.bearing

            bearingJ = bearingJ - location.bearing
            if (bearingJ < 0) {
                bearingJ += 360f
            }
            bearingH = bearingH - location.bearing
            if (bearingH < 0) {
                bearingH += 360f
            }
        }



        val atJerusalem = distanceJ < 100f
        val atHome = distanceH < 100f

        val markerView = findViewById<AzimuthMarkerView>(R.id.azimuthMarker)

        val markers = listOf(
            Marker(azimuth = bearingJ, color = Color.parseColor("#673AB7"), radius = 100f, drawAtCenter = atJerusalem),  // in the middle
            Marker(azimuth = bearingH, color = Color.GREEN, radius = 100f, drawAtCenter = atHome)   // on circumference
        )

        markerView.setMarkers(markers)

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


    fun calculateDistanceAndBearing(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Pair<Float, Float> {
        val results = FloatArray(2)

        // Compute distance (in meters) and initial bearing (in degrees)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)

        val distance = results[0]   // meters
        val bearing = results[1]    // degrees from north

        return Pair(distance, bearing)
    }



}
