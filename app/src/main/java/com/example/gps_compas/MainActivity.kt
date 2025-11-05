package com.example.gpscompass

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import com.example.gps_compas.AzimuthMarkerView
import com.example.gps_compas.MarkerConfig
import com.example.gps_compas.Marker
import android.view.animation.Animation
import com.google.firebase.FirebaseApp
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.example.gps_compas.FirestoreManager
import com.example.gps_compas.ReferencePoint
import com.example.gps_compas.askUserName
import com.example.gpscompass.LocationService.Companion.latestLocation

class MainActivity : AppCompatActivity() {

    companion object {
        val noName = "No_Name"
        var userName: String = noName
    }

    private var fullLocationsList: List<NavigationResult> = emptyList()

    private var currentDegree = 0f  // <-- declare here

    private lateinit var tvSpeed: TextView
    private lateinit var tvDirection: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView

    private val visibleLines = mutableListOf<String>() // 👈 store currently visible lines

    private val uiUpdateHandler = Handler(Looper.getMainLooper())

    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            // Read latest location from service
            val location = LocationService.latestLocation
            location?.let {
                updateUI(location)
            }

            // Schedule next update in 2 seconds
            uiUpdateHandler.postDelayed(this, 5000)
        }
    }

    data class NavigationResult(
        var point: ReferencePoint,
        var distance: Float,
        var bearing: Float,
        var atPoint: Boolean = false,
        var index: Int = 0
    )

    private var referencePoints: MutableList<ReferencePoint> = mutableListOf(
//        ReferencePoint("Jerusalem", 31.7795, 35.2339),
//        ReferencePoint("Home", 32.17062, 34.83878),
//        ReferencePoint("Marina", 32.16580, 34.79267)
    )

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineGranted || coarseGranted) {
                // ✅ Permission granted → start the location service
                startLocationService()
            } else {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
            }
        }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    // 🧩 helper function to update visible lines
    private fun updateVisibleLines(scrollView: ScrollView, container: LinearLayout) {
        val scrollY = scrollView.scrollY
        val scrollHeight = scrollView.height

        visibleLines.clear()

        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val childTop = child.top
            val childBottom = child.bottom

            if (childBottom > scrollY && childTop < scrollY + scrollHeight) {
                val tv = child as TextView
                visibleLines.add(tv.text.toString())
            }
        }

        // Example: show in log or use in other parts of app
        Log.d("VisibleLines", "Currently visible: $visibleLines")

        showPointsOnCompas(fullLocationsList, latestLocation)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // must match activity_main.xml


        tvSpeed = findViewById(R.id.tvSpeed)
        tvDirection = findViewById(R.id.tvDirection)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)

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

        locationPermissionRequest.launch(locationPermissions)
        requestIgnoreBatteryOptimizations()


        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        val pointsContainer = findViewById<LinearLayout>(R.id.pointsContainer)

// Add a scroll listener
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            updateVisibleLines(scrollView, pointsContainer)
        }
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

        fullLocationsList = referencePoints.map { point ->
            val (distance, bearing) = CalculateDistance.calculateDistanceAndBearing(
                location.latitude,
                location.longitude,
                point.lat,
                point.lon
            )
            NavigationResult(point, distance, bearing, distance < 10F)
        }.sortedBy { it.distance }
            .onEachIndexed { index, result ->
                result.index = index
            }

        showCompasArrow(fullLocationsList, location)
        showPointsOnList(fullLocationsList)
        showPointsOnCompas(fullLocationsList, location)

    }

    private fun showCompasArrow(fullLocationsList: List<NavigationResult>, location: Location)
    {
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


            for (r in fullLocationsList) {
                r.bearing = r.bearing - location.bearing
                if (r.bearing < 0) {
                    r.bearing += 360f
                }
            }

        }
    }

    private fun showPointsOnCompas(fullLocationsList: List<NavigationResult>, location: Location)
    {
        val markerView = findViewById<AzimuthMarkerView>(R.id.azimuthMarker)

        val visibleLocationsList = if (visibleLines.isEmpty()) {
            // If no lines are visible yet, take the first 5 from the full list
            fullLocationsList
                .sortedBy { it.distance }
                .take(5)
                .map { r ->
                    Marker(
                        azimuth = r.bearing,
                        color = MarkerConfig.colors[r.index % MarkerConfig.colors.size],
                        radius = 100f,
                        drawAtCenter = r.atPoint,
                        distance = r.distance.toInt()
                    )
                }
        } else {
            // Otherwise, filter by visible lines
            fullLocationsList
                .filter { r ->
                    visibleLines.any { line -> line.contains(r.point.name.take(14)) }
                }
                .sortedBy { it.distance }
                .map { r ->
                    Marker(
                        azimuth = r.bearing,
                        color = MarkerConfig.colors[r.index % MarkerConfig.colors.size],
                        radius = 100f,
                        drawAtCenter = r.atPoint,
                        distance = r.distance.toInt()
                    )
                }
        }

        markerView.setMarkers(visibleLocationsList)
    }

    override fun onStart() {
        super.onStart()
        uiUpdateHandler.post(uiUpdateRunnable) // start periodic updates
    }

    override fun onStop() {
        super.onStop()
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable) // stop updates when activity stops
    }

    fun showPointsOnList(fullLocationsList: List<NavigationResult>) {

        val pointsContainer = findViewById<LinearLayout>(R.id.pointsContainer)
        pointsContainer.removeAllViews()

        for ( point in fullLocationsList) {
//            val sdf = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.getDefault())
//            val lastUpdateStr = point.point.lastUpdate?.let { sdf.format(it) } ?: "N/A"
            val tv = TextView(this)
            tv.textSize = 20f
            tv.setTypeface(null, Typeface.BOLD) // 👈 makes the text bold


            // Fixed-width columns
            val text =
                String.format("%-14s %-7d", point.point.name.take(14), point.distance.toInt())
            tv.text = text
            tv.typeface = Typeface.MONOSPACE // ensures columns align


            tv.setPadding(16, 16, 16, 16)
            // Set background color, cycling through list if more points than colors
            tv.setBackgroundColor(MarkerConfig.colors[point.index % MarkerConfig.colors.size])
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
