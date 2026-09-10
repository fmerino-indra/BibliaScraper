package org.fmm.biblia.scraper

import org.jsoup.Jsoup

class App {
    fun ejecutar() {

        val urlTextoAnt = "https://www.edesclee.com/content/309-biblia-online-ed?page=libro01gn"
        val urlBase = "https://www.edesclee.com/bibliaonline/content/"
        val libroGenesis = "libro01gn.html"
        val libroExodo   = "libro02ex.html"

        val documento = BibliaScraper.parsearURLBible(
            idDoc = "LIB_GN",
            titulo = "Génesis",
            urlTexto = urlBase + libroGenesis,
            urlNotas = "https://www.edesclee.com/content/309-biblia-online-ed?page=notas01gn"
        )

        println("Documento parseado con éxito: ${documento.titulo}")
        println("Capítulos encontrados: ${documento.capitulosLibro.size}")
    }
}

fun main() {
    App().ejecutar()
}


fun pruebaBasica() {
    val url = "https://www.edesclee.com/content/309-biblia-online-ed"
    try {
        println("Conectando a : $url")
        val doc = Jsoup.connect(url).get()

        val titulo = doc.select("h3").first()?.text()
        println("Conexión exitosa. Título detectado: $titulo")

    } catch (e: Exception) {
        println("Error al conectar: ${e.message}")
    }
}
