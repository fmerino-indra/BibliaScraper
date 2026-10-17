package org.fmm.biblia.scraper

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.data.AppConfig
import org.junit.jupiter.api.Test
import org.fmm.biblia.scraper.data.Capitulo
import org.fmm.biblia.scraper.data.DocumentoBiblico
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmmp.biblia.scraper.utils.RecursosUtils
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BibliaScraperTest0 {
    private val rutaValidaciones = "validaciones-json-v2"
    private val rutaHtml = "html-original/libros"

    private val logger = KotlinLogging.logger {}
    private val descargar = false

    private lateinit var config: Map<String, Map<String, String>>
    private lateinit var appConfig: AppConfig
    private lateinit var masterLibros: List<LibroDto>
    private lateinit var validaciones: Map<String,Map<String, Int>>

    // 1. URL de la fuente (Sustitúyela por la URL real de tu scraper)
    val urlBiblia = "https://www.edesclee.com/bibliaonline/content/"

    @BeforeEach
    fun `parsear librosjson para que sirva como indice en la descarga` () {
        appConfig = readAppConfig()
        masterLibros = leerLibros()
        validaciones = leerValidacionesVersiculos()
    }

    @Test
    fun `leer nuevo indice y parsear los libros html`() {
        logger.error { "------------------------------------" }
        logger.error { "[FMMP] Ejecución ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "------------------------------------" }

    }

    @Test
    fun `deberia descargar y parsear capitulos reales cuadrando con los conteos oficiales`() {
        logger.error { "------------------------------------" }
        logger.error { "[FMMP] Ejecución ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "------------------------------------" }

        // 2. Convertimos a mapa
        val mapaLibrosPorId: Map<Int, LibroDto> = masterLibros.associateBy { it.id }
        //println(buildPageName(1,"Gn"))
        // 3. Recorremos mapa.
        mapaLibrosPorId.forEach { (key, value) ->
            val documento = buildBook(key,value)
            val versiculosEsperados: Map<String, Int> = validaciones[value.esKey] ?:emptyMap()
            val errores = verificarIntegridad(documento = documento, numCapitulosEsperados = value.chapters, versiculosEsperados = versiculosEsperados, value)
            errores.forEach { key, value ->
                if (value.isNotEmpty())
                    logger.error { "$key: $value" }
            }
//            guardarJson(documento)
        }
        // ==========================================
        // VALIDACIÓN CONTRA LA TABLA DE CONTROL
        // ==========================================

        // Imprimimos un resumen en consola para tu control visual al ejecutar el test
        /*
        println("--- Reporte de Integridad del Test ---")
        capitulosParseados.forEach { capi ->
            println("Libro: Génesis | Cap: ${capi.numero} | Versículos parseados: ${capi.versiculos.size}")
        }

         */
    }

    private fun buildBook(key: Int, libroDto: LibroDto):DocumentoBiblico {
        val aux = "Key: $key -> ${libroDto.es} | ${libroDto.esKey}"
        println()
        println()
        println(aux)
        logger.debug {aux}
        // a) Descarga html libro
        /*
        val libroDoc = Jsoup.connect(urlBiblia+libroDto.url)
            .timeout(Duration.ofSeconds(15).toMillis().toInt())
            .get()
        assertNotNull(libroDoc, "El documento descargado no debería ser nulo")
         */

        // a) Descargar y parsear el libro

        val book=if (descargar)
            BibliaScraper.obtainAndParseURLBook(libroDto = libroDto, urlBase = appConfig.remote.uriBiblia, descargar = true)
        else
            BibliaScraper.obtainAndParseURLBook(libroDto = libroDto, urlBase = appConfig.local.uriBiblia, descargar = false)
        return book
        //val capitulosParseados = BibliaScraper.parsearHTMLDocument(libroDoc)
        //println(capitulosParseados)


    }

    private fun readConfigMap(): Map<String,Map<String, String>> {
        val fileName = "config.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(rutaRelativa = fileName)
        return Json.decodeFromString<Map<String,Map<String, String>>>(jsonString)

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

    private fun guardarJson(documento: DocumentoBiblico) {
        val rutaArchivo: String="json/%02d-${documento.key}.json".format(documento.id)
        BibliaScraper.guardarDocumentoBiblicoJson(documento, rutaArchivo)
    }
    private fun verificarIntegridad(documento: DocumentoBiblico, numCapitulosEsperados: Int, versiculosEsperados: Map<String, Int>, libroDto: LibroDto): Map<String, List<Int>> {
        val errores: MutableMap<String, List<Int>> = mutableMapOf() // genesis: [3, 47, ...]
        val listaCapitulosError: MutableList<Int> = mutableListOf()
        val capitulosParseados: Map<Int, Capitulo> = documento.capitulosLibro

        val caps = numCapitulosEsperados == capitulosParseados.size
        if (!caps) {
            println("❌ ERROR: Capítulos mal parseados: esperados: $numCapitulosEsperados vs parseados: ${capitulosParseados.size}")
            return errores
        }
        var erroresNum = 0

        for (i in 1.. numCapitulosEsperados) {

            val existe =capitulosParseados[i] != null
            if (!existe) {
                println("❌ ERROR en Cap $i: NO EXISTE")
                return errores
            }
            val cap = capitulosParseados[i]

            val cuenta= cap?.versiculos?.count()?:0
            val esperados = versiculosEsperados[i.toString()]?:0

            if (cuenta == esperados) {
                //println("✅ ÉXITO: Capítulo $i parseado correctamente con $esperados versículos.")
            } else {
                erroresNum++
                println("❌ ERROR en Cap $i: Esperados $esperados, pero el algoritmo contó ${cap?.versiculos?.size?:0}.")

                listaCapitulosError.add(cap?.numero?:-1)
            }
        }
        errores[libroDto.esKey] = listaCapitulosError
        println("[FMMP] ${documento.titulo} -> $erroresNum Errores ")
        return errores
    }
    private fun buildPageName(id:Int, key:String):String {
// <9 ==
        // 9 y 10 => libro09s.html == n y n - 1
        // 11 y 12 => libro10r.html == n-1 Y n-2
        // 13 y 14 => libro11c.html == n-2 Y n-3
        // 15 => libro12esd.html == n-3
        // >14 => NN = n-3
        return "libro%02d${key.lowercase()}.html".format(id)

    }

}