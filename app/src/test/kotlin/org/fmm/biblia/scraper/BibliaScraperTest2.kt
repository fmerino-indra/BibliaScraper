package org.fmm.biblia.scraper

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.fmm.biblia.scraper.data.Capitulo
import org.fmm.biblia.scraper.data.DocumentoBiblico
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmmp.biblia.scraper.utils.RecursosUtils
import org.junit.jupiter.api.BeforeEach

class BibliaScraperTest2 {

    private lateinit var masterLibros: List<LibroDto>
    private lateinit var validaciones: Map<String,Map<String, Int>>
    // 1. URL de la fuente (Sustitúyela por la URL real de tu scraper)
    val urlBiblia = "https://www.edesclee.com/bibliaonline/content/"

    @BeforeEach
    fun `parsear librosjson para que sirva como indice en la descarga` (){
        masterLibros = leerLibros()
        validaciones = leerValidacionesVersiculos()
    }

    @Test
    fun `deberia descargar y parsear capitulos reales cuadrando con los conteos oficiales`() {

        // 2. Convertimos a mapa
        val mapaLibrosPorId: Map<Int, LibroDto> = masterLibros.associateBy { it.id }
        println(buildPageName(1,"Gn"))
        // 3. Recorremos mapa.
        mapaLibrosPorId.forEach { (key, value) ->
            val documento = buildBook(key,value)
            val versiculosEsperados: Map<String, Int> = validaciones[value.esKey] ?:emptyMap()
            verificarIntegridad(documento = documento, numCapitulosEsperados = value.chapters, versiculosEsperados = versiculosEsperados)
            guardarJson(documento)
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
        println(aux)

        // a) Descarga html libro
        /*
        val libroDoc = Jsoup.connect(urlBiblia+libroDto.url)
            .timeout(Duration.ofSeconds(15).toMillis().toInt())
            .get()
        assertNotNull(libroDoc, "El documento descargado no debería ser nulo")
         */

        // a) Descargar y parsear el libro
        val book=BibliaScraper.downloadAndParseURLBook(libroDto = libroDto, urlBase = urlBiblia)
        return book
        //val capitulosParseados = BibliaScraper.parsearHTMLDocument(libroDoc)
        //println(capitulosParseados)


    }
    private fun leerLibros():List<LibroDto> {
        val fileName = "bible-books.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(fileName)
        return Json.decodeFromString<List<LibroDto>>(jsonString)
    }

    private fun guardarJson(documento: DocumentoBiblico) {
        val rutaArchivo: String="json/%02d-${documento.key}.json".format(documento.id)
        BibliaScraper.guardarDocumentoBiblicoJson(documento, rutaArchivo)
    }
    private fun leerValidacionesVersiculos():Map<String,Map<String, Int>> {
        val fileName = "bible-chapters.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(fileName)
        val aux = Json.decodeFromString<Map<String,Map<String, Int>>>(jsonString)
        return aux
    }

    private fun verificarIntegridad(documento: DocumentoBiblico, numCapitulosEsperados: Int, versiculosEsperados: Map<String, Int>) {
        val capitulosParseados: Map<Int, Capitulo> = documento.capitulosLibro

        val caps = numCapitulosEsperados == capitulosParseados.size
        if (!caps) {
            println("❌ ERROR: Capítulos mal parseados: esperados: $numCapitulosEsperados vs parseados: ${capitulosParseados.size}")
            return
        }

        for (i in 1.. numCapitulosEsperados) {
            val existe =capitulosParseados[i] != null
            if (!existe) {
                println("❌ ERROR en Cap $i: NO EXISTE")
                return
            }
            val cap = capitulosParseados[i]

            val cuenta= cap?.versiculos?.count()?:0
            val esperados = versiculosEsperados[i.toString()]?:0

            if (cuenta == esperados) {
                println("✅ ÉXITO: Capítulo $i parseado correctamente con $esperados versículos.")
            } else {
                println("❌ ERROR en Cap $i: Esperados $esperados, pero el algoritmo contó ${cap?.versiculos?.size?:0}.")
            }
        }
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