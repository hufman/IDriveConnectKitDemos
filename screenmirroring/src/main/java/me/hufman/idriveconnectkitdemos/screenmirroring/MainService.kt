package me.hufman.idriveconnectkitdemos.screenmirroring

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.IBinder
import android.util.Log
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout


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
		if (Data.projectionPermission != null) {
			Log.i(TAG, "Creating virtual display")
			val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
			val projection = projectionManager.getMediaProjection(RESULT_OK, Data.projectionPermission)

			val imageCapture = ImageReader.newInstance(720, 440, 1, 2)
			val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
			val virtualDisplay = projection.createVirtualDisplay("idrive-screen-mirror", imageCapture.width, imageCapture.height, 140, flags, imageCapture.surface, null, null)
			synchronized(MainService::class.java) {
				Log.i(TAG, "Starting mirroring thread, using VirtualDisplay $virtualDisplay")
				this.projection = projection
				this.imageCapture = imageCapture
				this.virtualDisplay = virtualDisplay
				startNotification()
				startThread()
			}
		} else {
			Log.e(TAG, "Screen mirroring permission not granted")
			Data.projectionPermission = null
		}
	}

	/**
	 * Handle action Baz in the provided background thread with the provided
	 * parameters.
	 */
	private fun handleActionStop() {
		Log.i(TAG, "Shutting down mirroring service")
		synchronized(MainService::class.java) {
			stopThread()
			stopNotification()
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
			val imageCapture = this.imageCapture
			var imageModel = Data.mirroringAppImage?.getModel()?.asRaImageModel()
			if (imageCapture != null && imageModel != null) {
				backgroundThread = ScreenMirroringThread(imageCapture, imageModel)
				Thread(backgroundThread).start()
			} else {
				Log.e(TAG, "Error loading imageCapture and carappImage components")
			}
		}
	}

	private fun stopNotification() {
		stopForeground(true)
	}

	private fun stopThread() {
		if (backgroundThread != null) {
			backgroundThread?.connected = false
		}
		backgroundThread = null

		projection?.stop()

	}

	private var orientationChanger: LinearLayout? = null

	/**
	private fun disableRotation() {
		if (Settings.canDrawOverlays(this)) {
			orientationChanger = LinearLayout(this)
			val orientationLayout = WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.RGBA_8888)
			orientationLayout.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

			val windowManager = getSystemService(Service.WINDOW_SERVICE) as WindowManager
			windowManager.addView(orientationChanger, orientationLayout)
			orientationChanger?.visibility = View.VISIBLE
		}
	}

	private fun restoreRotation() {
		if (orientationChanger != null) {
			val windowManager = getSystemService(Service.WINDOW_SERVICE) as WindowManager
			windowManager.removeView(orientationChanger)
			orientationChanger = null
		}
	}
	 */
}
