package com.codingwithmitch.openapi.session

import android.app.Application
import com.codingwithmitch.openapi.persistence.AuthTokenDao

class SessionManager
constructor(
    val autTokenDao: AuthTokenDao,
    val application: Application
)
{

}
