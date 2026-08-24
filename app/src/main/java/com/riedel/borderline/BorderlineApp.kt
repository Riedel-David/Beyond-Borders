package com.riedel.borderline

import android.app.Application

class BorderlineApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
