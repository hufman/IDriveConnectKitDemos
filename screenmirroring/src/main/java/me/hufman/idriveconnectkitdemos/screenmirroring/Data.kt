package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIState

object Data {
	@Volatile var projectionPermission: Intent? = null
	@Volatile var mirroringApp: RHMIApplication? = null
	@Volatile var mirroringWindow: RHMIState? = null
	@Volatile var mirroringAppImage: RHMIComponent.Image? = null
	@Volatile var carappFocused = false
	@Volatile var inputWidgets = ArrayList<AccessibilityNodeInfo>()
	@Volatile @JvmField var inputFocus = 0

	fun setInputWidgets(widgets: List<AccessibilityNodeInfo>) {
		synchronized(Data) {
			inputWidgets.clear()
			inputWidgets.addAll(widgets)
		}
	}

	fun getInputWidgets():List<AccessibilityNodeInfo> {
		synchronized(Data) {
			return ArrayList(inputWidgets)
		}
	}

	fun changeInputFocus(direction: Int) {
		synchronized(Data) {
			inputFocus += direction
			inputFocus = Math.min(inputWidgets.size - 1, inputFocus)
			inputFocus = Math.max(0, inputFocus)
		}
	}

	fun getInputFocus(): Int {
		synchronized(Data) {
			return inputFocus
		}
	}
}