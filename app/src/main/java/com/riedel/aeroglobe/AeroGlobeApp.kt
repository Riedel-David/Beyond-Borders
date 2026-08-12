package com.riedel.aeroglobe

import android.app.Application

class AeroGlobeApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
