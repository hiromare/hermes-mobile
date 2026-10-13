package com.m57.hermescontrol.data.local

/** Gated sessions survive WS ticket expiry when their encrypted cookie is still present. */
internal fun hasLocalSession(
    gated: Boolean,
    token: String?,
    sessionCookie: String?,
): Boolean = if (gated) !sessionCookie.isNullOrBlank() else !token.isNullOrBlank()
