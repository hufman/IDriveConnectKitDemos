package me.hufman.idriveconnectkitdemos.screenmirroring

import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BaseBMWRemotingServer
import java.util.concurrent.CountDownLatch

open class MockBMWRemotingServer: BaseBMWRemotingServer() {
	val waitForLogin = CountDownLatch(1)
	val waitForApp = CountDownLatch(1)
	var numFrames = 0

	override fun sas_certificate(data: ByteArray?): ByteArray {
		return ByteArray(16)
	}

	override fun sas_login(data: ByteArray?) {
		waitForLogin.countDown()
	}

	override fun rhmi_create(token: String?, metaData: BMWRemoting.RHMIMetaData?): Int {
		return 1
	}

	override fun rhmi_initialize(handle: Int?) {
		waitForApp.countDown()
	}

	override fun rhmi_setResource(handle: Int?, data: ByteArray?, type: BMWRemoting.RHMIResourceType?) {

	}

	override fun rhmi_addActionEventHandler(handle: Int?, ident: String?, actionId: Int?) {

	}

	override fun rhmi_addHmiEventHandler(handle: Int?, ident: String?, componentId: Int?, eventId: Int?) {

	}

	override fun rhmi_ackActionEvent(handle: Int?, actionId: Int?, confirmId: Int?, success: Boolean?) {

	}

	override fun rhmi_setData(handle: Int?, modelId: Int?, value: Any?) {
		if (modelId == 62)
			numFrames += 1
	}

	override fun rhmi_setProperty(handle: Int?, componentId: Int?, propertyId: Int?, values: MutableMap<*, *>?) {

	}

	override fun rhmi_triggerEvent(handle: Int?, eventId: Int?, args: MutableMap<*, *>?) {

	}
}