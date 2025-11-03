package com.example.gpscompass

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
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
import com.example.gps_compas.MarkerConfig
import com.example.gps_compas.Marker
import android.view.animation.Animation
import com.google.firebase.FirebaseApp
import android.widget.LinearLayout
import com.example.gps_compas.FirestoreManager
import com.example.gps_compas.ReferencePoint
import com.example.gps_compas.askUserName
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        val noName = "No_Name"
        var userName: String = noName
    }
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var currentDegree = 0f  // <-- declare here


    private lateinit var tvSpeed: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView

    private var previousLatitude: Double = 90.0

    private var previousLongitude: Double = 180.0


    data class NavigationResult(
        var point: ReferencePoint,
        var distance: Float,
        var bearing: Float,
        var atPoint: Boolean = false
    )

    private var referencePoints: MutableList<ReferencePoint> = mutableListOf(
//        ReferencePoint("Jerusalem", 31.7795, 35.2339),
//        ReferencePoint("Home", 32.17062, 34.83878),
//        ReferencePoint("Marina", 32.16580, 34.79267)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // must match activity_main.xml


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
                locationResult.lastLocation?.let { location -> updateUI(location)}
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

        FirebaseApp.initializeApp(this)

// Save name
        val prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)
        prefs.edit().putString("userName", userName).apply()

// Load name in onCreate
        val savedName = prefs.getString("userName", null)
        if (savedName == null || savedName == noName) {
            askUserName(this) { name ->
                userName = name
                prefs.edit().putString("userName", name).apply()
            }
        } else {
            userName = savedName
        }

        val serviceIntent = Intent(this, LocationService::class.java)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        requestIgnoreBatteryOptimizations()

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

        tvSpeed.text = "Speed: %.1f knots".format(speedKnots)
        tvDirection.text = "Direction: %.0f°".format(location.bearing)
//        tvCoords.text = "Lat: %.5f, Lng: %.5f".format(location.latitude, location.longitude)

        tvLatitude.text = "Lat: %.5f".format(location.latitude)
        tvLongitude.text = "Lng: %.5f".format(location.longitude)


        val firestoreManager = FirestoreManager()

        firestoreManager.readAllLocations { points ->
            // This block runs after Firestore data is loaded
            if (points.isNotEmpty()) {
                // Assign to a variable for later use
                referencePoints = points.toMutableList()

                // Use referencePoints here, e.g., update UI or show on map
            }
        }

        val results = referencePoints.map { point ->
            val (distance, bearing) = CalculateDistance.calculateDistanceAndBearing(
                location.latitude,
                location.longitude,
                point.lat,
                point.lon
            )
            NavigationResult(point, distance, bearing, distance < 10F)
        }


        val arrowStatic = false

        if (arrowStatic) {
            val arrow = findViewById<ImageView>(R.id.directionArrow)
            val animator =
                ObjectAnimator.ofFloat(arrow, "rotation", arrow.rotation, location.bearing)
            animator.duration = 300  // milliseconds
            animator.interpolator = LinearInterpolator()
            animator.start()
        } else {
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


            for (r in results) {
                r.bearing = r.bearing - location.bearing
                if (r.bearing < 0) {
                    r.bearing += 360f
                }
            }

        }


        val markerView = findViewById<AzimuthMarkerView>(R.id.azimuthMarker)

        val markers = results.mapIndexed { index, r ->

            Marker(
                azimuth = r.bearing,
                color = MarkerConfig.colors[index % MarkerConfig.colors.size], // safe wrapping
                radius = 100f,
                drawAtCenter = r.atPoint,
                distance = r.distance.toInt()
            )

        }

        markerView.setMarkers(markers)

        showPointsList(results)
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

    fun showPointsList(results: List<NavigationResult>) {

        val pointsContainer = findViewById<LinearLayout>(R.id.pointsContainer)
        pointsContainer.removeAllViews()

        for ((index, point) in results.withIndex()) {
            val sdf = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.getDefault())
            val lastUpdateStr = point.point.lastUpdate?.let { sdf.format(it) } ?: "N/A"
            val tv = TextView(this)
            tv.textSize = 12f
            tv.setTypeface(null, Typeface.BOLD) // 👈 makes the text bold

//
//            tv.text = getString(
//                R.string.point_info,
//                point.point.name,
//                point.distance.toInt(),
//                lastUpdateStr)

            // Fixed-width columns
            val text = String.format("%-12s %-7d %-20s", point.point.name.take(11), point.distance.toInt(), lastUpdateStr)
            tv.text = text
            tv.typeface = Typeface.MONOSPACE // ensures columns align


            tv.setPadding(16, 16, 16, 16)
            // Set background color, cycling through list if more points than colors
            tv.setBackgroundColor(MarkerConfig.colors[index % MarkerConfig.colors.size])
            pointsContainer.addView(tv)
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = packageName

            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
    }
}
