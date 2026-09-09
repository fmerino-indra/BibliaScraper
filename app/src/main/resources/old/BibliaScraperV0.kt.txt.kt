package org.fmm.biblia.scraper

import org.fmm.biblia.scraper.data.Capitulo
import org.fmm.biblia.scraper.data.CategoriaDocumento
import org.fmm.biblia.scraper.data.DocumentoBiblico
import org.fmm.biblia.scraper.data.NotaAlPie
import org.fmm.biblia.scraper.data.Paralelo
import org.fmm.biblia.scraper.data.SeccionIntroduccion
import org.fmm.biblia.scraper.data.Titulo
import org.fmm.biblia.scraper.data.Versiculo
import org.fmm.biblia.scraper.data.registrarNota
import org.fmm.biblia.scraper.data.registrarParalelo
import org.fmm.biblia.scraper.data.vincularA
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object `BibliaScraperV0.kt.txt` {

    // 1. Sanitizador para limpiar el HTML malformado antes de que Jsoup cree el DOM
    private fun sanitizarHtmlV01(rawHtml: String): String {
        return rawHtml
            // Convierte etiquetas corruptas <href="..."> en <a href="...">
            .replace(Regex("""<href=([^>]+)>""", RegexOption.IGNORE_CASE), "<a href=$1>")
            .replace(Regex("""</href>""", RegexOption.IGNORE_CASE), "</a>")
    }

    private fun sanitizarHtmlV02(rawHtml: String): String {
        return rawHtml
            // Sustituye cualquier etiqueta de apertura <href="...
            .replace(Regex("""<href=""", RegexOption.IGNORE_CASE), "<a href=")
            // Sustituye los cierres </href...
            .replace(Regex("""</href[^>]*>""", RegexOption.IGNORE_CASE), "</a>")
    }

    private fun sanitizarHtml(rawHtml: String): String {
        return rawHtml
            // Sustituye <href ...> y </href> por <span data-type="href" ...> y </span>
            .replace(Regex("""<href(\s+[^>]*)?>""", RegexOption.IGNORE_CASE), "<span data-type=\"href\"$1>")
            .replace(Regex("""</href>""", RegexOption.IGNORE_CASE), "</span>")
            // Sustituye <id ...> y </id> por <span data-type="id" ...> y </span>
            .replace(Regex("""<id(\s+[^>]*)?>""", RegexOption.IGNORE_CASE), "<span data-type=\"id\"$1>")
            .replace(Regex("""</id>""", RegexOption.IGNORE_CASE), "</span>")
    }
    // 2. Descarga y limpia la página objetivo
    fun descargarYLimpiar(url: String): Document {
        val respuesta = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (X11; Fedora; Linux x86_64)")
            .timeout(10_000)
            .execute()

        val htmlSanitizado = sanitizarHtml(respuesta.body())
        return Jsoup.parse(htmlSanitizado, url)
    }

    // 3. Orquestador del parseo
    fun parsearDocumento(
        idDoc: String,
        titulo: String,
        urlTexto: String,
        urlNotas: String? = null
    ): DocumentoBiblico {
        /*
        // A) Extraer notas globales primero si existen
        val notasGlobales = urlNotas?.let { parsearNotas(it) } ?: emptyList()
        val mapaNotasHtmlAId = notasGlobales.mapNotNull { nota ->
            nota.idHtml?.let { it to nota.id }
        }.toMap()

         */

        // B) Descargar y parsear el documento principal
        val docHtml = descargarYLimpiar(urlTexto)

//        val seccionesIntro = parsearSeccionesIntroduccion(docHtml, mapaNotasHtmlAId)
//        val capitulos = parsearCapitulos(docHtml, mapaNotasHtmlAId)
        val seccionesIntro = parsearSeccionesIntroduccion(docHtml)
        val capitulos = parsearCapitulosDesdeArea(docHtml)

        return DocumentoBiblico(
            id = idDoc,
            titulo = titulo,
            categoria = CategoriaDocumento.LIBRO,
            orden = 1,
            urlOrigen = urlTexto,
            seccionesIntroduccion = seccionesIntro,
            capitulosLibro = capitulos,
//            notasAlPieGlobales = notasGlobales
        )
    }

    private fun parsearNotas(urlNotas: String): List<NotaAlPie> {
        val docNotas = descargarYLimpiar(urlNotas)
        // Lógica de extracción de div/p de notas asociándoles un ID entero autoincremental
        return emptyList()
    }

    private fun parsearSeccionesIntroduccion(doc: Document): List<SeccionIntroduccion> {
        // Lógica de extracción de párrafos de introducción
        return emptyList()
    }

    private fun parsearCapitulosDesdeArea(doc: Document): List<Capitulo> {
        val areaContenido = doc.selectFirst("div.ddbEbookContent") ?: return emptyList()
        val capitulos = mutableListOf<Capitulo>()

        var numCapituloActual = 1
        var versiculosActuales = mutableListOf<Versiculo>()
        var titulosPendientes = mutableListOf<Titulo>()

        val elementos = areaContenido.children()
        var indexInicio = elementos.indexOfFirst { it.hasClass("pba") }
        if (indexInicio == -1) indexInicio = 0

        for (i in indexInicio until elementos.size) {
            val elem = elementos[i]

            when {
                // Títulos y encabezados
                elem.tagName() in listOf("h1", "h2", "h3", "h4") -> {
                    val nivel = when (elem.tagName()) {
                        "h1" -> 1
                        "h2" -> 2
                        "h3" -> 3
                        else -> 4
                    }

                    // Buffer de notas exclusivo para el título actual
                    val notasTitulo = mutableListOf<NotaAlPie>()

                    // Extraemos texto y capturamos posibles notas en <span data-type="id"> dentro del título
                    for (nodoHijo in elem.childNodes()) {
                        extraerNotasDeNodo(nodoHijo, numCapituloActual, notasTitulo)
                    }

                    val nuevoTitulo = Titulo(
                        id = "tit_${numCapituloActual}_${titulosPendientes.size + 1}",
                        texto = elem.text().trim(),
                        nivel = nivel
                    )

                    // Vinculamos las notas capturadas al objeto Titulo
                    notasTitulo.vincularA(nuevoTitulo)

                    titulosPendientes.add(nuevoTitulo)
                }

                // Párrafos de texto
                elem.tagName() == "p" && (elem.hasClass("ddb6") || elem.hasClass("noindent")) -> {
                    // DETECCIÓN DE CAPÍTULO POR span.capital1
                    val spanCapital = elem.selectFirst("span.capital1")
                    if (spanCapital != null) {
                        val numCapDetectado = spanCapital.text().replace(Regex("[^0-9]"), "").toIntOrNull()
                        if (numCapDetectado != null) {
                            // Guardar capítulo anterior si ya había contenido
                            if (versiculosActuales.isNotEmpty()) {
                                capitulos.add(
                                    Capitulo(
                                        id = "cap_$numCapituloActual",
                                        numero = numCapituloActual,
                                        titulosPrevios = titulosPendientes.toList(),
                                        versiculos = versiculosActuales.toList()
                                    )
                                )
                                versiculosActuales = mutableListOf()
                                titulosPendientes = mutableListOf()
                            }
                            numCapituloActual = numCapDetectado
                        }
                    }

                    val versiculosDelParrafo = extraerVersiculosDeElemento(elem, numCapituloActual)

                    for (v in versiculosDelParrafo) {
                        if (v.numero == -1) {
                            // Si es continuación de un párrafo anterior, ensamblamos el texto al último versículo guardado
                            if (versiculosActuales.isNotEmpty()) {
                                val ultimo = versiculosActuales.removeAt(versiculosActuales.size - 1)
                                val textoCombinado = "${ultimo.texto} ${v.texto}".replace(Regex("\\s+"), " ")
                                val notasCombinadas = (ultimo.llamadasNotas + v.llamadasNotas).distinct()

                                versiculosActuales.add(ultimo.copy(texto = textoCombinado, llamadasNotas = notasCombinadas))
                            }
                        } else {
                            versiculosActuales.add(v)
                        }
                    }
                }
            }
        }

        // Guardar el último capítulo procesado
        if (versiculosActuales.isNotEmpty()) {
            capitulos.add(
                Capitulo(
                    id = "cap_$numCapituloActual",
                    numero = numCapituloActual,
                    titulosPrevios = titulosPendientes.toList(),
                    versiculos = versiculosActuales.toList()
                )
            )
        }

        return capitulos
    }

    /**
     * Extraer Versiculos
     */
    private fun extraerVersiculosDeElemento(
        parrafo: Element,
        numCapitulo: Int,
    ): List<Versiculo> {
        val resultado = mutableListOf<Versiculo>()

        var idHtmlVerActual = ""
        var numVerActual = -1
        val textoVerActual = StringBuilder()
        val notasVerActual = mutableListOf<Int>()
        val paralelosVerActual = mutableListOf<Paralelo>()
        val notasPendientes = mutableListOf<NotaAlPie>()

        // Cierra el versículo activo en el acumulador y lo añade a la lista
        fun consolidarVersiculo() {
            if (numVerActual != -1) {
                val textoLimpio = textoVerActual.toString()
                    .trim()
                    .replace(Regex("\\s+"), " ")
                val nuevoVersiculo =
                    Versiculo(
                        id = "ver_${numCapitulo}_$numVerActual",
                        idHtml = idHtmlVerActual,
                        numero = numVerActual,
                        texto = textoLimpio,
                        paralelos = paralelosVerActual.distinct()
                    )
                notasPendientes.vincularA(nuevoVersiculo)
                resultado.add(nuevoVersiculo)

                textoVerActual.clear()
                notasVerActual.clear()
                paralelosVerActual.clear()
                numVerActual = -1
            }
        }

        // Inspecciona recursivamente cada nodo del párrafo en orden secuencial
        fun procesarNodoInterno(nodo: Node) {
            when (nodo) {
                is Element -> {
                    val tag = nodo.tagName().lowercase()

                    when {
                        // # <sup> -> Marca de número de versículo
                        tag == "sup" && nodo.id().startsWith("v") -> {
                            consolidarVersiculo()
                            idHtmlVerActual = nodo.id()
                            numVerActual = nodo.text().replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                            return
                        }

                        // # <id...> -> Contenedor o referencia a Nota al Pie
                        tag.startsWith("id") -> {
                            val hrefAttr = nodo.attr("href").ifEmpty {
                                tag.substringAfter("href=\"", "").substringBefore("\"")
                            }
                            // Extrae la referencia del href o del id propio del nodo malformado
                            val target = nodo.attr("href").substringAfter("#", "")
                                .ifEmpty { nodo.id() }
                                .ifEmpty { tag.removePrefix("id") } // Por si el parser pegó el id al nombre de la etiqueta
                                .ifEmpty { tag.substringAfter("id=").replace("\"", "").trim()}

                            notasPendientes.registrarNota(idHtml = target, href = hrefAttr, capitulo = numCapitulo)
                        }

                        // # <href...> -> Contenedor de pasajes paralelos. Referencia a pasaje paralelo
                        tag.startsWith("href") -> {
                            val ref = nodo.attr("href")
                                .ifEmpty { tag.substringAfter("href=").replace("\"", "").trim() }
                            val paraleloRef=Paralelo(
                                cita = ref,
                                destino = ref,
                            )
                            if (ref.isNotBlank()) {
                                paralelosVerActual.add(paraleloRef)
                            }
                        }

                        // # <i> -> Formato cursiva o notas directas
                        tag == "i" -> {
                            // Si contiene una referencia a nota interna
                            val target = nodo.attr("href").substringAfter("#", "").ifEmpty { nodo.id() }
                            if (target.isNotEmpty()) {
                            }
                        }
                    }

                    // Recorrer nodos hijos para extraer el texto y/o subetiquetas
                    for (hijo in nodo.childNodes()) {
                        procesarNodoInterno(hijo)
                    }
                }

                is TextNode -> {
                    // # Texto -> Se añade al versículo que esté activo en este momento
                    val texto = nodo.text()
                    if (texto.isNotBlank() && numVerActual != -1) {
                        textoVerActual.append(texto)
                    }
                }
            }
        }

        // Recorrido de los nodos directamente contenidos en el <p>
        for (nodoHijo in parrafo.childNodes()) {
            procesarNodoInterno(nodoHijo)
        }

        // Guardar el último versículo hallado en el párrafo
        consolidarVersiculo()

        // Añadir marca [BR] al último versículo extraído para indicar fin de párrafo
        if (resultado.isNotEmpty()) {
            val ultimo = resultado.last()
            resultado[resultado.size - 1] = ultimo.copy(texto = "${ultimo.texto} [BR]")
        }

        return resultado
    }

    /**
     * Helper unificado para extraer notas en cualquier nodo DOM
     */
    private fun extraerNotasDeNodo(
        nodo: Node,
        capitulo: Int,
        bufferNotas: MutableList<NotaAlPie>
    ) {
        if (nodo is Element) {
            val tag = nodo.tagName().lowercase()

            // Tras sanitizarHtml, las notas están en etiquetas <span> con data-type="id" o que inicien por "id"
            if (tag.startsWith("id") || (tag == "span" && nodo.attr("data-type") == "id")) {
                val hrefAttr = nodo.attr("href").ifEmpty {
                    nodo.outerHtml().substringAfter("href=\"", "").substringBefore("\"")
                }
                val target = hrefAttr.substringAfter("#", "")
                    .ifEmpty { nodo.id() }
                    .ifEmpty { tag.removePrefix("id") }

                bufferNotas.registrarNota(idHtml = target, href = hrefAttr, capitulo = capitulo)
            }

            // [FMMP] Creo que no es necesario que sea recursivo, porque el procesamiento ya detectará las notas
//            for (hijo in nodo.childNodes()) {
//                extraerNotasDeNodo(hijo, capitulo, bufferNotas)
//            }
        }
    }

    /**
     * Helper unificado para extraer paralelos en cualquier nodo DOM
     */
    private fun extraerParalelosDeNodo(
        nodo: Node,
        capitulo: Int,
        bufferParalelos: MutableList<Paralelo>
    ) {
        if (nodo is Element) {
            val tag = nodo.tagName().lowercase()

            // # <href...> -> Contenedor de pasajes paralelos. Referencia a pasaje paralelo
            if (tag.startsWith("href")) {
                val ref = nodo.attr("href")
                    .ifEmpty { tag.substringAfter("href=").replace("\"", "").trim() }
                bufferParalelos.registrarParalelo(ref, ref)
            }

            // [FMMP] Creo que no es necesario que sea recursivo, porque el procesamiento ya detectará las notas
//            for (hijo in nodo.childNodes()) {
//                extraerParaleloDeNodo(hijo, capitulo, bufferNotas)
//            }
        }
    }
}