package pt.ismai.farol

import android.app.Application

class FarolApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
