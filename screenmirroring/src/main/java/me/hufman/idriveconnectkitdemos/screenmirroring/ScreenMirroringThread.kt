package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.util.Log
import me.hufman.idriveconnectionkit.rhmi.RHMIModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore

class ScreenMirroringThread(val context: Context, val source: ImageReader, val carImage: RHMIModel.RaImageModel) : Runnable {
	var paused = false
		set(value) {
			field = value
			if (value) ready.release()
		}
	var connected = true
		set(value) {
			field = value
			if (!value) ready.release()
		}

	val TAG = "ScreenMirroringThread"
	private val ready = Semaphore(if (paused) 0 else 1)
	private val jpg = ByteArrayOutputStream()

	private fun getBitmapConfig(imageFormat: Int): Bitmap.Config? {
		return when (imageFormat) {
			ImageFormat.RGB_565 -> Bitmap.Config.RGB_565
			ImageFormat.FLEX_RGBA_8888 -> Bitmap.Config.ARGB_8888
			1 -> Bitmap.Config.ARGB_8888
			ImageFormat.FLEX_RGB_888 -> Bitmap.Config.ARGB_8888
			else -> null
		}
	}

	override fun run() {
		try {
			sendScreen()
		} catch (e: Exception) {
			Log.e(TAG, "Error while sending screen to the car!", e)
			Data.carApp = null
		} finally {
			context.startService(Intent(context, MainService::class.java).setAction(MainService.ACTION_STOP))
		}
	}

	fun sendScreen() {
		var bmp: Bitmap? = null
		while (connected) {
			//ready.acquire()  // block until we're ready to show another frame
			if (!connected) continue

			// iamge algorithm from https://binwaheed.blogspot.com/2015/03/how-to-correctly-take-screenshot-using.html
			val image = source.acquireLatestImage()
			if (image == null) {
				//Log.i(TAG, "Null image received from screen mirror")
				//val bmp = Bitmap.createBitmap(720, 480, Bitmap.Config.RGB_565)
				//bmp.eraseColor(0x552277)
				//setPicture(bmp)
				Thread.sleep(10)
				continue
			}
			//Log.i(TAG, "Image received from screen mirror!")
			val planes = image.planes
			val buffer = planes[0].buffer
			val padding = planes[0].rowStride - planes[0].pixelStride * source.width
			val width = source.width + padding / planes[0].pixelStride
			if (bmp == null || bmp.width != width || bmp.height != source.height)
				bmp = Bitmap.createBitmap(width, source.height, getBitmapConfig(source.imageFormat))
			bmp!!.copyPixelsFromBuffer(buffer)
			image.close()
			setPicture(bmp)
			//if (!paused) ready.release()    // immediately show another frame
		}
	}

	fun setPicture(bmp: Bitmap) {
		jpg.reset()
		bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpg)
		carImage.value = jpg.toByteArray()
	}
}