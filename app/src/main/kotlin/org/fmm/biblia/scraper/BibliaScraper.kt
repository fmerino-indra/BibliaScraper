package org.fmm.biblia.scraper

import org.fmm.biblia.scraper.data.Capitulo
import org.fmm.biblia.scraper.data.CategoriaDocumento
import org.fmm.biblia.scraper.data.ContextoParseo
import org.fmm.biblia.scraper.data.DocumentoBiblico
import org.fmm.biblia.scraper.data.IElementoVersiculo
import org.fmm.biblia.scraper.data.NotaAlPie
import org.fmm.biblia.scraper.data.Paralelo
import org.fmm.biblia.scraper.data.SeccionIntroduccion
import org.fmm.biblia.scraper.data.TextoPlano
import org.fmm.biblia.scraper.data.Titulo
import org.fmm.biblia.scraper.data.Versiculo
import org.fmm.biblia.scraper.data.registrarNota
import org.fmm.biblia.scraper.data.registrarParalelo
import org.fmm.biblia.scraper.data.vincularA
import org.fmm.biblia.scraper.data.addListIElementVersiculoToVersiculo
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements

object BibliaScraper {

    // 1. Sanitizador para limpiar el HTML malformado antes de que Jsoup cree el DOM
    private fun sanitizarHtml(rawHtml: String): String {
        return rawHtml
//            .replace(Regex("""<href(\s+[^>]*)?>""", RegexOption.IGNORE_CASE), "<span data-type=\"href\"$1>")
//            .replace(Regex("""</href>""", RegexOption.IGNORE_CASE), "</span>")
//            .replace(Regex("""<id(\s+[^>]*)?>""", RegexOption.IGNORE_CASE), "<span data-type=\"id\"$1>")
//            .replace(Regex("""</id>""", RegexOption.IGNORE_CASE), "</span>")
    }

    // 2. Descarga y limpia la página objetivo
    private fun descargarYLimpiar(url: String): Document {
        val respuesta = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (X11; Fedora; Linux x86_64)")
            .timeout(100_000)
            .execute()

        val htmlSanitizado = sanitizarHtml(respuesta.body())
        return Jsoup.parse(htmlSanitizado, url)
    }

    // 3. Orquestador del parseo
    fun parsearURLBible(
        idDoc: String,
        titulo: String,
        urlTexto: String,
        urlNotas: String? = null
    ): DocumentoBiblico {

        // A) Descargar y parsear el documento principal
        val docHtml = descargarYLimpiar(urlTexto)

        val seccionesIntro = parsearSeccionesIntroduccion(docHtml)

        // Aquí, vamos a partir este método
//        val capitulos = parsearCapitulosDesdeArea(docHtml)
        val caps = parsearHTMLDocument(docHtml)

        return DocumentoBiblico(
            id = idDoc,
            titulo = titulo,
            categoria = CategoriaDocumento.LIBRO,
            orden = 1,
            urlOrigen = urlTexto,
            seccionesIntroduccion = seccionesIntro,
            capitulosLibro = caps,
        )
    }

    private fun parsearSeccionesIntroduccion(doc: Document): List<SeccionIntroduccion> {
        // Lógica de extracción de párrafos de introducción
        return emptyList()
    }

    private fun parsearHTMLDocument(doc: Document): List<Capitulo> {
        val areaContenido = doc.selectFirst("div.ddbEbookContent") ?: return emptyList()

        // Inicializamos nuestro estado compartido
        val contexto = ContextoParseo()


        //val capitulos = mutableListOf<Capitulo>()

        // Hijos de <div class="ddbEbookContent"> comenzando por indexInicio
        // (siempre hay una clase pba por ahí justo después del índice del libro.
        val elementos = areaContenido.children()
        var indexInicio = elementos.indexOfFirst {
            it.hasClass("pba")
        }
        if (indexInicio == -1)
            indexInicio = 0

        parsearHTMLElements(elementos, indexInicio, contexto)

        // Consolidamos el último capítulo al terminar todo el árbol
        contexto.consolidarCapitulo()

        return contexto.capitulos
    }

    private fun parsearHTMLElements(elements: Elements, indexInicio: Int=0, ctx: ContextoParseo) {
        //val capitulos = mutableListOf<Capitulo>()

//        var numCapituloActual = 1
//        var versiculosActuales = mutableListOf<Versiculo>()
//        var titulosPendientes = mutableListOf<Titulo>()

        for (i in indexInicio until elements.size) {
            val elem = elements[i]

            when {
                // #<hX>: Títulos (Pueden contener paralelos y notas en cualquier orden; no versículos)
                elem.tagName() in listOf("h1", "h2", "h3", "h4") -> {
                    val nivel = elem.tagName().removePrefix("h").toIntOrNull() ?: 1
                    val titulo = parsearEncabezado(elem, ctx.numCapituloActual, ctx.titulosPendientes.size + 1, nivel)
                    ctx.titulosPendientes.add(titulo)
                }

                // #<p>: Párrafos de texto (Pueden contener cambio de capítulo, versículos, notas y paralelos)
                elem.tagName() == "p" && (elem.hasClass("ddb6") || elem.hasClass("noindent")) -> {

                    // ## Detección de cambio de capítulo por <span class="capital1">
                    val spanCapital = elem.selectFirst("span.capital1")
                    if (spanCapital != null) {
                        val numCapDetectado = spanCapital.text().replace(Regex("[^0-9]"), "").toIntOrNull()
                        if (numCapDetectado != null) {
                            ctx.consolidarCapitulo()
                            ctx.numCapituloActual = numCapDetectado
                        }
                    }

                    // Extraemos pasando el capítulo que figura en el contexto
                    val versiculosDelParrafo = extraerVersiculosDeElemento(elem, ctx.numCapituloActual, ctx.titulosPendientes)

                    // [FMMP] Versículos huérfanos,
                    // significa que la segunda parte de un versículo está en párrafo aparte.
                    for (v in versiculosDelParrafo) {
                        if (v.numero == -1) {
                            if (ctx.versiculosActuales.isNotEmpty()) {
                                val ultimo = ctx.versiculosActuales.last()
                                //[FMMP] Coger el último y añadirle un <BR>
                                //ultimo.addParagraph()
                                ultimo.contenido.addListIElementVersiculoToVersiculo(v.contenido)
                                ultimo.paralelos.addAll(v.paralelos)
                            }
                        } else {
                            ctx.versiculosActuales.add(v)
                        }
                    }
                    if (ctx.versiculosActuales.isNotEmpty()) {
                        // [FMMP] Al último versículo le añadimos un salto
                        val ultimo = ctx.versiculosActuales.last()
                        ultimo.addParagraph()
                    } else {
                        println("[FMMP]")
                    }
                } // <p>
                // 🌟 EL DIV U OTRA ESTRUCTURA ANIDADA: Nos metemos recursivamente en vivo
                elem.tagName() == "div" -> {
                    // Llamamos a este mismo método para que procese los hijos del div.
                    // IMPORTANTE: Le pasamos el mismo 'ctx'. Todo lo que descubra ahí dentro
                    // se sumará al capítulo y versículo en el que estamos actualmente.
                    // El indexInicio para sus hijos vuelve a ser 0.
                    parsearHTMLElements(elements = elem.children(), indexInicio = 0, ctx = ctx)
                }
            } //when
        } // for
    }

//    fun parsearCapitulosDesdeArea(doc: Document): List<Capitulo> {
    fun parsearCapitulosDesdeArea(doc: Element): List<Capitulo> {
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
                // #<hX>: Títulos (Pueden contener paralelos y notas en cualquier orden; no versículos)
                elem.tagName() in listOf("h1", "h2", "h3", "h4") -> {
                    val nivel = elem.tagName().removePrefix("h").toIntOrNull() ?: 1
                    val titulo = parsearEncabezado(elem, numCapituloActual, titulosPendientes.size + 1, nivel)
                    titulosPendientes.add(titulo)
                }

                // #<p>: Párrafos de texto (Pueden contener cambio de capítulo, versículos, notas y paralelos)
                elem.tagName() == "p" && (elem.hasClass("ddb6") || elem.hasClass("noindent")) -> {

                    // ## Detección de cambio de capítulo por <span class="capital1">
                    val spanCapital = elem.selectFirst("span.capital1")
                    if (spanCapital != null) {
                        val numCapDetectado = spanCapital.text().replace(Regex("[^0-9]"), "").toIntOrNull()

                        // [FMMP] Me pasé de listo, crea el capítulo anterior
                        // cuando encuentra uno nuevo
                        /*
                        if (numCapDetectado != null) {
                            numCapituloActual = numCapDetectado
                        }

                         */
                        if (numCapDetectado != null) {
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

                    val versiculosDelParrafo = extraerVersiculosDeElemento(elem, numCapituloActual, titulosPendientes)

                    // [FMMP] Versículos huérfanos, significa que la segunda parte de un versículo está en párrafo aparte.
                    for (v in versiculosDelParrafo) {
                        if (v.numero == -1) {
                            if (versiculosActuales.isNotEmpty()) {
                                val ultimo = versiculosActuales.last()
                                //[FMMP] Coger el último y añadirle un <BR>
                                ultimo.addParagraph()
                                ultimo.contenido.addListIElementVersiculoToVersiculo(v.contenido)
                                ultimo.paralelos.addAll(v.paralelos)
                            }
                        } else {
                            versiculosActuales.add(v)
                        }
                    }
                    if (versiculosActuales.isNotEmpty()) {
                        // [FMMP] Al último versículo le añadimos un salto
                        val ultimo = versiculosActuales.last()
                        ultimo.addParagraph()
                    } else {
                        println("[FMMP]")
                    }
                } // <p>
                else -> { // un <div>, por ejemplo
                    for (nodoHijo in elem.childNodes()) {
                        if (nodoHijo is Element) {
                            parsearCapitulosDesdeArea(nodoHijo)
                        }
                    }
                }
            } //when

        } // for
        // Ultimo capítulo
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
     * Procesa etiquetas <hX> extrayendo de forma agnóstica al orden sus notas y paralelos.
     */
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
                        extraerYRegistrarNota(nodo,tag, numCapitulo, notas)
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

    /**
     * Recorre linealmente las entrañas del <p>, extrayendo versículos a medida que los encuentra
     * en el árbol anidado de notas y paralelos.
     */
    private fun extraerVersiculosDeElemento(parrafo: Element, numCapitulo: Int, titulosPrevios: MutableList<Titulo>): List<Versiculo> {
        val resultado = mutableListOf<Versiculo>()

        var idHtmlVerActual = ""
        var numVerActual = -1
        val textoVerActual = StringBuilder()
        val textoVerLista = mutableListOf<IElementoVersiculo>()
        val paralelosVerActual = mutableListOf<Paralelo>()
        val notasPendientes = mutableListOf<NotaAlPie>()

        var versiculoHuerfano=false

        fun consolidarVersiculo() {
            if (numVerActual != -1) {
                val textoLimpio = textoVerActual.toString().trim().replace(Regex("\\s+"), " ")
                val nuevoVersiculo = Versiculo(
                    id = "ver_${numCapitulo}_$numVerActual",
                    idHtml = idHtmlVerActual,
                    capitulo = numCapitulo,
                    numero = numVerActual,
                    paralelos = paralelosVerActual.distinct().toMutableList(),
                )
                nuevoVersiculo.contenido.addListIElementVersiculoToVersiculo(titulosPrevios)
                nuevoVersiculo.contenido.addListIElementVersiculoToVersiculo(textoVerLista)

                titulosPrevios.clear()
                notasPendientes.vincularA(nuevoVersiculo)
                resultado.add(nuevoVersiculo)

                textoVerActual.clear()
                textoVerLista.clear()
                paralelosVerActual.clear()
                numVerActual = -1
            } else {
                // Este caso es que exista un fragmento que no se ha asignado a un versículo ya creado en párrafo anterior.
                // Puede arrastrar títulos, etc.
                // Por aquí pasa cuando es el primer versículo de un párrafo.
                //println("[FMMP] ${Thread.currentThread().stackTrace[1].methodName} Capítulo: $numCapitulo | Texto: $textoLimpio ")

                val nuevoVersiculo = Versiculo(
                    id = "ver_${numCapitulo}_$numVerActual",
                    idHtml = idHtmlVerActual,
                    capitulo = numCapitulo,
                    numero = numVerActual,
                    paralelos = paralelosVerActual.distinct().toMutableList()
                )
                nuevoVersiculo.contenido.addListIElementVersiculoToVersiculo(titulosPrevios)
                nuevoVersiculo.contenido.addListIElementVersiculoToVersiculo(textoVerLista)

                titulosPrevios.clear()
                notasPendientes.vincularA(nuevoVersiculo)
                resultado.add(nuevoVersiculo)

                textoVerActual.clear()
                textoVerLista.clear()
                paralelosVerActual.clear()
                numVerActual = -1
                versiculoHuerfano = false
            }
        }

        fun consolidarVersiculoHuerfano() {

            if (versiculoHuerfano) {
                println("[FMMP] ${Thread.currentThread().stackTrace[1].methodName} Capítulo: $numCapitulo | Texto: $textoVerLista | Paralelos: $paralelosVerActual | Notas: $notasPendientes | versiculoHuerfano: $versiculoHuerfano")
                //addTextoPlanoVersiculo(textoVerLista, textoLimpio)
                val nuevoVersiculo = Versiculo(
                    id = "ver_${numCapitulo}_$numVerActual",
                    idHtml = idHtmlVerActual,
                    capitulo = numCapitulo,
                    numero = numVerActual,
//                    contenido = textoVerLista.distinct().toMutableList(),
                    paralelos = paralelosVerActual.distinct().toMutableList()
                )
                nuevoVersiculo.contenido.addListIElementVersiculoToVersiculo(titulosPrevios)
                nuevoVersiculo.contenido.addListIElementVersiculoToVersiculo(textoVerLista)
                titulosPrevios.clear()

                notasPendientes.vincularA(nuevoVersiculo)
                resultado.add(nuevoVersiculo)

                textoVerActual.clear()
                textoVerLista.clear()
                paralelosVerActual.clear()
                numVerActual = -1
                versiculoHuerfano = false

            }
        }

        fun procesarNodoInterno(nodo: Node) {
            when (nodo) {
                is Element -> {
                    val tag = nodo.tagName().lowercase()

                    when {
                        // Número de versículo: punto de corte y consolidación
                        tag == "sup" && nodo.id().startsWith("v") -> {
                            if (textoVerLista.isNotEmpty() && numVerActual == -1) {
                                //println("[FMMP] ${Thread.currentThread().stackTrace[1].methodName} Capítulo: $numCapitulo | Texto: SPM")
                                versiculoHuerfano=true
                                consolidarVersiculoHuerfano()
                            }
                            consolidarVersiculo()
                            idHtmlVerActual = nodo.id()
                            numVerActual = nodo.text().replace(Regex("[^0-9]"), "").toIntOrNull() ?: -1
                            return
                        }

                        // Detección de Nota al pie
                        isTagNota(nodo, tag) -> {
                            extraerYRegistrarNota(nodo, tag, numCapitulo, notasPendientes)
                                ?.let { elemento -> textoVerLista.add(elemento) } // el nombre por defecto es it
                        }

                        // Detección de Paralelo
                        isTagParalelo(nodo, tag) -> {
                            val ref = nodo.attr("href").ifEmpty {
                                tag.substringAfter("href=").replace("\"", "").trim()
                            }
                            if (ref.isNotBlank()) {
                                paralelosVerActual.add(Paralelo(cita = ref, destino = ref))
                            }
                        }
                    }

                    for (hijo in nodo.childNodes()) {
                        procesarNodoInterno(hijo)
                    }
                }

                is TextNode -> {
                    val texto = nodo.text()
                    val textoLimpio = texto.trim().replace(Regex("\\s+"), " ")

                    // Ignora el contenido del <span class="capital1"> para no contaminar el texto del versículo
                    val parent = nodo.parent() as? Element
                    val esSpanCapital = parent?.tagName() == "span" && parent.hasClass("capital1")

//                    if (!esSpanCapital && texto.isNotBlank() && numVerActual != -1) {
                    if (!esSpanCapital && texto.isNotBlank()) {
                        if (numVerActual == -1) {

//                            println("[FMMP] ${Thread.currentThread().stackTrace[1].methodName} Capítulo: $numCapitulo | Texto: $textoLimpio  ")
//                            versiculoHuerfano=true
//                            consolidarVersiculoHuerfano(textoLimpio)
                        }
                        textoVerActual.append(textoLimpio)
                        addTextoPlanoVersiculo(textoVerLista, textoLimpio)
                        //textoVerLista.add(TextoPlano(textoLimpio))
                    }
                }
            } // when
        } // fun procesarNodoInterno

        for (nodoHijo in parrafo.childNodes()) {
            procesarNodoInterno(nodoHijo)
        }

        consolidarVersiculo()
        consolidarVersiculoHuerfano()
        //[FMMP] Añade fin de párrafo al último fragmento del último versículo
        /* Para después
        if (resultado.isNotEmpty()) {
            val ultimo = resultado.last()
            resultado[resultado.size - 1] = ultimo.copy(texto = "${ultimo.texto} [BR]")
        }
        */
        return resultado
    }

    private fun addTextoPlanoVersiculo(contenidoVersiculo: MutableList<IElementoVersiculo>, textoLimpio: String) {
        if (textoLimpio.isBlank() || textoLimpio == "*")
            return
        val ultimo = contenidoVersiculo.lastOrNull()
        if (ultimo is TextoPlano) {
            val indiceUltimo = contenidoVersiculo.lastIndex

            // 1. Tomamos el primer carácter del texto que va a entrar
            val primerCaracter = textoLimpio.first()
            // 2. Definimos los signos que NO llevan espacio antes (puedes añadir más si quieres)
            val signosSinEspacioPrevio = setOf('.', ',', ';', ':', '?', '!', ')', ']', '»')
            // 3. Decidimos el conector: si es un signo, nada ""; si es palabra, un espacio " "
            val conector = if (primerCaracter in signosSinEspacioPrevio) "" else " "

            // 4. Reemplazamos el último elemento con los textos combinados
            contenidoVersiculo[indiceUltimo]=TextoPlano(ultimo.texto + conector + textoLimpio)

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

    /**
     * Helper unificado para extraer paralelos en cualquier nodo DOM
     * <href="m01gn.html#m2"
     */
    private fun extraerYRegistrarParalelo(nodo: Node, tag: String, capitulo: Int, buffer: MutableList<Paralelo>
    ) {
        if (nodo is Element) {
            // <href="m01gn.html#m2" ...> -> Contenedor de pasajes paralelos. Referencia a pasaje paralelo
            if (tag.startsWith("href")) {
                val ref = nodo.attr("href")
                    .ifEmpty { tag.substringAfter("href=").replace("\"", "").trim() }
                buffer.registrarParalelo(ref, ref)
            }
        }
    }

}