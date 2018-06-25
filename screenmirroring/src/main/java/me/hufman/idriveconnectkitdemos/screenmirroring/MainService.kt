package me.hufman.idriveconnectkitdemos.screenmirroring

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.os.IBinder
import android.util.Log
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager


class MainService : Service() {

	companion object {
		const val ACTION_START = "me.hufman.screenmirroring.action.START"
		const val ACTION_STOP = "me.hufman.screenmirroring.action.STOP"
	}

	private val TAG = "IdriveScreenMirror"
	private val ONGOING_NOTIFICATION_ID = 0x0045

	private var backgroundThread: ScreenMirroringThread? = null
	private var foregroundNotification: Notification? = null
	private var projection: MediaProjection? = null
	private var imageCapture: ImageReader? = null
	private var virtualDisplay: VirtualDisplay? = null

	override fun onBind(intent: Intent?): IBinder? {
		return null
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val action = intent?.action ?: ""
		if (ACTION_START == action) {
			handleActionStart()
		} else if (ACTION_STOP == action) {
			handleActionStop()
		} else {
			Log.e(TAG, "Unknown service action: $action")
		}
		return Service.START_STICKY
	}

	/**
	 * Start the service
	 */
	private fun handleActionStart() {
		if (Data.carApp == null) {
			Log.i(TAG, "Initializing screenmirroring car app")
			val carApp = CarApp(this)
			Data.carApp = carApp
			carApp.createCarApp()
		}
		if (Data.projectionPermission != null) {
			Log.i(TAG, "Still have screenmirroring permission from before")
			if (Data.carappFocused) {
				Log.i(TAG, "Carapp is focused, beginning to send data")
				startNotification()
				startThread()
			} else {
				Log.e(TAG, "Carapp is not focused, not starting to send data")
			}
		} else {
			Log.e(TAG, "Screen mirroring permission not granted")
			promptForMirroringPermission()
		}
	}

	/**
	 * Stop the service
	 */
	private fun handleActionStop() {
		Log.i(TAG, "Shutting down mirroring service")
		synchronized(MainService::class.java) {
			stopThread()
			stopNotification()
		}
	}

	fun promptForMirroringPermission() {
		if (Data.projectionPermission != null) {
			Log.w(TAG, "Should we prompt again, if we already have permission?")
		} else {
			this.startActivity(Intent(this, PermissionDialog::class.java).setAction(PermissionDialog.REQUEST_RECORDING).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
		}
	}

	private fun startNotification() {
		Log.i(TAG, "Creating foreground notification")
		foregroundNotification = Notification.Builder(this)
				.setOngoing(true)
				.setContentTitle(getText(R.string.notification_title))
				.setContentText(getText(R.string.notification_description))
				.setSmallIcon(android.R.drawable.ic_media_play)
				.build()
		startForeground(ONGOING_NOTIFICATION_ID, foregroundNotification)
	}

	private fun startThread() {
		if (backgroundThread == null) {
			Log.i(TAG, "Creating virtual display")
			val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			val projection = projectionManager.getMediaProjection(RESULT_OK, Data.projectionPermission)
			this.projection = projection

			val imageCapture = ImageReader.newInstance(720, 440, 1, 2)
			val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
			val virtualDisplay = projection.createVirtualDisplay("idrive-screen-mirror", imageCapture.width, imageCapture.height, 140, flags, imageCapture.surface, null, null)

			var imageModel = Data.mirroringAppImage?.getModel()?.asRaImageModel()
			if (imageCapture != null && imageModel != null) {
				Log.i(TAG, "Starting mirroring thread, using VirtualDisplay $virtualDisplay")
				this.backgroundThread = ScreenMirroringThread(this, imageCapture, imageModel)
				Thread(backgroundThread).start()
			} else {
				Log.e(TAG, "Error loading imageCapture and carappImage components")
			}
		}
	}

	private fun stopNotification() {
		Log.i(TAG, "Hiding foreground notification")
		stopForeground(true)
	}

	private fun stopThread() {
		Log.i(TAG, "Shutting down screenmirroring thread")
		if (backgroundThread != null) {
			backgroundThread?.connected = false
		}
		backgroundThread = null

		projection?.stop()
	}
}
