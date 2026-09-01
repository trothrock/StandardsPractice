package com.rothrockware.studyjazzstandards.data

import kotlinx.serialization.json.Json

/**
 * explicitNulls=false is load-bearing: the web app distinguishes absent keys
 * from null (e.g. `entry.oldLevel !== undefined`), so Kotlin nulls must be
 * omitted from the JSON. encodeDefaults=true keeps fields like `reviews: []`
 * and `streak: 0` present, matching what the web app writes.
 */
val JazzJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Same shape as [JazzJson], but indented for human-readable backup exports. */
val JazzJsonPretty: Json = Json(JazzJson) {
    prettyPrint = true
}
