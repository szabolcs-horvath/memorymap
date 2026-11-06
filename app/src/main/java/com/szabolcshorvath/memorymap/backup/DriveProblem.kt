package com.szabolcshorvath.memorymap.backup

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.szabolcshorvath.memorymap.auth.DriveAccessNeededException
import com.szabolcshorvath.memorymap.auth.DriveAuthorizationFailedException
import java.io.IOException

/** What went wrong in a call to Google Drive, sorted by what the user (or a retry) can do about it. */
enum class DriveProblem {
    /** Drive access was withdrawn or has to be confirmed again: only the user can fix it, by allowing it. */
    NEEDS_ACCESS,

    /** No connection, a timeout or a dropped connection. */
    NO_CONNECTION,

    /** Drive's servers are failing or asking for fewer requests. */
    DRIVE_UNAVAILABLE,

    /** Anything else: Drive rejected the request, or the app failed. Trying again fails the same way. */
    OTHER;

    /** Whether trying again later can work without the user doing anything. */
    val isRetryable: Boolean get() = this == NO_CONNECTION || this == DRIVE_UNAVAILABLE

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500

        // The more specific IOExceptions first: the app's own access errors and Drive's errors are IOExceptions too.
        fun of(e: Throwable): DriveProblem = when (e) {
            is DriveAccessNeededException -> NEEDS_ACCESS
            is DriveAuthorizationFailedException -> OTHER
            is GoogleJsonResponseException -> when {
                e.statusCode == HTTP_UNAUTHORIZED -> NEEDS_ACCESS
                e.statusCode == HTTP_TOO_MANY_REQUESTS || e.statusCode >= HTTP_SERVER_ERROR -> DRIVE_UNAVAILABLE
                else -> OTHER
            }
            is IOException -> NO_CONNECTION
            else -> OTHER
        }
    }
}
