package org.fmm.biblia.scraper.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
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
@SerialName("libros")
data class LibroDto (
    override val id: Int,
    override val orden: Int,
    val ordenStandard: Int=0,
    val esKey: String,
    override val es:String,
    val abbr: String,
    override val testament: TipoTestamento,
    val chapters: Int,
    override val url:String=""
): IndexBaseDto

