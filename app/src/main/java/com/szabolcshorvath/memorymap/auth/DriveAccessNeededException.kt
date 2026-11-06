package com.szabolcshorvath.memorymap.auth

import android.content.IntentSender
import java.io.IOException

/**
 * Drive access for the account isn't (or is no longer) allowed: only the user can fix it, on [consent], Google's
 * consent screen, when there is one (otherwise the app asks for access as usual). An [IOException], like every other
 * Drive failure, so the backup code handles it in the same place.
 */
class DriveAccessNeededException(val consent: IntentSender?, cause: Throwable? = null) :
    IOException("Drive access has to be allowed", cause)

/** Play services couldn't authorize Drive access for another reason than the connection or the user's consent. */
class DriveAuthorizationFailedException(cause: Throwable) : IOException("Drive access could not be authorized", cause)
