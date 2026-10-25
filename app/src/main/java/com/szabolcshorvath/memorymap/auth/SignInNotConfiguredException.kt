package com.szabolcshorvath.memorymap.auth

/** This build has no OAuth web client ID (`OAUTH_WEB_CLIENT_ID` in `local.properties`), so Google sign-in can't work. */
class SignInNotConfiguredException : IllegalStateException("OAUTH_WEB_CLIENT_ID is not set for this build")
