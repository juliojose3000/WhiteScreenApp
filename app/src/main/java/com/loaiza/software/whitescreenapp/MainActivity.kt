package com.loaiza.software.whitescreenapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Remove de shadow from action bar:
        supportActionBar?.elevation = 0F

    }
}