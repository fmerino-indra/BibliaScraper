package org.fmm.biblia.scraper.app

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.BibliaScraper
import org.fmm.biblia.scraper.data.Capitulo
import org.fmm.biblia.scraper.data.DocumentoBiblico
import org.fmm.biblia.scraper.data.TextoBiblico
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmmp.biblia.scraper.utils.RecursosUtils
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun main() {
    val bibliaScraperParsearApp = BibliaScraperParsearApp()
//    bibliaScraperParsearApp.parseBibliaLibros()
    bibliaScraperParsearApp.parseBibliaTextos()
}

class BibliaScraperParsearApp: BaseScraperApp() {
    private lateinit var masterLibros: List<LibroDto>
    private lateinit var validaciones: Map<String,Map<String, Int>>

    fun parseBibliaLibros() {
        val hilo = Thread.currentThread().stackTrace[1].methodName
        logger.error { "-------------------------------------------------------------" }
        logger.error { "[FMMP] Ejecución: $hilo ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "-------------------------------------------------------------" }

        appConfig=readAppConfig()
        masterLibros=leerLibros()
        validaciones=leerValidacionesVersiculos()

        BibliaScraper.init(appConfig)

        // 2. Convertimos a mapa
        val mapaLibrosPorId: Map<Int, LibroDto> = masterLibros.associateBy { it.id }
        //println(buildPageName(1,"Gn"))
        // 3. Recorremos mapa.
        mapaLibrosPorId.forEach { (key, libroDto) ->
            //val documento = buildBook(key,value)
            val aux = "Key: $key -> ${libroDto.es} | ${libroDto.esKey}"
            logger.debug {aux}

            val documento = BibliaScraper.obtainAndParseURLBook(libroDto = libroDto, urlBase = appConfig.local.uriBiblia)
            val versiculosEsperados: Map<String, Int> = validaciones[libroDto.esKey] ?:emptyMap()
            val errores = verificarIntegridad(documento = documento, numCapitulosEsperados = libroDto.chapters, versiculosEsperados = versiculosEsperados, libroDto)
            errores.forEach { (key, value) ->
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
        logger.debug { "(${libroDto.id}) ${documento.titulo} -> " }

        for (i in 1.. numCapitulosEsperados) {

            val existe =capitulosParseados[i] != null
            if (!existe) {
                logger.error { "(${libroDto.id}) ${documento.titulo} -> ❌ ERROR en Cap $i: NO EXISTE" }
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
        logger.debug { "[FMMP] ${documento.titulo} -> $erroresNum Errores " }
        println("[FMMP] ${documento.titulo} -> $erroresNum Errores ")
        return errores
    }

    private fun guardarJson(documento: DocumentoBiblico) {
        val rutaArchivo: String="json/%02d-${documento.key}.json".format(documento.id)
        BibliaScraper.guardarDocumentoBiblicoJson(documento, rutaArchivo)
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

    fun parseBibliaTextos() {
        val hilo = Thread.currentThread().stackTrace[1].methodName
        logger.error { "-------------------------------------------------------------" }
        logger.error { "[FMMP] Ejecución: $hilo ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))}" }
        logger.error { "-------------------------------------------------------------" }

        appConfig=readAppConfig()

        BibliaScraper.init(appConfig)
        val mapaTextosPorId = readTextIndex()

        // 3. Recorremos mapa.
        mapaTextosPorId.forEach { (key, textoDto) ->
            //val documento = buildBook(key,value)
            val aux = "Key: $key -> ${textoDto.es}"
            logger.debug {aux}

            val texto = BibliaScraper.obtainAndParseURLText(textoDto = textoDto, urlBase =appConfig.rutas.rutaHtml )
            guardarTextJson(texto)
        }
    }

    private fun guardarTextJson(texto: TextoBiblico) {
        appConfig.files.uriTextos
        appConfig.rutas.rutaBibliaJson
        val dir=File(File(appConfig.rutas.rutaBase, appConfig.rutas.rutaBibliaJson), appConfig.files.uriTextos)

        val file=File(dir, texto.urlOrigen.replace(".html",".json"))
        RecursosUtils.guardarDocumentoBiblicoJson(texto, file)
    }



    /**
     * Old functions
     */
    private fun readConfigMap(): Map<String,Map<String, String>> {
        val fileName = "config.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(rutaRelativa = fileName)
        return Json.decodeFromString<Map<String,Map<String, String>>>(jsonString)

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