package com.example.gpscompass

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Typeface
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
import com.example.gps_compas.MarkerConfig
import com.example.gps_compas.Marker
import android.view.animation.Animation
import com.google.firebase.FirebaseApp
import android.util.Log
import android.widget.LinearLayout
import com.example.gps_compas.FirestoreManager
import com.example.gps_compas.ReferencePoint
import com.example.gps_compas.askUserName

import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private var userName: String = "No Name"  // Variable to store the name

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

        FirebaseApp.initializeApp(this)


        askUserName(this) { name ->
            userName = name
            // You can continue using userName here
        }    }

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


        val distance = calculateDistanceAndBearing(
            location.latitude,
            location.longitude,
            previousLatitude,
            previousLongitude
        ).first

        Log.d("DistanceCheck", "Distance: $distance meters")

//        val xxx = "RanT"
        if (abs(distance) < 5.0) {
            val myLocation = ReferencePoint(userName, location.latitude, location.longitude)
            firestoreManager.writeLocation(myLocation) { success ->
                if (success) Log.d("Main", "Location saved!")
            }
            Log.d("Firestore", "Document write: $userName, Latitude: ${location.latitude}, Longitude: ${location.longitude}")

        }
        previousLatitude = location.latitude
        previousLongitude = location.longitude

        val results = referencePoints.map { point ->
            val (distance, bearing) = calculateDistanceAndBearing(
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

    //    fun showPointsList(results: List<NavigationResult>) {
//        val tvPoints = findViewById<TextView>(R.id.tvPoints)
//        tvPoints.textSize = 24f  // <- increase this value for bigger fonts
//
//        val sb = StringBuilder()
//        for (point in results) {
//            sb.append(getString(R.string.point_info, point.point.name, point.distance))
//            sb.append("\n")
//        }
//        tvPoints.text = sb.toString()
//
//    }
    fun showPointsList(results: List<NavigationResult>) {

        val pointsContainer = findViewById<LinearLayout>(R.id.pointsContainer)
        pointsContainer.removeAllViews()

        for ((index, point) in results.withIndex()) {
            val tv = TextView(this)
            tv.textSize = 24f
            tv.setTypeface(null, Typeface.BOLD) // 👈 makes the text bold
            tv.text = getString(R.string.point_info, point.point.name, point.distance)
            tv.setPadding(16, 16, 16, 16)
            // Set background color, cycling through list if more points than colors
            tv.setBackgroundColor(MarkerConfig.colors[index % MarkerConfig.colors.size])
            pointsContainer.addView(tv)
        }
    }
}
