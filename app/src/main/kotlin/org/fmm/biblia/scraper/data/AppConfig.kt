package org.fmm.biblia.scraper.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
    val remote: RemoteConfig,
    val local: LocalConfig,
    val rutas: RutasConfig,
    val files: FilesConfig
)

@Serializable
data class RemoteConfig(
    val uriBiblia: String,
    val index: String
)

@Serializable
data class LocalConfig(
    val uriBiblia: String,
    val index: String
)

@Serializable
data class RutasConfig(
    val rutaValidaciones: String
)

@Serializable
data class FilesConfig(
    @SerialName("bible-books")
    val bibleBooks: String,    // Al usar guiones, envolvemos el nombre en comillas invertidas
    @SerialName("bible-chapters")
    val bibleChapters: String, // O bien podemos usar @SerialName si prefieres camelCase en Kotlin
    val uriLibros: String,
    val uriTextos: String,
    val librosDto: String,
    val textosDto: String
)
