package org.fmm.biblia.scraper

import io.github.oshai.kotlinlogging.KotlinLogging
import org.fmm.biblia.scraper.data.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import java.io.File
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmmp.biblia.scraper.utils.RecursosUtils

object BibliaScraper {
    private val logger = KotlinLogging.logger {}

    fun buildVersiculoId(capitulo: Int, numero: Int): String {
        return "ver_${capitulo}_$numero"
    }

    // 1. Sanitizador para limpiar el HTML malformado antes de que Jsoup cree el DOM
    private fun sanitizarHtml(rawHtml: String): String {
        return rawHtml
            .replace(Regex("""<href""", RegexOption.IGNORE_CASE), "<a href=")
            .replace(Regex("""</href[^>]*>""", RegexOption.IGNORE_CASE), "</a>")
    }

    // 2. Descarga y limpia la página objetivo
    private fun obtenerHtmlYLimpiar(uriBase: String, uriFichero: String, descargar: Boolean=true ): Document {
        val url = uriBase + uriFichero
        if (descargar) {
            val respuesta = Jsoup.connect(url)
//            .userAgent("Mozilla/5.0 (X11; Fedora; Linux x86_64)")
                .userAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36")
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
                )
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Referer", "https://www.edesclee.com/content/309-biblia-online-ed")
//            .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")

                .header("Sec-Ch-Ua", "\"Google Chrome\";v=\"153\", \"Not_A Brand\";v=\"8\", \"Chromium\";v=\"153\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Linux\"")
                .cookie("anubis-cookie-cookie-verification", "01a0ec5a-1fb9-70e5-9704-13b5dbc3ba85")
                .cookie(
                    "anubis-cookie-auth",
                    "eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9.eyJhY3Rpb24iOiJDSEFMTEVOR0UiLCJjaGFsbGVuZ2UiOiIwMWEwZWM1YS0xZmI5LTcwZTUtOTcwNC0xM2I1ZGJjM2JhODUiLCJleHAiOjE3OTEyNzY1MjQsImlhdCI6MTc5MDY3MTcyNCwibWV0aG9kIjoiZmFzdCIsIm5iZiI6MTc5MDY3MTY2NCwicG9saWN5UnVsZSI6ImFjOTgwZjQ5YzRkMzVmYWIiLCJyZXN0cmljdGlvbiI6IjE1OTU2OGExNWMxZmUxMzc1ZDBjMTRmNDZlNThlY2ZlOTY5MzMwOGYyNjg5OWZjMTY3NmNjMmRiMWU1MTI2NTEifQ.zli9evv_MwrEMTqTAt3YQZiqgKH7weFy70qCBjqIY5_KTgpL20NFeq2T9j1v3dn4Uq0CuB7RALajHBGCI8MDDQ"
                )
                .timeout(100_000)
                .execute()
            val bodyResponse = respuesta.body()
            guardarHtmlOriginal(ruta = "html-original", nombreFichero = uriFichero, content = bodyResponse)
            val htmlSanitizado = sanitizarHtml(bodyResponse)
            return Jsoup.parse(htmlSanitizado, url)
        } else {
            val content = RecursosUtils.leerFicheroComoTexto(rutaArchivo=uriBase, archivo = uriFichero)
            val htmlSanitizado = sanitizarHtml(content)
            return Jsoup.parse(htmlSanitizado, url)
        }

    }

    // 3. Orquestador del parseo
    fun obtainAndParseURLBook(
        libroDto: LibroDto,
        urlBase: String,
        urlNotas: String? = null,
        descargar: Boolean = false,
    ): DocumentoBiblico {

        logger.debug { "[FMMP] obtainAndParseURLBook: ${libroDto.es}" }
        logger.debug { "[FMMP] Descargar: $descargar" }

        val docHtml = obtenerHtmlYLimpiar(uriBase = urlBase, uriFichero = libroDto.url, false)
        val seccionesIntro = parsearSeccionesIntroduccion(docHtml)
        val caps = parsearHTMLDocument(docHtml)

        return DocumentoBiblico(
            id = libroDto.id,
            key = libroDto.esKey,
            titulo = libroDto.es,
            abreviatura = libroDto.abbr,
            categoria = CategoriaDocumento.LIBRO,
            orden = libroDto.id,
            urlOrigen = urlBase + libroDto.url,
            seccionesIntroduccion = seccionesIntro,
            capitulosEsperados = libroDto.chapters,
            capitulosLibro = caps,
        )
    }

    private fun parsearSeccionesIntroduccion(doc: Document): List<SeccionIntroduccion> {
        return emptyList()
    }

    private fun parsearHTMLDocument(doc: Document): Map<Int, Capitulo> {
        val areaContenido = doc.selectFirst("div.ddbEbookContent") ?: return emptyMap()
        val contexto = ContextoParseo()

        val elementos = areaContenido.children()
        var indexInicio = elementos.indexOfFirst { it.hasClass("pba") }
        if (indexInicio == -1) indexInicio = 0

        parsearHTMLElements(elementos, indexInicio, contexto)

        // Cierre final del árbol procesado
        contexto.consolidarCapitulo(numCapDetectado = contexto.numCapituloActual)

//        return contexto.capitulos.associateBy { capitulo -> capitulo.numero }
        return contexto.capitulosHash
    }

    private fun parsearHTMLElements(elements: Elements, indexInicio: Int = 0, ctx: ContextoParseo) {
        for (i in indexInicio until elements.size) {
            val elem = elements[i]

            when {
                // #<hX>: Títulos
                elem.tagName() in listOf("h1", "h2", "h3", "h4") -> {
                    val nivel = elem.tagName().removePrefix("h").toIntOrNull() ?: 1
                    val titulo = parsearEncabezado(elem, ctx.numCapituloActual, ctx.titulosPendientes.size + 1, nivel)

                    if (ctx.numVersiculoActual != "-1") {
                        // Si hay un versículo en curso, el título forma parte de su contenido directo
                        // Lo de antes puede ser verdad o bien, si el vers. ha terminado, debe preceder al siguiente versículo o capítulo.
                        ctx.titulosPendientes.add(titulo)
                        //ctx.contenidoVersiculoActual.add(titulo)
                    } else {
                        // Si no hay versículo activo, se reserva en títulos pendientes
                        ctx.titulosPendientes.add(titulo)
                    }
                }

                // #<p>: Párrafos de texto
                elem.tagName() == "p" && (elem.hasClass("ddb6") || elem.hasClass("noindent") || (elem.hasClass("top5"))) -> {
                    procesarParrafo(elem, ctx)
                }

                // #<div>: Estructura anidada
                elem.tagName() == "div" -> {
                    parsearHTMLElements(elements = elem.children(), indexInicio = 0, ctx = ctx)
                }
            }
        }
    }

    private fun procesarParrafo(parrafo: Element, ctx: ContextoParseo) {
        // Detección de cambio de capítulo por <span class="capital1">
//        val spanCapital = parrafo.selectFirst("span.capital1")
        val spanCapital = parrafo.selectFirst("span[class^=capital]")
        if (spanCapital != null) {
            val numCapDetectado = spanCapital.text().replace(Regex("[^0-9]"), "").toIntOrNull()
            if (numCapDetectado != null) {
                ctx.consolidarCapitulo(numCapDetectado)
            }
        }

        // Recorrido de los nodos internos del párrafo
        for (nodoHijo in parrafo.childNodes()) {
            procesarNodoInterno(nodoHijo, ctx)
        }

        // Notificación de fin de párrafo para el versículo en construcción
        if (ctx.numVersiculoActual != "-1" && ctx.contenidoVersiculoActual.isNotEmpty()) {
            ctx.notificarFinDeParrafo()
        }
    }

    private fun procesarNodoInterno(nodo: Node, ctx: ContextoParseo) {
        when (nodo) {
            is Element -> {
                val tag = nodo.tagName().lowercase()

                when {
                    // Marcador de versículo: Punto de corte y consolidación
                    tag == "sup" && nodo.id().startsWith("v") -> {
                        // Cualquier caracter que no sea un dígito lo reemplaza por "" (cadena vacía)
//                        val numNuevoVer = nodo.text().replace(Regex("[^0-9]"), "").toIntOrNull() ?: -1
                        val numNuevoVer: String = nodo.text().replace(Regex("[^0-9a-z]"), "")
                        ctx.consolidarVersiculo()

                        ctx.numVersiculoActual = numNuevoVer
                        return
                    }

                    // Detección de Nota al pie
                    isTagNota(nodo, tag) -> {
                        val nota = extraerYRegistrarNota(nodo, tag, ctx.numCapituloActual, ctx.notasPendientes)
                        if (nota != null) {
                            if (ctx.numVersiculoActual == "-1") {
                                ctx.capitulosHash[ctx.numCapituloActual]?.llamadasNotas?.add(nota)
                            } else {
                                ctx.contenidoVersiculoActual.add(nota)
                            }
                        }
                    }

                    // Detección de Paralelo
                    isTagParalelo(nodo, tag) -> {
                        val ref = nodo.attr("href").ifEmpty {
                            tag.substringAfter("href=").replace("\"", "").trim()
                        }
                        if (ref.isNotBlank()) {
                            ctx.paralelosVersiculoActual.add(Paralelo(cita = ref, destino = ref))
                        }
                    }
                }

                for (hijo in nodo.childNodes()) {
                    procesarNodoInterno(hijo, ctx)
                }
            }

            is TextNode -> {
                val texto = nodo.text()
                val textoLimpio = texto.trim().replace(Regex("\\s+"), " ")

                val parent = nodo.parent() as? Element
//                val esSpanCapital = parent?.tagName() == "span" && parent.hasClass("capital1")
                val esSpanCapital = parent?.tagName() == "span" && parent.className().startsWith("capital")

                if (!esSpanCapital && texto.isNotBlank()) {
                    // Si lo siguiente a leer es texto y hay títulos
                    if (ctx.titulosPendientes.isNotEmpty()) {
                        ctx.contenidoVersiculoActual.addAll(ctx.titulosPendientes)
                        ctx.titulosPendientes.clear()
                    }
                    addTextoPlanoVersiculo(ctx.contenidoVersiculoActual, textoLimpio)
                }
            }
        }
    }

    private fun parsearEncabezado(elem: Element, numCapitulo: Int, orden: Int, nivel: Int): Titulo {
        val notas = mutableListOf<NotaAlPie>()
        val paralelos = mutableListOf<Paralelo>()
        val textoTitulo = StringBuilder()

        fun recorrerNodo(nodo: Node) {
            when (nodo) {
                is Element -> {
                    val tag = nodo.tagName().lowercase()
                    if (isTagParalelo(nodo, tag)) {
                        extraerYRegistrarParalelo(nodo, tag, numCapitulo, paralelos)
                    }
                    if (isTagNota(nodo, tag)) {
                        extraerYRegistrarNota(nodo, tag, numCapitulo, notas)
                    }
                    for (hijo in nodo.childNodes()) {
                        recorrerNodo(hijo)
                    }
                }
                is TextNode -> {
                    val t = nodo.text()
                    if (t.isNotBlank()) textoTitulo.append(t)
                }
            }
        }

        recorrerNodo(elem)

        return Titulo(
            id = "tit_${numCapitulo}_$orden",
            texto = textoTitulo.toString().trim().replace(Regex("\\s+"), " "),
            nivel = nivel,
            llamadasNotas = notas.distinctBy { it.href }.toMutableList(),
            paralelos = paralelos.distinctBy { it.cita }.toMutableList(),
        )
    }

    private fun addTextoPlanoVersiculo(contenidoVersiculo: MutableList<IElementoVersiculo>, textoLimpio: String) {
        if (textoLimpio.isBlank() || textoLimpio == "*") return

        val ultimo = contenidoVersiculo.lastOrNull()
        if (ultimo is TextoPlano) {
            val indiceUltimo = contenidoVersiculo.lastIndex
            val primerCaracter = textoLimpio.first()
            val signosSinEspacioPrevio = setOf('.', ',', ';', ':', '?', '!', ')', ']', '»')
            val conector = if (primerCaracter in signosSinEspacioPrevio) "" else " "

            contenidoVersiculo[indiceUltimo] = TextoPlano(ultimo.texto + conector + textoLimpio)
        } else {
            contenidoVersiculo.add(TextoPlano(textoLimpio))
        }
    }

    private fun isTagNota(elem: Element, tag: String): Boolean {
        return tag.startsWith("id") || (tag == "span" && elem.attr("data-type") == "id")
    }

    private fun isTagParalelo(elem: Element, tag: String): Boolean {
        return tag.startsWith("href") || (tag == "span" && elem.attr("data-type") == "href")
    }

    private fun extraerYRegistrarNota(elem: Element, tag: String, capitulo: Int, buffer: MutableList<NotaAlPie>): NotaAlPie? {
        val hrefAttr = elem.attr("href").ifEmpty {
            elem.outerHtml().substringAfter("href=\"", "").substringBefore("\"")
        }
        val target = hrefAttr.substringAfter("#", "")
            .ifEmpty { elem.id() }
            .ifEmpty { tag.removePrefix("id") }
        return buffer.registrarNota(idHtml = target, href = hrefAttr, capitulo = capitulo)
    }

    private fun extraerYRegistrarParalelo(nodo: Node, tag: String, capitulo: Int, buffer: MutableList<Paralelo>) {
        if (nodo is Element && tag.startsWith("href")) {
            val ref = nodo.attr("href").ifEmpty { tag.substringAfter("href=").replace("\"", "").trim() }
            buffer.registrarParalelo(ref, ref)
        }
    }

    fun guardarDocumentoBiblicoJson(documento: DocumentoBiblico, rutaArchivo: String) {
        val jsonConfig = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        val jsonString = jsonConfig.encodeToString(documento)
        val archivo = File(rutaArchivo)
        archivo.writeText(jsonString, Charsets.UTF_8)

        println("✅ Estructura exportada con éxito a: ${archivo.absolutePath}")
    }

    private fun guardarHtmlOriginal(ruta: String, nombreFichero: String, content: String) {
        RecursosUtils.guardarHtmlOriginal(ruta, nombreFichero, content)
    }
}