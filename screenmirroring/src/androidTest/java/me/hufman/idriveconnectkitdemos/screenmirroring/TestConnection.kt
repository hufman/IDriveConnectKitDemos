package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.Intent
import android.support.test.InstrumentationRegistry
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.Until

import com.bmwgroup.connected.car.app.BrandType
import de.bmw.idrive.BMWRemotingClient
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.CarAPIClient
import me.hufman.idriveconnectionkit.android.CarAPIDiscovery
import me.hufman.idriveconnectionkit.android.IDriveConnectionListener
import org.awaitility.Awaitility.await
import org.awaitility.Duration

import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Rule
import org.mockito.ArgumentMatchers
import java.util.concurrent.TimeUnit

import org.mockito.Mockito.*
import java.util.concurrent.CountDownLatch
import android.support.test.uiautomator.UiSelector
import android.support.test.uiautomator.Until.findObject
import me.hufman.idriveconnectionkit.android.SecurityService


// perhaps follow example in https://android.googlesource.com/platform/cts/+/master/tests/tests/view/src/android/view/cts/surfacevalidator/CapturedActivity.java
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
		assertEquals("me.hufman.screenmirroring", appContext.packageName)
	}

	@Rule
	@JvmField
	val activityMatcher = ActivityTestRule(MainActivity::class.java)

	@After
	fun tearDown() {
		CarAPIDiscovery.cancelDiscovery(activityMatcher.activity)
		CarAPIDiscovery.broadcastReceiver = null
		CarAPIDiscovery.callback = null
		CarAPIDiscovery.discoveredApps.clear()
		IDriveConnectionListener.reset()
		val appContext = InstrumentationRegistry.getTargetContext()
		SecurityService.securityConnections.values.forEach {
			try {
				appContext.unbindService(it)
			} catch (e: Exception) {}
		}
		val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
		uiDevice.pressBack()
	}

	@Test
	fun testNoConnection() {
		/* Test that we don't connect if we don't have a CarAPI app */
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

		mockServer.waitForLogin.await(20, TimeUnit.SECONDS)
		// check that it didn't try to connect
		verify(mockServer, never()).sas_certificate(ArgumentMatchers.any(ByteArray::class.java))
		verify(mockServer, never()).sas_login(ArgumentMatchers.any(ByteArray::class.java))
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
		/* Test that we connect to the car */
		/* For some reason, SecurityService objects to being run multiple times in the same process
		   and so we have one big giant test :/
		*/
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()
		val mockServer = spy(MockBMWRemotingServer())
		IDriveConnection.mockRemotingServer = mockServer

		// add a mock CarAPI
		CarAPIDiscovery.discoveredApps["me.hufman.idriveconnectkitdemos.test_app"] = MockCarApiApp()

		// start the app
		val intent = Intent("com.bmwgroup.connected.accessory.ACTION_CAR_ACCESSORY_ATTACHED")
		intent.putExtra("EXTRA_BRAND", "mini")
		intent.putExtra("EXTRA_HOST", "127.0.0.1")
		intent.putExtra("EXTRA_PORT", 9999)
		intent.putExtra("EXTRA_INSTANCE_ID ", 16)
		appContext.sendBroadcast(intent)

		// check that it tried to connect
		mockServer.waitForLogin.await(20, TimeUnit.SECONDS)
		verify(mockServer).sas_certificate(ArgumentMatchers.any(ByteArray::class.java))
		verify(mockServer).sas_login(ArgumentMatchers.any(ByteArray::class.java))
		val mockClient = IDriveConnection.mockRemotingClient as BMWRemotingClient

		// check for the permission dialog
		val ACCEPT_RESOURCE_ID = "android:id/button1"
		val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
		val acceptButton = uiDevice.wait(Until.findObject(By.res(ACCEPT_RESOURCE_ID)), 20000)
		assertNotNull("should find Screen Recording permission button", acceptButton)
		acceptButton.click()
		await().atMost(Duration.TEN_SECONDS).until { Data.projectionPermission != null }

		// what happens if the main activity gets recreated? it shouldn't reconnect
		val mockServer2 = spy(MockBMWRemotingServer())
		IDriveConnection.mockRemotingServer = mockServer2
		IDriveConnection.mockRemotingServer
		activityMatcher.finishActivity()
		activityMatcher.launchActivity(null)
		mockServer2.waitForLogin.await(5, TimeUnit.SECONDS)
		assertEquals("should not make a new connection", mockClient, IDriveConnection.mockRemotingClient)

		// then the user "navigates" to the screen mirroring app in the car
		mockServer.waitForApp.await(10, TimeUnit.SECONDS)
		assertFalse("should believe the carapp is not focused to start with", Data.carappFocused)
		mockClient.rhmi_onHmiEvent(1, "test app", 40, 11, mapOf(23.toByte() to true))   // app became visible in the car
		assertTrue("should notice that the carapp is now focused", Data.carappFocused)

		// check for the Screen Mirroring notification to show up
		uiDevice.openNotification()
		uiDevice.wait(Until.hasObject(By.text("IDrive Demo")), 15000)
		val title = uiDevice.findObject(By.text("IDrive Demo"))
		assertNotNull("should show IDrive Demo notification", title)

		// test that some frames have been sent
		await().atMost(Duration.TEN_SECONDS).until { mockServer.numFrames > 0 }

		// user hides app
		mockClient.rhmi_onHmiEvent(1, "test app", 40, 11, mapOf(23.toByte() to false))   // app became visible in the car
		assertFalse("should notice that the carapp is now unfocused", Data.carappFocused)
		uiDevice.wait(Until.gone(By.text("IDrive Demo")), 10000)
		val pausedFrameCount = mockServer.numFrames
		Thread.sleep(5000)
		assertEquals("No new mirror frames were sent", pausedFrameCount, mockServer.numFrames)

		// now try showing the app again, it shouldn't reprompt
		mockClient.rhmi_onHmiEvent(1, "test app", 40, 11, mapOf(23.toByte() to true))   // app became visible in the car
		assertTrue("should notice that the carapp is now focused again", Data.carappFocused)

		// check for the Screen Mirroring notification to show up
		uiDevice.openNotification()
		uiDevice.wait(Until.hasObject(By.text("IDrive Demo")), 15000)
		val title2 = uiDevice.findObject(By.text("IDrive Demo"))
		assertNotNull("should show IDrive Demo notification", title2)
		// test that some frames have been sent
		await().atMost(Duration.TEN_SECONDS).until { mockServer.numFrames > pausedFrameCount }
		// now the car has been turned off
		mockServer.disconnect()
		// make sure everything is cleaned up
		uiDevice.wait(Until.gone(By.text("IDrive Demo")), 10000)
		await().atMost(30, TimeUnit.SECONDS).until {Data.carApp == null }
	}
}