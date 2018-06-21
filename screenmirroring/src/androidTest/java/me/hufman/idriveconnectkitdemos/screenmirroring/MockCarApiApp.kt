package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.Context
import android.content.res.AssetFileDescriptor
import com.bmwgroup.connected.car.app.BrandType
import me.hufman.idriveconnectionkit.android.CarAPIClient
import java.io.ByteArrayInputStream
import java.io.InputStream

class MockCarApiApp: CarAPIClient(id="me.hufman.idriveconnectkitdemos.test_app",
		title="Fake App",
		category="Multimedia",
		version="v3",
		brandType=BrandType.ALL,
		connectIntentName="receiver.action",
		disconnectIntentName="receiver.action2",
		appIcon=ByteArray(2)) {
	override fun getAppCertificate(context: Context): InputStream? {
		return ByteArrayInputStream(ByteArray(0))
	}

	override fun getUiDescription(context: Context): InputStream? {
		val description =
			"<pluginApps><pluginApp>" +
			"<actions>" +
			"<raAction id=\"2\"/>" +
			"<combinedAction id=\"3\" sync=\"true\" actionType=\"spellWord\">" +
			"    <actions>" +
			"        <raAction id=\"4\"/>" +
			"        <hmiAction id=\"5\" targetModel=\"6\"/>" +
			"    </actions>" +
			"</combinedAction>" +
			"</actions>" +
			"<models>" +
			"<imageIdModel id=\"4\" imageId=\"15\"/>" +
			"<raImageModel id=\"62\"/>" +
			"<raDataModel id=\"6\"/>" +
			"<raDataModel id=\"7\"/>" +
			"<raDataModel id=\"8\"/>" +
			"</models>" +
			"<hmiStates>" +
			"<toolbarHmiState id=\"40\" textModel=\"7\">" +
			"<toolbarComponents>" +
			"<button id=\"41\" action=\"3\" selectAction=\"2\" tooltipModel=\"8\" imageModel=\"4\" />" +
			"</toolbarComponents>" +
			"<components>" +
			"<image id=\"50\" model=\"62\"/>" +
			"</components>" +
			"</toolbarHmiState>" +
			"<hmiState id=\"40\" textModel=\"7\">" +
			"<components>" +
			"<image id=\"51\" model=\"62\"/>" +
			"</components>" +
			"</hmiState>" +
			"</hmiStates>" +
			"</pluginApp></pluginApps>"
		return ByteArrayInputStream(description.toByteArray())
	}

	override fun getImagesDB(context: Context, brand: String): InputStream? {
		return ByteArrayInputStream(ByteArray(1))
	}

	override fun getTextsDB(context: Context, brand: String): InputStream? {
		return ByteArrayInputStream(ByteArray(2))
	}
}