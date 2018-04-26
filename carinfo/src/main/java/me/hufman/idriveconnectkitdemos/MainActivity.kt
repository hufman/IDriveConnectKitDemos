package me.hufman.idriveconnectkitdemos

import android.app.Activity
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import me.hufman.idriveconnectionkit.IDriveConnection
import me.hufman.idriveconnectionkit.android.*
import org.json.JSONException
import org.json.JSONObject

class MainActivity : Activity() {
    private val TAG = "IdriveDemos"
    private val carData = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // update the status label on changes
        IDriveConnectionListener.callback = Runnable {
            if (IDriveConnectionListener.isConnected) {
                findViewById<TextView>(R.id.textView).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
                        "Connecting to BMW or Mini Connected app"
                SecurityService.connect(this)
                SecurityService.subscribe(Runnable {
                    findViewById<TextView>(R.id.textView).text = "Connected to a " + IDriveConnectionListener.brand + "\n" +
                            "Using security services of " + SecurityService.activeSecurityConnections.keys.firstOrNull()
                    GetInfo().execute()
                })
            } else {
                findViewById<TextView>(R.id.textView).text = "Not connected"
                carData.clear()
            }
        }
        if (IDriveConnectionListener.isConnected) IDriveConnectionListener.callback?.run()

        // update the discovered list of CarAPI apps
        val discoveryCallback = object: CarAPIDiscovery.DiscoveryCallback {
            override fun discovered(app: CarAPIClient) {
                val apps = CarAPIDiscovery.discoveredApps.values.map {
                    it.title
                }
                var output = "Found " + apps.size + " BMW Connected Ready apps:\n" + apps.joinToString("\n")
                if (apps.size == 0) {
                    output = "Found 0 BMW Connected Ready apps!\nAt least one BMW Connected Ready app must be installed"
                    output += "\nTry installing Spotify or iHeartRadio from the Play Store"
                }
                findViewById<TextView>(R.id.carapiList).text = output
            }

        }
        CarAPIDiscovery.discoverApps(this, discoveryCallback)
    }

    /**
     * Implements a callback for the car to update the app with data
     * Saves any incoming data to the carData hash and updates the view
     */
    inner class CarDataListener: BMWRemotingClientLogger() {
        override fun cds_onPropertyChangedEvent(handle: Int?, ident: String?, propertyName: String?, propertyValue: String?) {
            super.cds_onPropertyChangedEvent(handle, ident, propertyName, propertyValue)
            if (propertyName != null && propertyValue != null) {
                carData[propertyName] = propertyValue
            }
            redrawData()
        }
    }

    /**
     * Running as a separate thread, connect to the car and fetch data from it
     */
    inner class GetInfo: AsyncTask<Unit, Void, Unit>() {
        override fun doInBackground(vararg p0: Unit?) {
            val context = this@MainActivity

            try {
                val app = CarAPIDiscovery.discoveredApps["com.spotify.music"] ?: CarAPIDiscovery.discoveredApps.values.first()
                val certInputStream = app.getAppCertificate(context)?.createInputStream()
                if (certInputStream == null) {
                    Log.e(TAG, "Failed to load app cert from CarAPI app " + app.title)
                    return
                }
                val cert = CertMangling.mergeBMWCert(Utils.loadInputStream(certInputStream), SecurityService.fetchBMWCerts())
                Log.i(TAG, "Loaded cert from " + app.id +" of length " + cert.size)

                if (!IDriveConnectionListener.isConnected || IDriveConnectionListener.host == null || IDriveConnectionListener.port == null) {
                    Log.e(TAG, "Not connected to car")
                    return
                }

                // connect and log in to car
                val car = IDriveConnection.getEtchConnection(IDriveConnectionListener.host
                        ?: "", IDriveConnectionListener.port ?: 8003, CarDataListener())
                val challenge = car.sas_certificate(cert)
                val response = SecurityService.signChallenge(context.packageName, "IdriveDemo", challenge)
                car.sas_login(response)

                // fetch data from the car
                val cdsHandle = car.cds_create()
                car.cds_addPropertyChangedEventHandler(cdsHandle, "vehicle.VIN", "83", 5000)
                car.cds_addPropertyChangedEventHandler(cdsHandle, "driving.gear", "37", 100)
                car.cds_addPropertyChangedEventHandler(cdsHandle, "driving.odometer", "39", 500)
                car.cds_addPropertyChangedEventHandler(cdsHandle, "engine.RPMSpeed", "50", 100)
                car.cds_getPropertyAsync(cdsHandle, "87", "vehicle.VIN")
                car.cds_getPropertyAsync(cdsHandle, "37", "driving.gear")
                car.cds_getPropertyAsync(cdsHandle, "39", "driving.odometer")
                car.cds_getPropertyAsync(cdsHandle, "50", "engine.RPMSpeed")
            } catch (e: Exception) {
                runOnUiThread({
                    Log.e(TAG, e.toString())
                    findViewById<TextView>(R.id.carData).text = "Failed to connect to car:\n" + e.toString()
                })
            }
        }
    }

    /**
     * Helps load JSON strings into objects, returns null if they are invalid
     */
    fun loadJSON(str: String?): JSONObject? {
        if (str == null) return null
        try {
            return JSONObject(str)
        } catch (e: JSONException) {
            return null
        }
    }

    /**
     * Updates the textview with information about the connected car
     */
    fun redrawData() {
        val vin = loadJSON(carData["vehicle.VIN"])?.getString("VIN")
        val odometer = loadJSON(carData["driving.odometer"])?.getString("odometer")
        val gear = loadJSON(carData["driving.gear"])?.getString("gear")
        val rpm = loadJSON(carData["engine.RPMSpeed"])?.getString("RPMSpeed")
        runOnUiThread({
            findViewById<TextView>(R.id.carData).text = "\nVIN: $vin\nOdometer: $odometer KM\nGear: $gear\nRPM: $rpm"
        })
    }
}
