package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.res.Resources
import android.graphics.*
import android.media.Image
import android.media.ImageReader
import android.util.Log
import me.hufman.idriveconnectionkit.rhmi.RHMIModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore

class ScreenMirroringThread(val source: ImageReader, val carImage: RHMIModel.RaImageModel) : Runnable {
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

		val paint = Paint()
		paint.color = Color.argb(0x60, 0xcc, 0xcc, 0xff)
		paint.style = Paint.Style.STROKE
		paint.strokeWidth = 5.0F
		val selectedPaint = Paint()
		selectedPaint.color = Color.argb(0xc0, 0x88, 0xff, 0x88)
		selectedPaint.style = Paint.Style.FILL_AND_STROKE
		selectedPaint.strokeWidth = 10.0F

		var bmp: Bitmap? = null
		var lastImage: Image? = null
		var lastInputFocus = 0
		while (connected) {
			//ready.acquire()  // block until we're ready to show another frame
			if (!connected) continue

			// iamge algorithm from https://binwaheed.blogspot.com/2015/03/how-to-correctly-take-screenshot-using.html
			val image = source.acquireLatestImage() ?: lastImage
			if (image == null) {
				// first image, not received yet
				Thread.sleep(50)
				continue
			}
			if (image === lastImage && lastInputFocus == Data.getInputFocus()) {
				// no screen update, and no focus change
				Thread.sleep(50)
				continue
			}
			if (image != lastImage) {
				lastImage?.close()
			}

			//Log.i(TAG, "Image received from screen mirror!")
			val planes = image.planes
			val buffer = planes[0].buffer
			val padding = planes[0].rowStride - planes[0].pixelStride * source.width
			val width = source.width + padding / planes[0].pixelStride
			if (bmp == null || bmp.width != width || bmp.height != source.height)
				bmp = Bitmap.createBitmap(width, source.height, getBitmapConfig(source.imageFormat))
			buffer.rewind()
			bmp!!.copyPixelsFromBuffer(buffer)

			// decorate with highlight boxes
			val canvas = Canvas(bmp)
			val inputWidgets = Data.getInputWidgets()
			val inputFocus = Data.getInputFocus()
			val rect = Rect()
			val screenWidth = Resources.getSystem().displayMetrics.widthPixels
			val screenHeight = Resources.getSystem().displayMetrics.heightPixels
			inputWidgets.forEachIndexed { index,it ->
				it.getBoundsInScreen(rect)
				val coloredRect = transformRect(rect, 720.0 / screenWidth, 440.0 / screenHeight)
				if (index == inputFocus)
					canvas.drawRect(coloredRect, selectedPaint)
				else
					canvas.drawRect(coloredRect, paint)
			}

			setPicture(bmp)
			//if (!paused) ready.release()    // immediately show another frame
			lastImage = image
			lastInputFocus = inputFocus
		}
	}

	fun transformRect(rect: Rect, xRatio:Double, yRatio:Double): Rect {
		return Rect((rect.left * xRatio).toInt(), (rect.top * yRatio).toInt(), (rect.right * xRatio).toInt(), (rect.bottom * yRatio).toInt())
	}

	fun setPicture(bmp: Bitmap) {
		jpg.reset()
		bmp.compress(Bitmap.CompressFormat.JPEG, 90, jpg)
		carImage.value = jpg.toByteArray()
	}
}