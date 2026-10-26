package org.fmm.biblia.scraper.data.dto

import kotlinx.serialization.Serializable

@Serializable
sealed interface IndexBaseDto {
    val id: Int
    val orden: Int
    val es:String
    val testament: TipoTestamento
    val url:String
}