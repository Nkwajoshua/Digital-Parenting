package com.digitalparenting.ui

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class BlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TextView(this).apply {
            text = "App Blocked 🚫\nTime limit reached"
            textSize = 24f
            gravity = Gravity.CENTER
        }

        setContentView(view)
    }

    override fun onBackPressed() {
        // Prevent exit - user cannot back out of block screen
    }
}
