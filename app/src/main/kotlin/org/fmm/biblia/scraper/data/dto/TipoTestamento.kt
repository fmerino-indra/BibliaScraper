package org.fmm.biblia.scraper.data.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/*
enum class TipoTestamento {
    @SerialName("old")
    ANTIGUO_TESTAMENTO,
    @SerialName("new")
    NUEVO_TESTAMENTO,

    OTRO
}
*/
@Serializable(with = TestamentoSerializer::class)
enum class TipoTestamento {
    ANTIGUO_TESTAMENTO,
    NUEVO_TESTAMENTO,
    OTRO
}

object TestamentoSerializer : KSerializer<TipoTestamento> {
    override val descriptor = PrimitiveSerialDescriptor("Testamento", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): TipoTestamento {
        return when (decoder.decodeString().lowercase()) {
            "old" -> TipoTestamento.ANTIGUO_TESTAMENTO
            "new" -> TipoTestamento.NUEVO_TESTAMENTO
            else -> TipoTestamento.OTRO
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: TipoTestamento
    ) {
        encoder.encodeString(
            when (value) {
                TipoTestamento.ANTIGUO_TESTAMENTO -> "old"
                TipoTestamento.NUEVO_TESTAMENTO -> "new"
                else -> "other"
            }
        )
    }
}


