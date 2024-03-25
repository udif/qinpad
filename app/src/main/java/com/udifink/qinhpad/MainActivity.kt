package com.udifink.qinhpad

import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

class MainActivity : AppCompatActivity() {
    private val TAG = "QinHPad"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Starting...")
        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        finish()
    }
}
