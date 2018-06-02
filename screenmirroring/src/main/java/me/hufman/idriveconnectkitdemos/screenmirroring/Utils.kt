package me.hufman.idriveconnectkitdemos.screenmirroring

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

fun InputStream.readEntireStream(maxSize: Long = 5000000): ByteArray {
	return Utils.loadInputStream(this, maxSize)
}

object Utils {
	private val TAG = "IdriveDemos"

	fun loadInputStream(input: InputStream, maxSize: Long = 5000000): ByteArray {
		val writer = ByteArrayOutputStream()
		val data = ByteArray(4096)
		try {
			var read = input.read(data, 0, data.size)
			while (read > -1 && (maxSize == 0L || writer.size() < maxSize)) {
				writer.write(data, 0, read)
				read = input.read(data, 0, data.size)
			}
			return writer.toByteArray()
		} catch (e: IOException) {
			Log.e(TAG, "Failed to load resource")
			return ByteArray(0)
		}

	}
}