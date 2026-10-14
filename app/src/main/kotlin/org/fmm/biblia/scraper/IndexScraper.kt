package org.fmm.biblia.scraper

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.fmmp.biblia.scraper.utils.RecursosUtils

object IndexScraper {
    private val logger = KotlinLogging.logger {}

    // 1. Orquestador del parseo
    fun obtainAndParseIndex(
        urlBase: String,
        descargar: Boolean = false,
    ) {
        logger.debug { "==========================" }
        logger.debug { "[FMMP] obtainAndParseIndex" }
        logger.debug { "[FMMP] Descargar: $descargar" }
        logger.debug { "==========================" }
        val docHtml = obtenerHtml(uriBase = urlBase, uriFichero = "index-base.xhtml", false)
    }

    // 2. Descarga y limpia la página objetivo
    fun obtenerHtml(uriBase: String, uriFichero: String="", descargar: Boolean=true): String{
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
//                .cookie("anubis-cookie-cookie-verification", "01a0ec5a-1fb9-70e5-9704-13b5dbc3ba85")
//                .cookie(
//                    "anubis-cookie-auth",
//                    "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhY3Rpb24iOiJDSEFMTEVOR0UiLCJjaGFsbGVuZ2UiOiIwMWEwZWM1YS0xZmI5LTcwZTUtOTcwNC0xM2I1ZGJjM2JhODUiLCJleHAiOjE3OTEyNzY1MjQsImlhdCI6MTc5MDY3MTcyNCwibWV0aG9kIjoiZmFzdCIsIm5iZiI6MTc5MDY3MTY2NCwicG9saWN5UnVsZSI6ImFjOTgwZjQ5YzRkMzVmYWIiLCJyZXN0cmljdGlvbiI6IjE1OTU2OGExNWMxZmUxMzc1ZDBjMTRmNDZlNThlY2ZlOTY5MzMwOGYyNjg5OWZjMTY3NmNjMmRiMWU1MTI2NTEifQ.zli9evv_MwrEMTqTAt3YQZiqgKH7weFy70qCBjqIY5_KTgpL20NFeq2T9j1v3dn4Uq0CuB7RALajHBGCI8MDDQ"
//                )
                .cookie("anubis-cookie-cookie-verification", "01a0fb2d-0775-7ea1-bc1f-8bed90b8d88b")
                .cookie(
                    "anubis-cookie-auth",
                    "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhY3Rpb24iOiJDSEFMTEVOR0UiLCJjaGFsbGVuZ2UiOiIwMWEwZmIyZC0wNzc1LTdlYTEtYmMxZi04YmVkOTBiOGQ4OGIiLCJleHAiOjE3OTE1MjUyMjcsImlhdCI6MTc5MDkyMDQyNywibWV0aG9kIjoiZmFzdCIsIm5iZiI6MTc5MDkyMDM2NywicG9saWN5UnVsZSI6ImFjOTgwZjQ5YzRkMzVmYWIiLCJyZXN0cmljdGlvbiI6IjE1OTU2OGExNWMxZmUxMzc1ZDBjMTRmNDZlNThlY2ZlOTY5MzMwOGYyNjg5OWZjMTY3NmNjMmRiMWU1MTI2NTEifQ.5DYYRIcrnfHTKBKouLp9rwH6F9fR6O0ZKBLFjvRNovay6tNqqn3XEB8eb80IN58MMn0CGn-UUMngmGyaEmvzBg"
                )
                .timeout(100_000)
                .execute()
            val bodyResponse = respuesta.body()
            return bodyResponse
        } else {
            // Leer
            return ""
        }

    }
    fun parseHtml(html: String, baseUri: String): Document {
        return Jsoup.parse(html, baseUri)
    }
}