package me.hufman.idriveconnectkitdemos.screenmirroring

import android.accessibilityservice.AccessibilityService
import android.app.Service
import android.content.Intent
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

class MirrorAccessibilityService: AccessibilityService() {
	val TAG = "MirrorAccessibility"

	companion object {
		val ACTION = "me.hufman.idriveconnectkitdemos.screenmirroring.MirrorAccessibilityService"
	}

	override fun onInterrupt() {
		TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		Log.i(TAG, "Received intent ${intent?.action} with command ${intent?.getStringExtra("COMMAND")}")
		if (intent?.action == ACTION) {
			Log.i(TAG, "Clicking button!")
			val inputWidgets = Data.getInputWidgets()
			val inputWidget = inputWidgets[Data.inputFocus]
			inputWidget?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id)
			return Service.START_NOT_STICKY
		} else {
			return super.onStartCommand(intent, flags, startId)
		}
	}

	override fun onAccessibilityEvent(event: AccessibilityEvent?) {
		Log.i(TAG, "Received accessibility event ${event?.eventType}: ${event?.contentDescription}")
		val node = event?.source
		if (node == null) {Log.w(TAG, "Could not find node for event"); return}
		val window = node?.window
		val root = rootInActiveWindow
		if (root == null) {Log.w(TAG, "Could not find window for event"); return }
		val inputNodes = collectAllNodes(root)
		Data.setInputWidgets(inputNodes)
		inputNodes.forEach {
			val rect = Rect()
			it.getBoundsInScreen(rect)
			Log.i(TAG, "Found input node ${it.className}:${it.text} at $rect")
		}
	}

	private fun collectAllNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
		val list = LinkedList<AccessibilityNodeInfo>()
		for (i in 0 until node.childCount) {
			val child = node.getChild(i)
			if (child == null) continue
			if (child.childCount > 0) list.addAll(collectAllNodes(child))

			if (child.isClickable) list.add(child)
			else child.recycle()
		}
		return list
	}
}