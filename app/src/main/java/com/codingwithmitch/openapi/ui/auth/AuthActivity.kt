package com.codingwithmitch.openapi.ui.auth

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.codingwithmitch.openapi.R
import com.codingwithmitch.openapi.ui.BaseActivity

class AuthActivity : BaseActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
    }
}