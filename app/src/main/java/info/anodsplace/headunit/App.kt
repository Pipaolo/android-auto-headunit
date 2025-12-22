package info.anodsplace.headunit

import android.app.Application
import android.content.Context
import info.anodsplace.headunit.utils.AppLog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.conscrypt.Conscrypt
import java.io.File
import java.security.Security

class App : Application() {

    private val component: AppComponent by lazy {
        AppComponent(this)
    }

    override fun onCreate() {
        super.onCreate()

        // Install Conscrypt as the first security provider for modern TLS support
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
            AppLog.i { "Conscrypt security provider installed successfully" }
        } catch (e: Exception) {
            AppLog.e { "Failed to install Conscrypt: ${e.message}" }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(AapBroadcastReceiver(), AapBroadcastReceiver.filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(AapBroadcastReceiver(), AapBroadcastReceiver.filter)
        }
    }

    companion object {
        const val defaultChannel = "default"

        fun get(context: Context): App {
            return context.applicationContext as App
        }
        fun provide(context: Context): AppComponent {
            return get(context).component
        }
    }
}
