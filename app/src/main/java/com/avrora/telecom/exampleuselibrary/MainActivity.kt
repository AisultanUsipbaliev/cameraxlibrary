package com.avrora.telecom.exampleuselibrary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.github.AisultanUsipbaliev

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, BorderView::class.java)
        startActivity(intent)

        finish()
    }
}