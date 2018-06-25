package me.hufman.idriveconnectkitdemos.screenmirroring

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log


class PermissionDialog : Activity() {

	companion object {
		const val REQUEST_RECORDING = "me.hufman.idriveconnectkitdemos.screenmirroring.REQUEST_RECORDING"
	}

	private val TAG = "IdriveScreenMirrorDlg"
	private val PROJECTION_PERMISSION_CODE = 0x0535

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.i(TAG, "Starting Screen Mirroring Permission Dialog")
		// if we were starting to request screen mirroring permission, just do that
		if (intent?.action == REQUEST_RECORDING) {
			promptForMirroringPermission()
			return
		}
	}

	fun promptForMirroringPermission() {
		val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
		if (Data.projectionPermission != null) {
			Log.w(TAG, "Should we prompt again, if we already have permission?")
		}
		if (projectionManager != null && Data.projectionPermission == null) {
			Log.i(TAG, "Showing screenshot access dialog")
			startActivityForResult(projectionManager.createScreenCaptureIntent(), PROJECTION_PERMISSION_CODE)
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (requestCode == PROJECTION_PERMISSION_CODE) {
			Log.i(TAG, "Received response from screenshot dialog")
			if (resultCode == Activity.RESULT_OK && data != null) {
				Log.i(TAG, "User granted screenshot access main dialog")
				Data.projectionPermission = data.clone() as Intent

				// now start the capture service
				startScreenMirroringService()
				finish()
			} else {
				Log.i(TAG, "User did not grant screenshot access: resultCode=$resultCode")
			}
		}
	}

	fun startScreenMirroringService() {
		val startIntent = Intent(applicationContext, MainService::class.java)
		startIntent.action = MainService.ACTION_START
		startService(startIntent)
	}

}