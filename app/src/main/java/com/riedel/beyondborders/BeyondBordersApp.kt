package com.riedel.beyondborders

import android.app.Application

class BeyondBordersApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
