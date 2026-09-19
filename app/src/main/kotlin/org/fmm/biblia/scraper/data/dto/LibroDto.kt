package org.fmm.biblia.scraper.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.fmm.biblia.scraper.data.dto.TipoTestamento

@Serializable
data class LibroDto (
    val id: Int,
    val orden: Int,
    val esKey: String,
    val es:String,
    val abbr: String,
    val testament: TipoTestamento,
    val chapters: Int,
    val url:String=""
)

/*
object LibroDtoListSerializer : KSerializer<LibroDto> {
//    private val delegateSerializer = ListSerializer(LibroDto.serializer())
    private val delegateSerializer = JsonObject.serializer()

    override val descriptor: SerialDescriptor
        get() = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder,value: LibroDto) {
        throw UnsupportedOperationException("Readable only")
    }

    override fun deserialize(decoder: Decoder): LibroDto {
        // Se realiza el cast a JsonDecoder para poder leer el JsonElement
        val jsonDecoder = decoder as JsonDecoder
            ?: throw IllegalStateException("JSON deserialization not supported")

//        val rootObject = Json.decodeFromJsonElement(
//            delegateSerializer,
//            decoder.decodeJsonElement()) as JsonObject
        val rootObject = jsonDecoder.decodeJsonElement().jsonObject

        return rootObject.map { (id, esKey , es, abbr, enKey, en, enAbbr, testament, chapters, url ) ->


        }
    }
}
 */

