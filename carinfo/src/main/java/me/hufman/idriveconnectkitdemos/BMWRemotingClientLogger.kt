package me.hufman.idriveconnectkitdemos

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingClient
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient

open class BMWRemotingClientLogger : BaseBMWRemotingClient() {
	private val TAG = "BMWRemotingClientLogger"

	var server: BMWRemotingServer? = null

	@Throws(BMWRemoting.IllegalArgumentException::class, BMWRemoting.ServiceException::class, BMWRemoting.SecurityException::class)
	override fun rhmi_onActionEvent(handle: Int?, ident: String, actionId: Int?, args: Map<*, *>) {
		Log.w(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
		server?.rhmi_ackActionEvent(handle, actionId, 1, true)

	}

	@Throws(BMWRemoting.IllegalArgumentException::class, BMWRemoting.ServiceException::class, BMWRemoting.SecurityException::class)
	override fun rhmi_onHmiEvent(handle: Int?, ident: String, componentId: Int?, eventId: Int?, args: Map<*, *>) {
		Log.w(TAG, "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId")
	}

	override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
		Log.w(TAG, "Received cds_onPropertyChangedEvent: handle=$handle propertyName=$propertyName propertyValue=$propertyValue")
	}

	override fun am_onAppEvent(handle: Int?, ident: String, appId: Int?, event: BMWRemoting.AMEvent) {
		Log.w(TAG, "Received am_onAppEvent: handle=$handle ident=$ident appId=$appId event=$event")
	}

}