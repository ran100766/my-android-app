package com.example.gps_compas  // <-- use your actual package name

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreManager {

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
}
