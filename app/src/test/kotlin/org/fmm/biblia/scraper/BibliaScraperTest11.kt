package org.fmm.biblia.scraper

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.data.AppConfig
import org.fmm.biblia.scraper.data.dto.IndexBaseDto
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
import kotlin.math.min

class BibliaScraperTest11 {
    private val logger = KotlinLogging.logger {}

    private lateinit var appConfig: AppConfig

    private lateinit var librosDto: Map<Int, LibroDto>
    private lateinit var textosDto: Map<Int, TextoDto>

    @BeforeEach
    fun `parsear librosjson para que sirva como indice en la descarga` () {
        appConfig = readAppConfig()
        //masterLibros = leerLibros()
        //validaciones = leerValidacionesVersiculos()
    }


    @Test
    fun `crear nuevo indice`() {
        logger.debug { "------------------------------------" }
        logger.debug { "[FMMP] Ejecución ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.debug { "------------------------------------" }

        val bookIndex=readBookIndex()
        val textIndex=readTextIndex()
        var baseIndex: MutableMap<Int, IndexBaseDto> = mutableMapOf()


        baseIndex = bookIndex.toMutableMap()
        baseIndex.putAll(textIndex)

        val size =baseIndex.size
        val orderedBaseIndex = baseIndex.toSortedMap()

        for (i in 1 .. size) {
            if (orderedBaseIndex[i] is LibroDto)
                logger.debug { "Book: $i: ${orderedBaseIndex[i]?.es?:""}" }
            else if (orderedBaseIndex[i] is TextoDto)
                logger.debug { "Text: $i: ${orderedBaseIndex[i]?.es?:""}" }
            else
                logger.debug { "Error: $i" }
        }

        RecursosUtils.guardarDocumentoBiblicoJson(orderedBaseIndex.toMap(), fileDir = appConfig.local.uriBiblia, fileName = "baseIndexDto.json")
    }

    private fun readAppConfig(): AppConfig {
        val fileName = "config.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(rutaRelativa = fileName)
        return Json.decodeFromString<AppConfig>(jsonString)

    }

    private fun readBookIndex(): Map<Int,LibroDto> {
        val librosStr = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.local.uriBiblia, appConfig.files.librosDto)
        return Json.decodeFromString<Map<String,LibroDto>>(librosStr).values.associateBy { it.id }
    }
    private fun readTextIndex(): Map<Int,TextoDto> {
        val textosStr = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.local.uriBiblia, appConfig.files.textosDto)
        return Json.decodeFromString<Map<String,TextoDto>>(textosStr).values.associateBy { it.id }
    }
}