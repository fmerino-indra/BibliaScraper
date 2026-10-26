package org.fmm.biblia.scraper.data

/**
 * Mantiene el estado mutable global del proceso de parseo secuencial.
 */
class TextParserContext {

    val paragraphs = mutableListOf<Parrafo>()
    //val seccionIntroduccion:SeccionIntroduccion=SeccionIntroduccion()

    // === ESTADO DE VERSÍCULO EN CONSTRUCCIÓN ===
    val currentContent = mutableListOf<IElementoVersiculo>()

    val paralelosParrafoActual = mutableListOf<Paralelo>()

    // === BUFFERS Y COLAS PENDIENTES ===
    val titulosPendientes = mutableListOf<Titulo>()
    val notasPendientes = mutableListOf<NotaAlPie>()

    /**
     * Añade la señal de salto de párrafo/línea al versículo en construcción.
     */
    fun notificarFinDeParrafo() {
        if (currentContent.isNotEmpty()) {
            val ultimo = currentContent.lastOrNull()
            if (ultimo is TextoPlano && !ultimo.texto.endsWith("\n")) {
                currentContent[currentContent.lastIndex] =
                    TextoPlano(ultimo.texto + "\n")
            }
        }
    }

    fun consolidateParagrap(id:String) {
        if (notasPendientes.isNotEmpty()) {
            currentContent.addAll(notasPendientes)
            notasPendientes.clear()
        }
        if (titulosPendientes.isNotEmpty()) {
            currentContent.addAll(titulosPendientes)
            titulosPendientes.clear()
        }
        val paragraph = Parrafo(
            id = id,
            titulosPrevios = titulosPendientes,
            llamadasNotas = emptyList(),
            contenido = currentContent.toMutableList()
        )
        paragraphs.add(paragraph)
        currentContent.clear()
    }
    fun consolidateIntroduction(id: String): SeccionIntroduccion {
        return SeccionIntroduccion(
            id = id,
            titulosPrevios = emptyList(),
            llamadasNotas = emptyList(),
            parrafos = paragraphs.toMutableList()
        )
    }
}