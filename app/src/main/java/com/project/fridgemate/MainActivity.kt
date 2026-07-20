package com.project.fridgemate

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.project.fridgemate.data.repository.UserRepository
import com.project.fridgemate.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(applicationContext)

        // Get FCM Token and send to server
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM_TOKEN", "Device FCM Token: $token")

                // Send to server
                lifecycleScope.launch {
                    val result = userRepository.registerFcmToken(token)
                    if (result.isSuccess) {
                        Log.d("FCM_TOKEN", "Token registered successfully")
                    } else {
                        Log.e("FCM_TOKEN", "Failed to register token: ${result.exceptionOrNull()?.message}")
                    }
                }
            } else {
                Log.e("FCM_TOKEN", "Failed to get FCM token: ${task.exception?.message}")
            }
        }
    }
}