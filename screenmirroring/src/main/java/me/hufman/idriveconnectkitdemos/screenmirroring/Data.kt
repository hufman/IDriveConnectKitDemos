package me.hufman.idriveconnectkitdemos.screenmirroring

import android.content.Intent
import me.hufman.idriveconnectionkit.rhmi.RHMIApplication
import me.hufman.idriveconnectionkit.rhmi.RHMIComponent
import me.hufman.idriveconnectionkit.rhmi.RHMIState

object Data {
	@Volatile var projectionPermission: Intent? = null
	@Volatile var mirroringApp: RHMIApplication? = null
	@Volatile var mirroringWindow: RHMIState? = null
	@Volatile var mirroringAppImage: RHMIComponent.Image? = null
	@Volatile var carApp: CarApp? = null
	@Volatile var carappFocused = false
}