package org.fmm.biblia.scraper.app

import org.fmm.biblia.scraper.IndexScraper
import org.fmm.biblia.scraper.data.dto.IndexBaseDto
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmm.biblia.scraper.data.dto.TextoDto
import org.fmmp.biblia.scraper.utils.RecursosUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val indexScraper = IndexScraperSaveOriginalsApp()
    // Comentado porque ya se ha bajado la información
    indexScraper.saveOriginals()
    indexScraper.createUniqueIndex()
}

class IndexScraperSaveOriginalsApp: BaseScraperApp() {

    fun saveOriginals() {
        logger.error { "------------------------------------" }
        logger.error { "[FMMP] Ejecución ${Thread.currentThread().stackTrace[1].methodName} ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "------------------------------------" }
        init()
        IndexScraper.init(appConfig)
        IndexScraper.obtainParseIndexAndSaveOriginalBooks( true)
    }

    fun createUniqueIndex() {
        logger.error { "------------------------------------" }
        val hilo = Thread.currentThread().stackTrace[1].methodName
        logger.error { "[FMMP] Ejecución: $hilo ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "------------------------------------" }
        init()

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

        RecursosUtils.guardarDocumentoBiblicoJson(orderedBaseIndex.toMap(), fileDir = appConfig.rutas.rutaHtml, fileName = appConfig.files.baseIndexDto)
    }

}