package com.rothrockware.studyjazzstandards.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.jsonPrimitive

/**
 * The web app stores topNote heterogeneously: seed voicings write JSON numbers
 * (7, 9, ...) while others write strings ("♭9"). Decode any primitive to its
 * string content; encode all-digit values back as JSON numbers so a round-trip
 * through this app leaves web-produced JSON byte-identical.
 */
object TopNoteSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("TopNote", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.jsonPrimitive.isString) return primitive.content
        if (primitive.content == "null") return null
        return primitive.content
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        val jsonEncoder = encoder as? JsonEncoder
        if (jsonEncoder != null && value.isNotEmpty() && value.all { it.isDigit() }) {
            jsonEncoder.encodeJsonElement(JsonUnquotedLiteral(value))
        } else {
            encoder.encodeString(value)
        }
    }
}
