package com.MateuszW.aigrowthtracker.data

import com.google.firebase.firestore.FirebaseFirestore

object FirebaseRepository {
    val db = FirebaseFirestore.getInstance()
}