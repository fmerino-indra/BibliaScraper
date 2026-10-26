package org.fmm.biblia.scraper.app

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.data.AppConfig
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmm.biblia.scraper.data.dto.TextoDto
import org.fmmp.biblia.scraper.utils.RecursosUtils

abstract class BaseScraperApp {
    protected val logger = KotlinLogging.logger {}
    protected lateinit var appConfig: AppConfig

    protected fun init() {
        appConfig = readAppConfig()
    }

    protected fun readAppConfig(): AppConfig {
        val fileName = "config.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(rutaRelativa = fileName)
        return Json.decodeFromString<AppConfig>(jsonString)
    }

    protected fun readBookIndex(): Map<Int,LibroDto> {
        val librosStr = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.local.uriBiblia, appConfig.files.librosDto)
        return Json.decodeFromString<Map<String,LibroDto>>(librosStr).values.associateBy { it.id }
    }
    protected fun readTextIndex(): Map<Int,TextoDto> {
        val textosStr = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.local.uriBiblia, appConfig.files.textosDto)
        return Json.decodeFromString<Map<String,TextoDto>>(textosStr).values.associateBy { it.id }
    }

}