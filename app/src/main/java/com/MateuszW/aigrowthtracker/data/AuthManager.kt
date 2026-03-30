package com.MateuszW.aigrowthtracker.data

import com.google.firebase.auth.FirebaseAuth

object AuthManager {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun getUserId(): String? {
        return auth.currentUser?.uid
    }
}