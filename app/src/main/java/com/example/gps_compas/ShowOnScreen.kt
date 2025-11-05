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

    fun showCompasArrow(activity: Activity, fullLocationsList: List<NavigationResult>, location: Location)
    {
        val arrowStatic = false

        if (arrowStatic) {
            val arrow = activity.findViewById<ImageView>(R.id.directionArrow)
            val animator =
                ObjectAnimator.ofFloat(arrow, "rotation", arrow.rotation, location.bearing)
            animator.duration = 300  // milliseconds
            animator.interpolator = LinearInterpolator()
            animator.start()
        } else {
            val compassBackground = activity.findViewById<ImageView>(R.id.compassBackground)

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

    fun showPointsOnCompas(activity: Activity,fullLocationsList: List<NavigationResult>, location: Location)
    {
        val markerView = activity.findViewById<AzimuthMarkerView>(R.id.azimuthMarker)

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
                        distance = (r.distance * 1).toInt()
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
                        distance = (r.distance * 1).toInt()
                    )
                }
        }

        markerView.setMarkers(visibleLocationsList)
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

        showPointsOnCompas(activity, fullLocationsList, latestLocation)

    }


