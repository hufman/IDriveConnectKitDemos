package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.Context
import android.content.Intent
import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.CertMangling
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import me.hufman.idriveconnectionkit.android.SecurityService
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationConcrete
import me.hufman.idriveconnectionkit.rhmi.RHMIApplicationEtch
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIState

class CarApp(val context: Context) {

	val TAG = "ScreenMirroringCarApp"

	/**
	 * When the user selects the app in the car, check to see if we have record permission
	 * If so, begin recording, or prompt the user again
	 */
	fun onCarappFocus() {
		Data.carappFocused = true
		Log.i(TAG, "User selected carapp of mirroring")
		startScreenMirroringService()
	}

	/**
	 * When the user exits the app, stop mirroring
	 */
	fun onCarappBlur() {
		Log.i(TAG, "User left carapp of mirroring")
		Data.carappFocused = false
		stopScreenMirroringService()
	}

	fun startScreenMirroringService() {
		val startIntent = Intent(context, MainService::class.java)
		startIntent.action = MainService.ACTION_START
		context.startService(startIntent)
	}

	fun stopScreenMirroringService() {
		val stopIntent = Intent(context, MainService::class.java)
		stopIntent.action = MainService.ACTION_STOP
		context.startService(stopIntent)
	}

	fun reportError(message: String) {
		Log.e(TAG, message)
		context.sendBroadcast(Intent(MainActivity.REPORT_ERROR).putExtra("EXTRA_MESSAGE", message).setClass(context, MainActivity::class.java))
	}

	fun reportError(e: Exception) {
		Log.e(TAG, e.message, e)
		context.sendBroadcast(Intent(MainActivity.REPORT_ERROR).putExtra("EXTRA_MESSAGE", e.message).setClass(context, MainActivity::class.java))
	}


	/**
	 * Implements a callback for the car to update the app with data
	 * Saves any incoming data to the carData hash and updates the view
	 */
	inner class HmiEventListener : BaseBMWRemotingClient() {
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

	fun createCarApp() {
		if (Data.mirroringApp != null) {
			Log.i(TAG, "App is already running in the car")
			return
		}
		try {
			val app = CarAPIDiscovery.discoveredApps.values.firstOrNull { it.getUiDescription(context) != null }
			if (app == null) {
				reportError("Could not locate Spotify app")
				return
			}
			val certInputStream = app.getAppCertificate(context)
			if (certInputStream == null) {
				reportError("Failed to load app cert from CarAPI app " + app.title)
				return
			}
			val cert = CertMangling.mergeBMWCert(Utils.loadInputStream(certInputStream), SecurityService.fetchBMWCerts())
			Log.i(TAG, "Loaded cert from " + app.id + " of length " + cert.size)
			val uilayout = app.getUiDescription(context)?.readEntireStream()
			val iconpack = app.getImagesDB(context, "common")?.readEntireStream()
			if (uilayout == null || iconpack == null) {
				reportError("Failed to load app resources from CarAPI app " + app.title)
				return
			}

			if (!IDriveConnectionListener.isConnected || IDriveConnectionListener.host == null || IDriveConnectionListener.port == null) {
				reportError("Not connected to car")
				return
			}

			RHMIApplicationConcrete().loadFromXML(uilayout)

			// connect and log in to car
			val listener = HmiEventListener()
			val car = IDriveConnection.getEtchConnection(IDriveConnectionListener.host
					?: "", IDriveConnectionListener.port ?: 8003, listener)
			listener.server = car
			val challenge = car.sas_certificate(cert)
			val response = SecurityService.signChallenge(context.packageName, "IdriveDemo", challenge)
			car.sas_login(response)

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
			var state = rhmiApp.states.values.filterIsInstance<RHMIState.PlainState>().first { it.componentsList.filterIsInstance<RHMIComponent.Image>().isNotEmpty() }
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

			// actually show the app in the car
			Data.carappFocused = false
		} catch (e: Exception) {
			reportError(e)
			Data.carApp = null
		}
	}

}
