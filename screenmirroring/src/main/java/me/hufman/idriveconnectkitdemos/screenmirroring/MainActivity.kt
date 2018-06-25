package me.hufman.idriveconnectkitdemos.screenmirroring

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.*
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIState


class MainActivity : Activity() {

	companion object {
		const val REPORT_ERROR = "me.hufman.idriveconnectkitdemos.screenmirroring.REPORT_ERROR"
	}

	private val TAG = "IdriveScreenMirror"

	private val messageReceiver = object:BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent != null && intent.getStringExtra("EXTRA_MESSAGE") != null)
				reportError(intent.getStringExtra("EXTRA_MESSAGE"))
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		// listen for GUI messages
		registerReceiver(messageReceiver, IntentFilter().apply({ addAction(REPORT_ERROR) }))

		// wait for car connection
		IDriveConnectionListener.callback = Runnable {
			if (IDriveConnectionListener.isConnected) {
				findViewById<TextView>(R.id.textView).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
						"Connecting to BMW or Mini Connected app"
				SecurityService.connect(this)
				SecurityService.subscribe(Runnable {
					findViewById<TextView>(R.id.textView).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
							"Using security services of " + SecurityService.activeSecurityConnections.keys.firstOrNull()
					combinedCallback()
				})
			} else {
				findViewById<TextView>(R.id.textView).text = "Not connected"
				Data.mirroringApp = null
				Data.mirroringAppImage = null
				Data.carApp = null
			}
			combinedCallback()
		}
		IDriveConnectionListener.callback?.run()

		// update the discovered list of CarAPI apps
		val discoveryCallback = object : CarAPIDiscovery.DiscoveryCallback {
			override fun discovered(app: CarAPIClient) {
				redraw()
				combinedCallback()
			}

			fun redraw() {
				val app = CarAPIDiscovery.discoveredApps.values.firstOrNull { it.getUiDescription(this@MainActivity) != null }
				val output: String
				if (app == null) {
					output = "Spotify is required to be installed for this demo app!"
				} else {
					output = "Spotify is installed and ready"
				}
				findViewById<TextView>(R.id.carapiList).text = output
			}
		}
		discoveryCallback.redraw()
		CarAPIDiscovery.discoverApps(this, discoveryCallback)
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(messageReceiver)
		CarAPIDiscovery.cancelDiscovery(this)
		SecurityService.listener = Runnable {}
	}

	/**
	 * Check whether the connection requirements are met, and then connect to car
	 */
	fun combinedCallback() {
		if (CarAPIDiscovery.discoveredApps.values.any { it.getUiDescription(this) != null} &&
				IDriveConnectionListener.isConnected && SecurityService.isConnected())
			startScreenMirroringService()
	}

	fun startScreenMirroringService() {
		val startIntent = Intent(applicationContext, MainService::class.java)
		startIntent.action = MainService.ACTION_START
		startService(startIntent)
	}

	fun stopScreenMirroringService() {
		val stopIntent = Intent(applicationContext, MainService::class.java)
		stopIntent.action = MainService.ACTION_STOP
		startService(stopIntent)
	}

	fun reportError(message: String) {
		runOnUiThread({
			Log.e(TAG, message)
			findViewById<TextView>(R.id.carData).text = message
		})
	}
}
