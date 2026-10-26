package org.fmm.biblia.scraper

import org.fmm.biblia.scraper.data.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import java.io.File
import kotlinx.serialization.json.Json
import org.fmm.biblia.scraper.app.BaseScraper
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.fmm.biblia.scraper.data.dto.TextoDto
import org.fmmp.biblia.scraper.utils.RecursosUtils

object BibliaScraper: BaseScraper() {
    fun buildVersiculoId(capitulo: Int, numero: Int): String {
        return "ver_${capitulo}_$numero"
    }

    // 1. Sanitizador para limpiar el HTML malformado antes de que Jsoup cree el DOM
    private fun sanitizarHtml(rawHtml: String): String {
        return rawHtml
        /*
        return rawHtml
            .replace(Regex("""<href""", RegexOption.IGNORE_CASE), "<a href=")
            .replace(Regex("""</href[^>]*>""", RegexOption.IGNORE_CASE), "</a>")

         */
    }

    // 2. Descarga y limpia la página objetivo
    private fun obtenerHtmlYLimpiar(uriBase: String, uriSecundario:String, uriFichero: String ): Document {
        val content = RecursosUtils.leerFicheroComoTexto(rutaArchivo=File(uriBase, uriSecundario).path, archivo = uriFichero)
        val htmlSanitizado = sanitizarHtml(content)
        return Jsoup.parse(htmlSanitizado, File(uriBase, uriFichero).toString())
    }

    // 3. Orquestador del parseo
    fun obtainAndParseURLBook(
        libroDto: LibroDto,
        urlBase: String,
        urlNotas: String? = null
    ): DocumentoBiblico {

        logger.debug { "[FMMP] obtainAndParseURLBook: ${libroDto.es}" }

        val docHtml = obtenerHtmlYLimpiar(uriBase = urlBase, uriSecundario = appConfig.files.uriLibros ,uriFichero = libroDto.url)
//        val seccionesIntro = parserHTMLTexts(docHtml)
        val caps = parsearHTMLDocument(docHtml)

        return DocumentoBiblico(
            id = libroDto.id,
            key = libroDto.esKey,
            titulo = libroDto.es,
            abreviatura = libroDto.abbr,
            categoria = CategoriaDocumento.LIBRO,
            orden = libroDto.id,
            urlOrigen = urlBase + libroDto.url,
//            seccionesIntroduccion = seccionesIntro,
            capitulosEsperados = libroDto.chapters,
            capitulosLibro = caps,
        )
    }


    private fun parsearHTMLDocument(doc: Document): Map<Int, Capitulo> {
        val areaContenido = doc.selectFirst("div.ddbEbookContent") ?: return emptyMap()
        val contexto = ContextoParseo()

        val elementos = areaContenido.children()
        var indexInicio = elementos.indexOfFirst { it.hasClass("pba") }
        if (indexInicio == -1) indexInicio = 0

        parsearHTMLElements(elementos, indexInicio, contexto)

        // Cierre final del árbol procesado. Se envía -1 porque realmente no se ha detectado ninguno
        contexto.consolidarCapitulo(numCapDetectado = -1)
//        contexto.consolidarCapitulo(numCapDetectado = contexto.numCapituloActual)

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
                    val titulo = parsearEncabezado(elem, ctx.titulosPendientes.size + 1, nivel)

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
//                        val nota = extraerYRegistrarNota(nodo, tag, ctx.numCapituloActual, ctx.notasPendientes)
                        val nota = extraerYRegistrarNota(nodo, tag, ctx.notasPendientes)
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
                val esSpanCapital = parent?.tagName() == "span" && parent.className().startsWith("capital")

                if (!esSpanCapital && texto.isNotBlank()) {
                    // Si lo siguiente a leer es texto y hay títulos
                    if (ctx.titulosPendientes.isNotEmpty()) {
                        ctx.contenidoVersiculoActual.addAll(ctx.titulosPendientes)
                        ctx.titulosPendientes.clear()
                    }
                    addPlainTextToContent(ctx.contenidoVersiculoActual, textoLimpio)
                }
            }
        }
    }

//    private fun parsearEncabezado(elem: Element, numCapitulo: Int, orden: Int, nivel: Int): Titulo {
    private fun parsearEncabezado(elem: Element, orden: Int, nivel: Int): Titulo {
        val notas = mutableListOf<NotaAlPie>()
        val paralelos = mutableListOf<Paralelo>()
        val textoTitulo = StringBuilder()

        fun recorrerNodo(nodo: Node) {
            when (nodo) {
                is Element -> {
                    val tag = nodo.tagName().lowercase()
                    if (isTagParalelo(nodo, tag)) {
                        extraerYRegistrarParalelo(nodo, tag,paralelos)
                    }
                    if (isTagNota(nodo, tag)) {
                        extraerYRegistrarNota(nodo, tag,notas)
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
//            id = "tit_${numCapitulo}_$orden",
            id = "tit_$orden",
            texto = textoTitulo.toString().trim().replace(Regex("\\s+"), " "),
            nivel = nivel,
            llamadasNotas = notas.distinctBy { it.href }.toMutableList(),
            paralelos = paralelos.distinctBy { it.cita }.toMutableList(),
        )
    }

    private fun addPlainTextToContent(content: MutableList<IElementoVersiculo>, textoLimpio: String) {
        if (textoLimpio.isBlank() || textoLimpio == "*") return

        val ultimo = content.lastOrNull()
        if (ultimo is TextoPlano) {
            val indiceUltimo = content.lastIndex
            val primerCaracter = textoLimpio.first()
            val signosSinEspacioPrevio = setOf('.', ',', ';', ':', '?', '!', ')', ']', '»')
            val conector = if (primerCaracter in signosSinEspacioPrevio) "" else " "

            content[indiceUltimo] = TextoPlano(ultimo.texto + conector + textoLimpio)
        } else {
            content.add(TextoPlano(textoLimpio))
        }
    }

    private fun isTagNota(elem: Element, tag: String): Boolean {
        return tag.startsWith("id") || (tag == "span" && elem.attr("data-type") == "id")
    }

    private fun isTagParalelo(elem: Element, tag: String): Boolean {
        return tag.startsWith("href")
                || (tag == "span" && elem.attr("data-type") == "href")
//                || (tag == "a")
    }

    private fun extraerYRegistrarNota(elem: Element, tag: String, buffer: MutableList<NotaAlPie>): NotaAlPie? {
        val hrefAttr = elem.attr("href").ifEmpty {
            elem.outerHtml().substringAfter("href=\"", "").substringBefore("\"")
        }
        val target = hrefAttr.substringAfter("#", "")
            .ifEmpty { elem.id() }
            .ifEmpty { tag.removePrefix("id") }
        return buffer.registrarNota(idHtml = target, href = hrefAttr)
    }

    private fun extraerYRegistrarParalelo(nodo: Node, tag: String, buffer: MutableList<Paralelo>) {
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

    /**
     * TEXTs
     * Desde aquí se parsean los textos
     */
    // 3. Orquestador del parseo
    fun obtainAndParseURLText(
        textoDto: TextoDto,
        urlBase: String,
        urlNotas: String? = null
    ): TextoBiblico {

        logger.debug { "[FMMP] obtainAndParseURLBook: ${textoDto.es}" }

        val docHtml = obtenerHtmlYLimpiar(uriBase = urlBase, uriSecundario = appConfig.files.uriTextos ,uriFichero = textoDto.url)
        val intro = parserHTMLTexts(docHtml)

        return TextoBiblico(
            id = textoDto.id,
            titulo = textoDto.es,
            categoria = CategoriaDocumento.INTRODUCCION,
            orden = textoDto.id,
            urlOrigen = textoDto.url,
            seccionIntroduccion = intro
        )
    }


    private fun parserHTMLTexts(doc: Document): SeccionIntroduccion {
        val textContext = TextParserContext()
        val content = doc.selectFirst("div.ddbEbookContent") ?: return textContext.consolidateIntroduction("")

        val elementos = content.children()
        //var indexInicio = elementos.indexOfFirst { it.hasClass("pba") }
        //if (indexInicio == -1) indexInicio = 0
        var indexInicio = 0
        parserTextElements(elementos, indexInicio, textContext)
        return textContext.consolidateIntroduction("")
    }

    /**
     * [FMMP] Cuidado, vamos por aquí pero es una vulgar copia de la otra función.
     */
    private fun parserTextElements(elements: Elements, indexInicio: Int = 0, textContext: TextParserContext) {
        for (i in indexInicio until elements.size) {
            val elem = elements[i]

            when {
                // #<hX>: Títulos
                elem.tagName() in listOf("h1", "h2", "h3", "h4") -> {
                    val nivel = elem.tagName().removePrefix("h").toIntOrNull() ?: 1
                    val titulo = parserTextHeader(elem, textContext.titulosPendientes.size + 1, nivel)
                    textContext.titulosPendientes.add(titulo)
                }

                // #<p>: Párrafos de texto
                elem.tagName() == "p" && (elem.hasClass("ddb6") || elem.hasClass("noindent") || (elem.hasClass("top5"))) -> {
                    processParagraph(elem, textContext)
                }

                // #<div>: Estructura anidada
                elem.tagName() == "div" -> {
                    parserTextElements(elements = elem.children(), indexInicio = 0, textContext = textContext)
                }
            }
        }

    }

    private fun parserTextHeader(elem: Element, orden: Int, nivel: Int): Titulo {
        val notas = mutableListOf<NotaAlPie>()
        val paralelos = mutableListOf<Paralelo>()
        val textoTitulo = StringBuilder()

        fun traverseNode(nodo: Node) {
            when (nodo) {
                is Element -> {
                    val tag = nodo.tagName().lowercase()
                    if (isTagParalelo(nodo, tag)) {
                        extraerYRegistrarParalelo(nodo, tag,paralelos)
                    }
                    if (isTagNota(nodo, tag)) {
                        extraerYRegistrarNota(nodo, tag,notas)
                    }
                    for (hijo in nodo.childNodes()) {
                        traverseNode(hijo)
                    }
                }
                is TextNode -> {
                    val t = nodo.text()
                    if (t.isNotBlank()) textoTitulo.append(t)
                }
            }
        }

        traverseNode(elem)

        return Titulo(
            id = "tit_$orden",
            texto = textoTitulo.toString().trim().replace(Regex("\\s+"), " "),
            nivel = nivel,
            llamadasNotas = notas.distinctBy { it.href }.toMutableList(),
            paralelos = paralelos.distinctBy { it.cita }.toMutableList(),
        )
    }

    private fun processParagraph(parrafo: Element, textContext: TextParserContext) {
        // Recorrido de los nodos internos del párrafo
        for (nodoHijo in parrafo.childNodes()) {
            processInternalNode(nodoHijo, textContext)
        }

        // Notificación de fin de párrafo para el versículo en construcción
        if (textContext.currentContent.isNotEmpty()) {
            textContext.notificarFinDeParrafo()
        }
        textContext.consolidateParagrap("")
    }

    private fun processInternalNode(nodo: Node, textContext: TextParserContext) {
        when (nodo) {
            is Element -> {
                val tag = nodo.tagName().lowercase()

                when {
                    // Marcador de cursiva: Gran parte del texto es cursiva.
                    tag == "i" -> {
                        // En principio no hacemos nada, porque al final tratamos a los hijos
                    }

                    // Detección de Nota al pie
                    isTagNota(nodo, tag) -> {
//                        val nota = extraerYRegistrarNota(nodo, tag, ctx.numCapituloActual, ctx.notasPendientes)
                        val nota = extraerYRegistrarNota(nodo, tag, textContext.notasPendientes)
                        if (nota != null) {
                            // Hay que discriminar si la nota es de un título o de un párrafo
                            textContext.currentContent.add(nota)
                        }
                    }

                    // Detección de Paralelo
                    isTagParalelo(nodo, tag) -> {
                        val ref = nodo.attr("href").ifEmpty {
                            tag.substringAfter("href=").replace("\"", "").trim()
                        }
                        if (ref.isNotBlank()) {
                            textContext.paralelosParrafoActual.add(Paralelo(cita = ref, destino = ref))
                        }
                    }
                }

                for (hijo in nodo.childNodes()) {
                    processInternalNode(hijo, textContext)
                }
            }

            is TextNode -> {
                val texto = nodo.text()
                val textoLimpio = texto.trim().replace(Regex("\\s+"), " ")

                // Por si hay que tratar algo del padre
                val parent = nodo.parent() as? Element

                if (textoLimpio.isNotBlank()) {
                    // Si lo siguiente a leer es texto y hay títulos
                    if (textContext.titulosPendientes.isNotEmpty()) {
                        textContext.currentContent.addAll(textContext.titulosPendientes)
                        textContext.titulosPendientes.clear()
                    }
                    addPlainTextToContent(textContext.currentContent, textoLimpio)
                }
            }
        }
    }


}