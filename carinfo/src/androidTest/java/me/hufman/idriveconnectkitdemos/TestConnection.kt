package me.hufman.idriveconnectkitdemos

import android.content.Intent
import android.support.test.InstrumentationRegistry
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.widget.TextView
import com.bmwgroup.connected.car.app.BrandType
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAPIClient
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.After

import org.junit.Assert.*
import org.mockito.ArgumentMatchers

import org.mockito.Mockito.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class TestConnection {
	@Test
	fun useAppContext() {
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()
		assertEquals("me.hufman.idriveconnectkitdemos", appContext.packageName)
	}

	@Rule
	@JvmField
	val activityMatcher = ActivityTestRule(MainActivity::class.java, true, true)

	@After
	fun tearDown() {
		CarAPIDiscovery.cancelDiscovery(activityMatcher.activity)
		CarAPIDiscovery.broadcastReceiver = null
		CarAPIDiscovery.callback = null
	}

	@Test
	fun testCarApiListener() {
		/* Test that the CarAPI listener is registered */
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()

		// override the callback to watch for our test app
		val waitingForConnection = CountDownLatch(1)
		val origCallback = CarAPIDiscovery.callback
		CarAPIDiscovery.callback = object : CarAPIDiscovery.DiscoveryCallback {
			override fun discovered(app: CarAPIClient) {
				if (app.id == "me.hufman.idriveconnectkitdemos.test_app")
					waitingForConnection.countDown()
				origCallback?.discovered(app)
			}
		}

		// announce our fake app
		val fakeApp = Intent("com.bmwgroup.connected.app.action.ACTION_CAR_APPLICATION_REGISTERING")
		fakeApp.putExtra("EXTRA_APPLICATION_ID", "me.hufman.idriveconnectkitdemos.test_app")
		fakeApp.putExtra("EXTRA_APPLICATION_TITLE", "Fake App")
		fakeApp.putExtra("EXTRA_APPLICATION_CATEGORY", "Multimedia")
		fakeApp.putExtra("EXTRA_APPLICATION_VERSION", "v3")
		fakeApp.putExtra("EXTRA_APPLICATION_BRAND", BrandType.ALL)
		fakeApp.putExtra("EXTRA_APPLICATION_CONNECT_RECEIVER_ACTION", "receiver.action")
		fakeApp.putExtra("EXTRA_APPLICATION_DISCONNECT_RECEIVER_ACTION", "receiver.action2")
		fakeApp.putExtra("EXTRA_APPLICATION_APP_ICON", ByteArray(1))
		appContext.sendBroadcast(fakeApp)

		// wait for our fake app to be noticed
		waitingForConnection.await(20, TimeUnit.SECONDS)
		// verify that it was found
		assertTrue(CarAPIDiscovery.discoveredApps.keys.contains("me.hufman.idriveconnectkitdemos.test_app"))
	}

	@Test
	fun testConnection() {
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()
		val mockServer = spy(MockBMWRemotingServer())
		IDriveConnection.mockRemotingServer = mockServer

		val activity = activityMatcher.activity
		val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_ATTACHED")
		intent.putExtra("EXTRA_BRAND", "mini")
		intent.putExtra("EXTRA_HOST", "127.0.0.1")
		intent.putExtra("EXTRA_PORT", 9999)
		intent.putExtra("EXTRA_INSTANCE_ID ", 16)
		appContext.sendBroadcast(intent)

		mockServer.waitForLogin.await(60, TimeUnit.SECONDS)
		// check that it was connected
		verify(mockServer).sas_certificate(ArgumentMatchers.any(ByteArray::class.java))
		verify(mockServer).sas_login(ArgumentMatchers.any(ByteArray::class.java))

		// check for cds listeners
		mockServer.expected_cdsEventHandlers.await(60, TimeUnit.SECONDS)
		assertEquals(4, mockServer.cdsEventHandlers.size)
		verify(mockServer).cds_addPropertyChangedEventHandler(eq(1), eq("vehicle.VIN"), eq("83"), ArgumentMatchers.anyInt())
		verify(mockServer).cds_addPropertyChangedEventHandler(eq(1), eq("driving.gear"), eq("37"), ArgumentMatchers.anyInt())
		verify(mockServer).cds_addPropertyChangedEventHandler(eq(1), eq("driving.odometer"), eq("39"), ArgumentMatchers.anyInt())
		verify(mockServer).cds_addPropertyChangedEventHandler(eq(1), eq("engine.RPMSpeed"), eq("50"), ArgumentMatchers.anyInt())

		// test the cds listeners
		val mockClient = IDriveConnection.mockRemotingClient
		assertNotNull(mockClient)
		if (mockClient == null) return // tell Kotlin that this object is not null
		mockClient.cds_onPropertyChangedEvent(1, "83", "vehicle.VIN", """{"VIN":"012345"}""")
		mockClient.cds_onPropertyChangedEvent(1, "37", "driving.gear", """{"gear":"0"}""")
		mockClient.cds_onPropertyChangedEvent(1, "39", "driving.odometer", """{"odometer":"1001"}""")
		mockClient.cds_onPropertyChangedEvent(1, "50", "engine.RPMSpeed", """{"RPMSpeed":"1000"}""")
		// wait for handlers to finish, by adding to the end of the UI Thread runnables
		activity.runOnUiThread { assertEquals("\nVIN: 012345\nOdometer: 1001 KM\nGear: 0\nRPM: 1000", activity.findViewById<TextView>(R.id.carData).text) }

	}
}
