package org.fmm.biblia.scraper.app

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.data.AppConfig
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmmp.biblia.scraper.utils.RecursosUtils
import org.jsoup.Jsoup

open class BaseScraper {
    protected val logger = KotlinLogging.logger {}

    protected lateinit var appConfig: AppConfig
    protected lateinit var masterLibros: List<LibroDto>
    protected lateinit var librosPreDto: Map<String, LibroDto>
    protected lateinit var validaciones: Map<String,Map<String, Int>>

    fun init(appConfig: AppConfig) {
        this.appConfig = appConfig
        masterLibros = leerLibros()
        validaciones = leerValidacionesVersiculos()
        librosPreDto  = leerLibrosMap()
    }


    protected fun readAppConfig(): AppConfig {
        val fileName = "config.json"

        val jsonString = RecursosUtils.leerRecursoComoTexto(rutaRelativa = fileName)
        return Json.decodeFromString<AppConfig>(jsonString)

    }

    protected fun leerLibros():List<LibroDto> {
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.rutas.rutaValidaciones, appConfig.files.bibleBooks)
        return Json.decodeFromString<List<LibroDto>>(jsonString)
    }

    protected fun leerValidacionesVersiculos():Map<String,Map<String, Int>> {
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.rutas.rutaValidaciones, appConfig.files.bibleChapters)
        val aux = Json.decodeFromString<Map<String,Map<String, Int>>>(jsonString)
        return aux
    }

    protected fun leerLibrosMap():Map<String, LibroDto> {
        val fileName = "bible-books.json"
        val jsonString = RecursosUtils.leerFicheroComoTexto(rutaArchivo = appConfig.rutas.rutaValidaciones, fileName)
        return Json.decodeFromString<List<LibroDto>>(jsonString).associateBy { it.url }
    }

    // 2. Descarga y limpia la página objetivo
    protected fun obtenerHtml(uriBase: String, uriFichero: String="", descargar: Boolean=true): String{
        val url = uriBase + uriFichero
        if (descargar) {
            val respuesta = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Referer", "https://www.edesclee.com/content/309-biblia-online-ed")
                .header("Upgrade-Insecure-Requests", "1")
//                .cookie("anubis-cookie-cookie-verification", "01a0fb2d-0775-7ea1-bc1f-8bed90b8d88b")
//                .cookie(
//                    "anubis-cookie-auth",
//                    "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhY3Rpb24iOiJDSEFMTEVOR0UiLCJjaGFsbGVuZ2UiOiIwMWEwZmIyZC0wNzc1LTdlYTEtYmMxZi04YmVkOTBiOGQ4OGIiLCJleHAiOjE3OTE1MjUyMjcsImlhdCI6MTc5MDkyMDQyNywibWV0aG9kIjoiZmFzdCIsIm5iZiI6MTc5MDkyMDM2NywicG9saWN5UnVsZSI6ImFjOTgwZjQ5YzRkMzVmYWIiLCJyZXN0cmljdGlvbiI6IjE1OTU2OGExNWMxZmUxMzc1ZDBjMTRmNDZlNThlY2ZlOTY5MzMwOGYyNjg5OWZjMTY3NmNjMmRiMWU1MTI2NTEifQ.5DYYRIcrnfHTKBKouLp9rwH6F9fR6O0ZKBLFjvRNovay6tNqqn3XEB8eb80IN58MMn0CGn-UUMngmGyaEmvzBg"
//                )
                .cookie("anubis-cookie-cookie-verification", appConfig.security.anubisCookieCookieVerification)
                .cookie(
                    "anubis-cookie-auth",
                    appConfig.security.anubisCookieAuth
                )
                .timeout(100_000)
                .execute()
            val bodyResponse = respuesta.body()
            return bodyResponse
        } else { // descargar
            // Leer
            return RecursosUtils.leerFicheroComoTexto(rutaArchivo = uriBase, archivo = uriFichero)
        }

    }

}