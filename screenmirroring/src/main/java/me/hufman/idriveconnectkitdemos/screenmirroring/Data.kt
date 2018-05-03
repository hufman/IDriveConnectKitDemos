package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.Intent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIState

object Data {
	var projectionPermission: Intent? = null
	var mirroringApp: RHMIApplication? = null
	var mirroringWindow: RHMIState? = null
	var mirroringAppImage: RHMIComponent.Image? = null
}