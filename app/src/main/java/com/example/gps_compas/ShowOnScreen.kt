package com.example.gps_compas

import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Typeface
import android.location.Location
import android.util.Log
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.gpscompass.LocationService.Companion.latestLocation
import com.example.gpscompass.MainActivity.NavigationResult
import com.example.gpscompass.R

    private val visibleLines = mutableListOf<String>() // 👈 store currently visible lines

    private var currentDegree = 0f  // <-- declare here
private var smoothedAzimuth = 0f
private const val ALPHA = 0.5f // smaller = smoother (0.05–0.2 typical)

/**
 * Smoothly rotates the compass background or arrow according to azimuth.
 */
fun showCompasArrow(
    activity: Activity,
    fullLocationsList: List<NavigationResult>,
    azimuth: Float,
    tvDirection: TextView
) {
    val arrowStatic = false

    // ✅ Smooth azimuth with a low-pass filter
    smoothedAzimuth = smoothedAzimuth + ALPHA * ((azimuth - smoothedAzimuth + 540) % 360 - 180)

    // Normalize to 0–360
    if (smoothedAzimuth < 0) smoothedAzimuth += 360f
    if (smoothedAzimuth >= 360f) smoothedAzimuth -= 360f

    // ✅ Update direction text
    tvDirection.text = "Direction: %.0f°".format(smoothedAzimuth)

    if (arrowStatic) {
        // 🧭 If we rotate an arrow instead of the background
        val arrow = activity.findViewById<ImageView>(R.id.directionArrow)

        arrow.animate()
            .rotation(-smoothedAzimuth)
            .setDuration(100)
            .setInterpolator(LinearInterpolator())
            .start()

    } else {
        val compassBackground = activity.findViewById<ImageView>(R.id.compassBackground)

        // ✅ Calculate shortest rotation path
        val delta = ((azimuth - currentDegree + 540) % 360) - 180
        val targetRotation = currentDegree + delta

        compassBackground.animate()
            .rotation(-targetRotation)
            .setDuration(150)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        currentDegree = targetRotation
    }
}

private val previousBearings = mutableMapOf<String, Float>()
private val smoothedBearings = mutableMapOf<String, Float>()
private const val MARKER_ALPHA = 0.2f // lower = smoother

fun showPointsOnCompas(
    activity: Activity,
    fullLocationsList: List<NavigationResult>,
    azimuth: Float
) {
    val markerView = activity.findViewById<AzimuthMarkerView>(R.id.azimuthMarker)

    val visibleLocationsList = if (visibleLines.isEmpty()) {
        fullLocationsList
            .sortedBy { it.distance }
            .take(5)
    } else {
        fullLocationsList
            .filter { r ->
                visibleLines.any { line -> line.contains(r.point.name.take(14)) }
            }
            .sortedBy { it.distance }
    }

    val markerList = visibleLocationsList.map { r ->
        var bearingToDisplay = (r.bearing - azimuth + 360f) % 360f

        // --- Smooth bearing ---
        val key = r.point.name
        val lastBearing = smoothedBearings[key] ?: bearingToDisplay
        val delta = ((bearingToDisplay - lastBearing + 540f) % 360f) - 180f // shortest path
        val smoothed = (lastBearing + MARKER_ALPHA * delta + 360f) % 360f
        smoothedBearings[key] = smoothed

        Marker(
            azimuth = smoothed,
            color = MarkerConfig.colors[r.index % MarkerConfig.colors.size],
            radius = 100f,
            drawAtCenter = r.atPoint,
            distance = r.distance.toInt()
        )
    }

    // ✅ Update marker view
    markerView.setMarkers(markerList)
}



    fun showPointsOnList(activity: Activity,fullLocationsList: List<NavigationResult>) {

        val pointsContainer = activity.findViewById<LinearLayout>(R.id.pointsContainer)
        pointsContainer.removeAllViews()

        for ( point in fullLocationsList) {
//            val sdf = SimpleDateFormat("dd:MM:yyyy HH:mm:ss", Locale.getDefault())
//            val lastUpdateStr = point.point.lastUpdate?.let { sdf.format(it) } ?: "N/A"
            val tv = TextView(activity)
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


    // 🧩 helper function to update visible lines
    fun updateVisibleLines(activity: Activity,scrollView: ScrollView, container: LinearLayout, fullLocationsList: List<NavigationResult>) {
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

    }


