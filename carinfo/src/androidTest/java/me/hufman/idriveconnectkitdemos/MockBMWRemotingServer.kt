package me.hufman.idriveconnectkitdemos

import de.bmw.idrive.BaseBMWRemotingServer
import java.util.*
import java.util.concurrent.CountDownLatch

open class MockBMWRemotingServer: BaseBMWRemotingServer() {
	val waitForLogin = CountDownLatch(1)
	val cdsEventHandlers = LinkedList<String>()
	val expected_cdsEventHandlers = CountDownLatch(4)
	override fun sas_certificate(data: ByteArray?): ByteArray {
		return ByteArray(16)
	}

	override fun sas_login(data: ByteArray?) {
		waitForLogin.countDown()
	}

	override fun cds_create(): Int {
		return 1
	}

	override fun cds_addPropertyChangedEventHandler(handle: Int?, propertyName: String?, ident: String?, intervalLimit: Int?) {
		if (propertyName != null) {
			cdsEventHandlers.add(propertyName)
			expected_cdsEventHandlers.countDown()
		}
	}

	override fun cds_getPropertyAsync(handle: Int?, ident: String?, propertyName: String?) {

	}
}