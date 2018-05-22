package me.hufman.idriveconnectkitdemos.screenmirroring

import android.app.Activity
import android.content.Context
import android.content.Intent
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

	private val TAG = "IdriveScreenMirror"
	private val PROJECTION_PERMISSION_CODE = 0x0535

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		// wait for car connection
		IDriveConnectionListener.callback = Runnable {
			if (IDriveConnectionListener.isConnected) {
				findViewById<TextView>(R.id.textView).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
						"Connecting to BMW or Mini Connected app"
				SecurityService.connect(this)
				SecurityService.subscribe(Runnable {
					findViewById<TextView>(R.id.textView).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
							"Using security services of " + SecurityService.activeSecurityConnections.keys.firstOrNull()
				})
			} else {
				findViewById<TextView>(R.id.textView).text = "Not connected"
				Data.mirroringApp = null
				Data.mirroringAppImage = null
			}
			combinedCallback()
		}
		IDriveConnectionListener.callback?.run()

		// update the discovered list of CarAPI apps
		val discoveryCallback = object: CarAPIDiscovery.DiscoveryCallback {
			override fun discovered(app: CarAPIClient) {
				redraw()
				combinedCallback()
			}
			fun redraw() {
				val apps = CarAPIDiscovery.discoveredApps.values.map {
					it.title
				}
				val output: String
				if (!CarAPIDiscovery.discoveredApps.containsKey("com.spotify.music")) {
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

	/**
	 * Check whether the connection requirements are met, and then connect to car
	 */
	fun combinedCallback() {
		if (CarAPIDiscovery.discoveredApps.containsKey("com.spotify.music") &&
				IDriveConnectionListener.isConnected)
			InitCarApp().execute()
	}

	/**
	 * Implements a callback for the car to update the app with data
	 * Saves any incoming data to the carData hash and updates the view
	 */
	inner class HmiEventListener: BaseBMWRemotingClient() {
		var server: BMWRemotingServer? = null
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			server?.rhmi_ackActionEvent(handle, actionId, 1, true)
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			Log.w(TAG, "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId")
			if (componentId == Data.mirroringWindow?.id && eventId == 11 && args?.get(23.toByte()) == true) {
				onCarappFocus()
			}
			if (componentId == Data.mirroringWindow?.id && eventId == 11 && args?.get(23.toByte()) == false) {
				onCarappBlur()
			}
		}
	}

	/**
	 * When the user selects the app in the car, check to see if we have record permission
	 * If so, begin recording, or prompt the user again
	 */
	fun onCarappFocus() {
		Data.carappFocused = true
		Log.i(TAG, "User selected carapp of mirroring")
		if (Data.mirroringApp != null && Data.projectionPermission == null) {
			Log.i(TAG, "User must grant mirroring permission on the phone first")
			promptForMirroringPermission()
		} else if (Data.mirroringApp != null && Data.projectionPermission != null) {
			Log.i(TAG, "Still have permission from earlier, start back up")
			startScreenMirroringService()
		}
	}

	/**
	 * When the user exits the app, stop mirroring
	 */
	fun onCarappBlur() {
		Log.i(TAG, "User left carapp of mirroring")
		Data.carappFocused = false
		stopScreenMirroringService()
	}

	fun promptForMirroringPermission() {
		val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
		if (Data.projectionPermission != null) {
			Log.w(TAG, "Should we prompt again, if we already have permission?")
		}
		if (projectionManager != null && Data.projectionPermission == null) {
			startActivityForResult(projectionManager.createScreenCaptureIntent(), PROJECTION_PERMISSION_CODE)
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
		if (requestCode == PROJECTION_PERMISSION_CODE) {
			if (resultCode == Activity.RESULT_OK) {
				Log.i(TAG, "User granted screenshot access main dialog")
				Data.projectionPermission = data.clone() as Intent

				// now start the capture service
				if (Data.carappFocused) {
					Log.i(TAG, "Since we are focused in the car, start mirroring now")
					startScreenMirroringService()
				}
			} else {
				Log.i(TAG, "User did not grant screenshot access")
			}
		}
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

	fun reportError(e: Exception) {
		runOnUiThread({
			Log.e(TAG, e.message, e)
			findViewById<TextView>(R.id.carData).text = e.message
		})
	}

	/**
	 * Running as a separate thread, connect to the car to make sure it works
	 */
	inner class InitCarApp: AsyncTask<Unit, Void, Unit>() {
		override fun doInBackground(vararg p0: Unit?) {
			val context = this@MainActivity

			if (Data.mirroringApp != null) {
				Log.i(TAG, "App is already running in the car")
				return
			}
			try {
				val app = CarAPIDiscovery.discoveredApps["com.spotify.music"]
				if (app == null) {
					reportError("Could not locate Spotify app")
					return
				}
				val certInputStream = app.getAppCertificate(context)?.createInputStream()
				if (certInputStream == null) {
					reportError("Failed to load app cert from CarAPI app " + app.title)
					return
				}
				val cert = CertMangling.mergeBMWCert(Utils.loadInputStream(certInputStream), SecurityService.fetchBMWCerts())
				Log.i(TAG, "Loaded cert from " + app.id +" of length " + cert.size)
				val uilayout = app.getUiDescription(context)?.createInputStream()?.readEntireStream()
				val iconpack = app.getImagesDB(context, "common")?.createInputStream()?.readEntireStream()
				if (uilayout == null || iconpack == null) {
					reportError("Failed to load app resources from CarAPI app " + app.title)
					return
				}

				if (!IDriveConnectionListener.isConnected || IDriveConnectionListener.host == null || IDriveConnectionListener.port == null) {
					reportError("Not connected to car")
					return
				}

				// connect and log in to car
				val listener = HmiEventListener()
				val car = IDriveConnection.getEtchConnection(IDriveConnectionListener.host
						?: "", IDriveConnectionListener.port ?: 8003, listener)
				listener.server = car
				val challenge = car.sas_certificate(cert)
				val response = SecurityService.signChallenge(context.packageName, "IdriveDemo", challenge)
				car.sas_login(response)

				// the rest of the carapp setup will probably work, so
				// prompt user to grant screen mirroring permission
				runOnUiThread({
					promptForMirroringPermission()
				})

				// create the car app
				val rhmiHandle = car.rhmi_create(null, BMWRemoting.RHMIMetaData(context.packageName, BMWRemoting.VersionInfo(0, 0, 1), context.packageName, "BMW Group"))
				car.rhmi_addHmiEventHandler(rhmiHandle, "Callback method", -1, -1)
				car.rhmi_addActionEventHandler(rhmiHandle, "Callback method", -1)
				car.rhmi_setResource(rhmiHandle, uilayout, BMWRemoting.RHMIResourceType.DESCRIPTION)
				car.rhmi_setResource(rhmiHandle, iconpack, BMWRemoting.RHMIResourceType.IMAGEDB)
				car.rhmi_initialize(rhmiHandle)

				// set up the RHMI App
				val rhmiApp = RHMIApplicationEtch(car, rhmiHandle)
				rhmiApp.loadFromXML(uilayout)
				// find a state with an image, which seems to be #12
				var state = rhmiApp.states.values.filterIsInstance<RHMIState.PlainState>().first {it.componentsList.filterIsInstance<RHMIComponent.Image>().isNotEmpty()}
				rhmiApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
					it.getAction()?.asCombinedAction()?.hmiAction?.getTargetModel()?.asRaIntModel()?.value = state.id
				}
				state.componentsList.forEach {
					it.setVisible(false)
				}
				var imageComponent = state.componentsList.filterIsInstance<RHMIComponent.Image>().first()
				imageComponent.setProperty(9, 720)  // width
				imageComponent.setProperty(10, 480) // height
				imageComponent.setVisible(true)

				// save the app to signal it is connected and ready
				Data.mirroringApp = rhmiApp
				Data.mirroringWindow = state
				Data.mirroringAppImage = imageComponent

			} catch (e: Exception) {
				reportError(e)
			}
		}
	}
}