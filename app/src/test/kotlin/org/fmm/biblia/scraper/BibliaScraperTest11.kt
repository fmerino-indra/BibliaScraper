package org.fmm.biblia.scraper

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.data.AppConfig
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmm.biblia.scraper.data.dto.TextoDto
import org.fmm.biblia.scraper.data.dto.TipoTestamento
import org.junit.jupiter.api.Test
import org.fmmp.biblia.scraper.utils.RecursosUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BibliaScraperTest11 {
    private val logger = KotlinLogging.logger {}

    private lateinit var appConfig: AppConfig
    private lateinit var masterLibros: List<LibroDto>
    private lateinit var validaciones: Map<String,Map<String, Int>>

//    private lateinit var indexDto: Map<String, Map>
    @BeforeEach
    fun `parsear librosjson para que sirva como indice en la descarga` () {
        appConfig = readAppConfig()

        masterLibros = leerLibros()
        validaciones = leerValidacionesVersiculos()
    }


    @Test
    fun `crear nuevo indice`() {
        logger.error { "------------------------------------" }
        logger.error { "[FMMP] Ejecución ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "------------------------------------" }

    }

    private fun readAppConfig(): AppConfig {
        val fileName = "config.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(rutaRelativa = fileName)
        return Json.decodeFromString<AppConfig>(jsonString)

    }



    private fun leerLibros():List<LibroDto> {
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.rutas.rutaValidaciones, appConfig.files.bibleBooks)
        return Json.decodeFromString<List<LibroDto>>(jsonString)
    }

    private fun leerValidacionesVersiculos():Map<String,Map<String, Int>> {
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.rutas.rutaValidaciones, appConfig.files.bibleChapters)
        val aux = Json.decodeFromString<Map<String,Map<String, Int>>>(jsonString)
        return aux
    }
}