package me.hufman.idriveconnectkitdemos.audiotesting

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import me.hufman.idriveconnectkitdemos.audiotesting.R
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.*


class MainService : Service() {

	companion object {
		const val ACTION_START = "me.hufman.idriveconnectkitdemos.audiotesting.action.START"
		const val ACTION_STOP = "me.hufman.idriveconnectkitdemos.audiotesting.action.STOP"
		const val EXTRAS_CONNECTION_ID = "me.hufman.idriveconnectkitdemos.audiotesting.EXTRAS.EXTRAS_CONNECTION_ID"
	}

	private val TAG = "IdriveAudioTesting"
	private val ONGOING_NOTIFICATION_ID = 0x1145

	private var foregroundNotification: Notification? = null
	private var desiredConnectionId = 0

	private var etchConnection: BMWRemotingServer? = null
	private var avHandle = -1
	private var avFocused = 0

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val action = intent?.action ?: ""
		if (ACTION_START == action) {
			desiredConnectionId = intent?.getIntExtra(EXTRAS_CONNECTION_ID, 0) ?: 0
			handleActionStart()
		} else if (ACTION_STOP == action) {
			handleActionStop()
		} else {
			Log.e(TAG, "Unknown service action: $action")
		}
		return Service.START_STICKY
	}

	private fun createCarConnection(): BMWRemotingServer {
		// get authentication
		val app = CarAPIDiscovery.discoveredApps.values.first()
		val certInputStream = app.getAppCertificate()
		if (certInputStream == null) {
			Log.e(TAG, "Failed to load app cert from CarAPI app " + (app as? CarAPIClient)?.title)
			throw Exception()
		}
		val cert = CertMangling.mergeBMWCert(Utils.loadInputStream(certInputStream), SecurityService.fetchBMWCerts())
		Log.i(TAG, "Loaded cert from " + ((app as? CarAPIClient)?.id ?: "app") + " of length " + cert.size)

		// connect to the car
		val car = IDriveConnection.getEtchConnection(IDriveConnectionListener.host
				?: "", IDriveConnectionListener.port ?: 8003, AVListener())

		// login to the car
		val challenge = car.sas_certificate(cert)
		val response = SecurityService.signChallenge(this.packageName, "IdriveAudioTesting", challenge)
		car.sas_login(response)

		return car
	}

	/**
	 * Start the service
	 */
	private fun handleActionStart() {
		Thread({
			var car = etchConnection
			if (car == null) {
				Log.i(TAG, "Creating car connection")
				car = createCarConnection()
				etchConnection = car
				startNotification()
				Log.i(TAG, "Created car connection")
			}

			try {
				// tell the car to start listening to audio
				if (avHandle >= 0) {
					car.av_playerStateChanged(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_STOP)
					car.av_dispose(avHandle)
					Thread.sleep(1000)
				}
				Log.i(TAG, "Calling av_create to $desiredConnectionId")
				avHandle = car.av_create(desiredConnectionId, "IdriveAudioTesting_$desiredConnectionId")
				Log.i(TAG, "Calling av_requestConnection to $desiredConnectionId with handle $avHandle")
				car.av_requestConnection(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT)
				car.av_playerStateChanged(avHandle, BMWRemoting.AVConnectionType.AV_CONNECTION_TYPE_ENTERTAINMENT, BMWRemoting.AVPlayerState.AV_PLAYERSTATE_PLAY)
			} catch (e:Exception) {
				Log.e(TAG, "Exception while registering AV handle! $e")
			}
		}).start()
	}

	/**
	 * Stop the service
	 */
	private fun handleActionStop() {
		Log.i(TAG, "Shutting down mirroring service")
		synchronized(MainService::class.java) {
			stopNotification()
		}
	}

	private fun startNotification() {
		var description = "Requesting AV Connection to ID ${desiredConnectionId}"
		if (avHandle >= 0)
			description = "Requesting to listen to ID ${desiredConnectionId} on ${avHandle}"
		if (avFocused == -1)
			description = "AV Rejected to ID ${desiredConnectionId} on ${avHandle}"
		if (avFocused == 1)
			description = "Listening to ID ${desiredConnectionId} on ${avHandle}"
		Log.i(TAG, "Creating foreground notification: $description")

		foregroundNotification = Notification.Builder(this)
				.setOngoing(true)
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(description)
				.setSmallIcon(android.R.drawable.ic_media_play)
				.build()
		startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
	}

	private fun stopNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
	}

	inner class AVListener: BaseBMWRemotingClient() {
		override fun av_connectionDenied(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			Log.i(TAG, "Connection denied to handle $handle")
			avFocused = -1
			startNotification() // update the notification
		}

		override fun av_connectionGranted(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			Log.i(TAG, "Connection granted to handle $handle")
			avFocused = 1
			startNotification() // update the notification
		}

		override fun av_connectionDeactivated(handle: Int?, connectionType: BMWRemoting.AVConnectionType?) {
			Log.i(TAG, "Connection deactivated to handle $handle")
			avFocused = 0
			startNotification()
		}

		override fun av_requestPlayerState(handle: Int?, connectionType: BMWRemoting.AVConnectionType?, playerState: BMWRemoting.AVPlayerState?) {
			Log.i(TAG, "Car requested that we $playerState audio on handle $handle ")
		}
	}
}
