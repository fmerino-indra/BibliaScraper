package org.fmm.biblia.scraper

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.app.BaseScraper
import org.fmm.biblia.scraper.data.AppConfig
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmm.biblia.scraper.data.dto.TextoDto
import org.fmm.biblia.scraper.data.dto.TipoTestamento
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.fmmp.biblia.scraper.utils.RecursosUtils
import org.jsoup.nodes.Element

object IndexScraper: BaseScraper() {
    private val nombreFichero="indice-base.html"

    // 1. Orquestador del parseo
    fun obtainParseIndexAndSaveOriginalBooks(
        descargar: Boolean = false
    ) {
        logger.debug { "==========================" }
        logger.debug { "[FMMP] obtainAndParseIndex" }
        logger.debug { "[FMMP] Descargar: $descargar" }
        logger.debug { "==========================" }

        init(appConfig)
        val indexFile = if (descargar)
            appConfig.remote.index
        else
            nombreFichero
        val dirFile = if (descargar)
            appConfig.remote.uriBiblia
        else
            appConfig.local.uriBiblia

        val docHtml =  obtenerHtml(uriBase = dirFile, uriFichero = indexFile, descargar)

        RecursosUtils.guardarHtmlOriginal(
            rutaArchivo = appConfig.local.uriBiblia,
            nombreFichero = nombreFichero, content = docHtml)
        // Procesar html y bajar textos
        procesarIndice(docHtml, appConfig.remote.uriBiblia)
    }

    private fun procesarIndice(html: String, uriBase:String) {
        val indexDocument = Jsoup.parse(html, uriBase)
        val areaToc = indexDocument.selectFirst("div.ddbToc") ?: return
        val elementos = areaToc.children()
        val librosMap: MutableMap<String, LibroDto> = mutableMapOf()
        val textosMap: MutableMap<String, TextoDto> = mutableMapOf()
        var indexLibros=1

        var testamento: TipoTestamento = TipoTestamento.OTRO

        var textoDto : TextoDto? = null

        for (i in 0 until elementos.size) {
            val elemento = elementos[i]
            val htmlContentFile =
                when {
                    elemento.tagName() == "p" && elemento.hasClass("t1")-> {
                        val ref = getReference(elemento)
                        val titulo = getTitulo(elemento)

                        if (ref.isNotEmpty()) {
                            val aux = leerYGuardar(uriBase=uriBase, ref=ref)
                            textoDto = TextoDto(
                                id = indexLibros, orden = indexLibros++,
                                es = titulo, testament = testamento,
                                url = ref
                            )
                            textosMap[ref] = textoDto
                            aux
                        } else {
                            ""
                        }
                    }
                    elemento.tagName() == "p" && elemento.hasClass("c")-> {
                        // [FMMP] Puede que sea un texto
                        // <p class="c">
                        //   <a href="indice-griega.html" class="ddbindice">Índice de la Biblia griega</a>
                        // </p>
                        val ref = getReference(elemento)
                        val titulo = getTitulo(elemento)
                        if (ref.isNotEmpty()) {
                            val aux = leerYGuardar(uriBase = uriBase, ref)
                            if (ref.startsWith("libro")) {
                                val libroAux = librosPreDto[ref]
                                val libroDto = libroAux?.copy(id=indexLibros++, ordenStandard = libroAux.id) ?: LibroDto(id=indexLibros, orden = indexLibros++,
                                    esKey = titulo, es = titulo,
                                    abbr = titulo,
                                    chapters = 0,
                                    testament = testamento,
                                    url = ref)
                                librosMap[ref] = libroDto

                                val ref2 =
                                    when {
                                        ref.startsWith("libro09s") -> "libro09s2.html"
                                        ref.startsWith("libro10r") -> "libro10r2.html"
                                        ref.startsWith("libro11cr") -> "libro11cr2.html"
                                        else -> null
                                    }
                                if (ref2 != null) {
                                    leerYGuardar(uriBase = uriBase, ref=ref2)
                                    val libroAux = librosPreDto[ref2]
                                    val libroDto = libroAux?.copy(id=indexLibros++, ordenStandard = libroAux.id) ?: LibroDto(id=indexLibros, orden = indexLibros++,
                                        esKey = titulo, es = titulo,
                                        abbr = titulo,
                                        chapters = 0,
                                        testament = testamento,
                                        url = ref2)
                                    librosMap[ref2] = libroDto
                                }
                            } else {
                                textoDto = TextoDto(
                                    id = indexLibros, orden = indexLibros++,
                                    es = titulo, testament = testamento,
                                    url = ref
                                )
                                textosMap[ref] = textoDto

                            }
                            aux
                        } else {
                            ""
                        }
                    }
                    elemento.tagName() == "p" && elemento.hasClass("centro")-> {
                        testamento=TipoTestamento.ANTIGUO_TESTAMENTO

                    }
                    elemento.tagName() == "p" && elemento.hasClass("centro3")-> {
                        if (elemento.text() == "NUEVO TESTAMENTO")
                            testamento=TipoTestamento.NUEVO_TESTAMENTO
                        else {
                            println("[FMMP] Otra cosa")
                        }
                    }

                    else -> {

                    }
                }
            if (htmlContentFile != "" && textoDto != null) {
                println("[FMMP] Se ha leido: ${textoDto.es}")
                textoDto = null
            }
        } // for
        RecursosUtils.guardarDocumentoBiblicoJson(
            json = librosMap,
            fileDir = appConfig.local.uriBiblia,
            fileName = appConfig.files.librosDto
        )
        RecursosUtils.guardarDocumentoBiblicoJson(
            json = textosMap,
            fileDir = appConfig.local.uriBiblia,
            fileName = appConfig.files.textosDto
        )
    }

    private fun getReference(element: Element): String {
        return element.getElementsByTag("a").firstOrNull()?.attr("href")?.replace("xhtml", "html") ?: ""
    }

    private fun getTitulo(element: Element): String {
        return element.getElementsByTag("a").firstOrNull()?.text()?: ""
    }

    private fun leerYGuardar(uriBase: String, ref: String): String {
        val aux = obtenerHtml(uriBase = uriBase, uriFichero = ref, descargar = true)
        val subDir =
            if (ref.startsWith("libro")) {
                "libros"
            } else {
                "textos"
            }
        RecursosUtils.guardarHtmlOriginal(
            rutaArchivo = "${appConfig.local.uriBiblia}/$subDir",
            nombreFichero = ref, content = aux
        )

        return aux
    }

}