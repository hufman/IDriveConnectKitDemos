package me.hufman.idriveconnectkitdemos.audiotesting

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import me.hufman.idriveconnectionkit.android.CarAPIClient
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService

class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)

		fab.setOnClickListener {
			combinedCallback()
		}

		txtConnectionId.setOnKeyListener { view, i, keyEvent ->
			if (keyEvent.action == ACTION_DOWN &&
					i == KEYCODE_ENTER) {
				combinedCallback()
				true
			} else {
				false
			}
		}

		// discover helper apps
		CarAPIDiscovery.discoverApps(this,  object : CarAPIDiscovery.DiscoveryCallback {
			override fun discovered(app: CarAPIClient) {
				combinedCallback()
			}
		})
		SecurityService.listener = object: Runnable {
			override fun run() {
				combinedCallback()
			}
		}
		SecurityService.connect(this)
		// wait for car connection
		IDriveConnectionListener.callback = Runnable {
			val id = IDriveConnectionListener.instanceId
			if (id != null) {
				val textField = findViewById<EditText>(R.id.txtConnectionId).text
				textField.replace(0, textField.length, id.toString())
			}
			combinedCallback()
		}
		IDriveConnectionListener.callback?.run()
	}

	/**
	 * Check whether the connection requirements are met, and then connect to car
	 */
	fun combinedCallback() {
		if (CarAPIDiscovery.discoveredApps.isEmpty()) {
			findViewById<TextView>(R.id.txtStatus).text = "No BMW Connected Apps installed, please install Spotify or iHeartRadio"
			return
		}
		if (! IDriveConnectionListener.isConnected) {
			findViewById<TextView>(R.id.txtStatus).text = "Not connected"
			return
		}

		findViewById<TextView>(R.id.txtStatus).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
				"Connecting to BMW or Mini Connected app"

		if (SecurityService.isConnected()) {
			findViewById<TextView>(R.id.txtStatus).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
					"Using security services of " + SecurityService.activeSecurityConnections.keys.firstOrNull()
		}

		if (CarAPIDiscovery.discoveredApps.isNotEmpty() &&
				IDriveConnectionListener.isConnected && SecurityService.isConnected()) {
			val startIntent = Intent(applicationContext, MainService::class.java)
			startIntent.action = MainService.ACTION_START
			val id = findViewById<EditText>(R.id.txtConnectionId).text.toString().toIntOrNull() ?: 0
			startIntent.putExtra(MainService.EXTRAS_CONNECTION_ID, id)
			startService(startIntent)
		} else {
			Snackbar.make(findViewById(R.id.txtStatus), "Car is not connected yet", Snackbar.LENGTH_SHORT)
		}
	}
}
