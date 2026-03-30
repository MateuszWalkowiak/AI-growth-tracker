package com.MateuszW.aigrowthtracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.MateuszW.aigrowthtracker.data.AuthManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun LoginScreen() {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLogin by remember { mutableStateOf(true) }

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(if (isLogin) "Logowanie" else "Rejestracja")

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = {
                email = it
            },
            label = { Text("Email") }
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
            },
            label = { Text("Hasło") }
        )

        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            if (isLogin) {
                AuthManager.auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        println("LOGIN SUCCESS")
                    }
                    .addOnFailureListener {
                        println("LOGIN ERROR: ${it.message}")
                    }
            } else {
                AuthManager.auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        println("REGISTER SUCCESS")
                    }
                    .addOnFailureListener {
                        println("REGISTER ERROR: ${it.message}")
                    }
            }
        }) {
            Text(if (isLogin) "Zaloguj" else "Zarejestruj")
        }

        TextButton(onClick = {
            isLogin = !isLogin
        }) {
            Text(if (isLogin) "Nie masz konta? Zarejestruj" else "Masz konto? Zaloguj")
        }
    }
}