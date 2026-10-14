package org.fmm.biblia.scraper

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmm.biblia.scraper.data.dto.TextoDto
import org.fmm.biblia.scraper.data.dto.TipoTestamento
import org.junit.jupiter.api.Test
import org.fmmp.biblia.scraper.utils.RecursosUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BibliaScraperTest1 {
    private val logger = KotlinLogging.logger {}
    // 1. URL de la fuente (Sustitúyela por la URL real de tu scraper)
    // val urlBiblia = "https://www.edesclee.com/content/309-biblia-online-ed"
    val urlBiblia = "https://www.edesclee.com/bibliaonline/content/"
    val directorioAlmacen = "html-original-v2"
    private val rutaValidaciones = "validaciones-json-v2"

    val nombreRecurso="indice-hebrea.html"
    val nombreFichero="indice-base.html"

    val librosPreDto = leerLibrosMap()
    val librosCapitulos = leerValidacionesVersiculos()
//https://www.edesclee.com/bibliaonline/content/indice-hebrea.html
    /**
     * Sólo guardar los HTML
     */
    @Test
    fun `descargar y procesar indice`() {
        logger.error { "------------------------------------" }
        logger.error { "[FMMP] Ejecución ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "------------------------------------" }
        val html=IndexScraper.obtenerHtml(uriBase = urlBiblia, uriFichero = nombreRecurso, descargar = true)
        RecursosUtils.guardarHtmlOriginal(
            rutaArchivo = directorioAlmacen,
            nombreFichero = nombreFichero, content = html)
        // Procesar html y bajar textos
        procesarIndice(html, urlBiblia)
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
        RecursosUtils.guardarDocumentoBiblicoJson(json = librosMap,
            rutaArchivo = "$directorioAlmacen/librosDto.json",
        )
        RecursosUtils.guardarDocumentoBiblicoJson(json = textosMap,
            rutaArchivo = "$directorioAlmacen/textosDto.json",
        )
    }

    private fun getReference(element: Element): String {
//        return element.selectFirst("a")?.selectFirst("href")?.text() ?: ""
        return element.getElementsByTag("a").firstOrNull()?.attr("href")?.replace("xhtml", "html") ?: ""
    }

    private fun getTitulo(element: Element): String {
        return element.getElementsByTag("a").firstOrNull()?.text()?: ""
    }

    private fun guardarJson(json: Map<String,Map<String, Int>>, rutaArchivo: String) {
//        val rutaArchivo: String="validaciones-json-v2/bible-chapters.json"
        RecursosUtils.guardarDocumentoBiblicoJson(json, rutaArchivo)
    }

    private fun leerYGuardar(uriBase: String, ref: String): String {
        val aux = IndexScraper.obtenerHtml(uriBase = uriBase, uriFichero = ref, descargar = true)
        val subDir =
            if (ref.startsWith("libro")) {
                "libros"
            } else {
                "textos"
            }
        RecursosUtils.guardarHtmlOriginal(
            rutaArchivo = "$directorioAlmacen/$subDir",
            nombreFichero = ref, content = aux
        )

        return aux
        /*
        if (ref.startsWith("libro")) {
            RecursosUtils.guardarHtmlOriginal(
                rutaArchivo = "$directorioAlmacen/libros",
                nombreFichero = ref, content = aux
            )
        } else {
            RecursosUtils.guardarHtmlOriginal(
                rutaArchivo = "$directorioAlmacen/textos",
                nombreFichero = ref, content = aux
            )
        }

         */
    }

    private fun leerLibros():List<LibroDto> {
        val fileName = "bible-books.json"
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = rutaValidaciones, fileName)
        return Json.decodeFromString<List<LibroDto>>(jsonString)
    }

    private fun leerLibrosMap():Map<String, LibroDto> {
        val fileName = "bible-books.json"
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = rutaValidaciones, fileName)
        return Json.decodeFromString<List<LibroDto>>(jsonString).associateBy { it.url }
    }

    private fun leerValidacionesVersiculos():Map<String,Map<String, Int>> {
        val fileName = "bible-chapters.json"

        //val jsonString = RecursosUtils.leerRecursoComoTexto(fileName)
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = rutaValidaciones, fileName)
        val aux = Json.decodeFromString<Map<String,Map<String, Int>>>(jsonString)
        return aux
    }

}