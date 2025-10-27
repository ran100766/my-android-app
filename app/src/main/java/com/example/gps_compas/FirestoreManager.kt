package com.example.gps_compas  // <-- use your actual package name

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreManager {
    private val db = FirebaseFirestore.getInstance()

    fun readAllLocations(onResult: (List<ReferencePoint>) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        db.collection("locations")
            .get()
            .addOnSuccessListener { documents ->
                val referencePoints = mutableListOf<ReferencePoint>()

                for (doc in documents) {
                    val name = doc.id
                    val latitude = doc.getDouble("latitude")
                    val longitude = doc.getDouble("longitude")

                    Log.d("Firestore", "Document: $name, Latitude: $latitude, Longitude: $longitude")

                    if (latitude != null && longitude != null) {
                        referencePoints.add(ReferencePoint(name, latitude, longitude))
                    }
                }

                onResult(referencePoints)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error reading locations", e)
                onResult(emptyList())
            }
    }

    fun writeLocation(point: ReferencePoint, onComplete: (Boolean) -> Unit) {
        val data = mapOf(
            "latitude" to point.lat,
            "longitude" to point.lon
        )

        db.collection("locations")
            .document(point.name)
            .set(data)
            .addOnSuccessListener {
                Log.d("Firestore", "Wrote location: ${point.name}")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error writing location", e)
                onComplete(false)
            }
    }

    fun deleteLocation(userName: String, onComplete: (Boolean) -> Unit) {

        val db = FirebaseFirestore.getInstance()

        db.collection("locations").document(userName)
            .delete()
            .addOnSuccessListener {
                Log.d("Firestore", "Document successfully deleted!")
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error deleting document", e)
            }
    }


}
