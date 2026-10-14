package org.fmm.biblia.scraper.data

/**
 * Mantiene el estado mutable global del proceso de parseo secuencial.
 */
class ContextoParseo {

    // === ESTADO DE CAPÍTULOS ===
    var numCapituloActual: Int = -1
    val capitulosHash = mutableMapOf<Int, Capitulo>()
    val versiculosAcumulados = mutableListOf<Versiculo>()

    // === ESTADO DE VERSÍCULO EN CONSTRUCCIÓN ===
    var numVersiculoActual: String = "-1"
    val contenidoVersiculoActual = mutableListOf<IElementoVersiculo>()
    val paralelosVersiculoActual = mutableListOf<Paralelo>()

    // === BUFFERS Y COLAS PENDIENTES ===
    val titulosPendientes = mutableListOf<Titulo>()
    val notasPendientes = mutableListOf<NotaAlPie>()

    /**
     * Cierra el versículo en construcción (si lo hay) e instancia el objeto [Versiculo].
     *
     * @param titulosAInyectar Lista opcional de títulos retenidos previamente que deben
     *                          anteponerse como contenido de este versículo.
     */
    fun consolidarVersiculo(titulosAInyectar: List<Titulo> = emptyList()) {
        if (numVersiculoActual != "-1" || contenidoVersiculoActual.isNotEmpty()) {
            val contenidoFinal = mutableListOf<IElementoVersiculo>()

            // 1. Añadimos el contenido propio del versículo acumulado hasta este punto
            contenidoFinal.addAll(contenidoVersiculoActual)

            // 2. Inyectamos los títulos recibidos que estaban retenidos
            contenidoFinal.addAll(titulosAInyectar)

            val nuevoVersiculo = Versiculo(
                id = "ver_${numCapituloActual}_$numVersiculoActual",
//                idHtml = idHtmlVerActual,
                capitulo = numCapituloActual,
                numero = numVersiculoActual,
                paralelos = paralelosVersiculoActual.distinct().toMutableList()
            )

            nuevoVersiculo.contenido.addAll(contenidoFinal)

            // 3. Asociamos las notas acumuladas en el buffer y reseteamos el buffer
            notasPendientes.vincularA(nuevoVersiculo)

            // 4. Guardamos el versículo en la lista temporal de versículos
            versiculosAcumulados.add(nuevoVersiculo)

            // 5. Reiniciamos el estado del versículo activo
            contenidoVersiculoActual.clear()
            paralelosVersiculoActual.clear()
            numVersiculoActual = "-1"
//            idHtmlVerActual = ""
        }
    }

    /**
     * Consolida y empaqueta el capítulo en curso.
     *
     * @param numCapDetectado Número del nuevo capítulo detectado al que se va a hacer la transición.
     *
     */
    fun consolidarCapitulo(numCapDetectado: Int) {


        // 1. Consolidamos primero el versículo que pudiera haber estado abierto en el capítulo anterior
        //    No le enviamos títulos pendientes, por lo que seguirán pendientes y se añadirán al capítulo
        consolidarVersiculo()

        // 2. En este punto:
        // a) si hay versículos claramente son del capítulo anterior.
        // b) si hay títulos claramente son del capítulo nuevo
        // masterLibros.associateBy { it.id } -> Sólo para acordarme de la sintaxis
        // masterLibros.associateByTo(mapa) { it.id }

        // Caso 3: Última llamada, realmente se hace para introducir los últimos versículos

        if (numCapDetectado == -1) {
            val capActual = capitulosHash[numCapituloActual]
            if (capActual != null) {
                if (versiculosAcumulados.isNotEmpty()) {
                    println("[FMMP] - WARNING. Último capítulo. El número de capitulo: $numCapituloActual tiene versículos sin añadir.")
                    capActual.versiculos.addAll(versiculosAcumulados)
                }
                return
            }
        }

        // 1º Capítuo del libro
        if (numCapituloActual == -1) {
            val cap = Capitulo(
                id = "cap_${numCapDetectado}",
                numero = numCapDetectado,
                titulosPrevios = titulosPendientes.toMutableList()
            )
            capitulosHash[cap.numero] = cap
            titulosPendientes.clear()
            numCapituloActual = numCapDetectado
            return
        }
        val capActual = capitulosHash[numCapituloActual]

        // Añadir los versiculosAcumulados al capítulo actual.
        if (versiculosAcumulados.isNotEmpty()) {
            capActual?.versiculos?.addAll(versiculosAcumulados)
            versiculosAcumulados.clear()
        } // Dejamos ya el capActual

        var capDetectado = capitulosHash[numCapDetectado]
        if (capDetectado == null) {
            capDetectado = Capitulo(
              id = "cap_${numCapDetectado}",
              numero = numCapDetectado,
              titulosPrevios = titulosPendientes.toMutableList()
            )
            titulosPendientes.clear()
            capitulosHash[capDetectado.numero] = capDetectado
        } // Si es diferente de null, no hay que hacer nada, pero se deja como actual
        // 3. Actualizamos el número de capítulo activo
        numCapituloActual = numCapDetectado
    }

    /**
     * Consolida y empaqueta el capítulo en curso.
     *
     * @param numCapDetectado Número del nuevo capítulo detectado al que se va a hacer la transición.
     *
     */
    fun consolidarCapituloOld(numCapDetectado: Int) {


        // 1. Consolidamos primero el versículo que pudiera haber estado abierto en el capítulo anterior
        //    No le enviamos títulos pendientes, por lo que seguirán pendientes y se añadirán al capítulo
        consolidarVersiculo()

        // 2. En este punto:
        // a) si hay versículos claramente son del capítulo anterior.
        // b) si hay títulos claramente son del capítulo nuevo
        // masterLibros.associateBy { it.id } -> Sólo para acordarme de la sintaxis
        // masterLibros.associateByTo(mapa) { it.id }

        // Este caso no puede darse, nunca existirá el que acaba de leerse.
        var cap = capitulosHash[numCapDetectado]
        if (cap != null) {
            if (versiculosAcumulados.isNotEmpty()) {
                println("[FMMP] - WARNING. Último capítulo. El número de capitulo: $numCapDetectado tiene versículos sin añadir.")
                cap.versiculos.addAll(versiculosAcumulados)
            }
            return
        }

        val capActual = capitulosHash[numCapituloActual]
        // Actual no existe -> Creamos NUEVO (no actual). Solo para -1???
        if (capActual == null) {
            cap =  Capitulo(
                id = "cap_${numCapDetectado}",
                numero = numCapDetectado,
                titulosPrevios = titulosPendientes.toMutableList()
            )
            if (versiculosAcumulados.isNotEmpty()) {
                println("[FMMP] - WARNING. El NUEVO número de capitulo: $numCapDetectado tiene versículos sin añadir")
                cap.versiculos.addAll(versiculosAcumulados)
            }
        } else {
            if (versiculosAcumulados.isNotEmpty()) {
                // Habría que añadir los versiculosAcumulados al capitulo actual.
                capActual.versiculos.addAll(versiculosAcumulados)
            } // Dejamos ya el capActual
            cap =  Capitulo(
                id = "cap_${numCapDetectado}",
                numero = numCapDetectado,
                titulosPrevios = titulosPendientes.toMutableList()
            )
        }
        capitulosHash[cap.numero] = cap
        versiculosAcumulados.clear()
        titulosPendientes.clear()

        // 3. Actualizamos el número de capítulo activo
        numCapituloActual = numCapDetectado

    }

    /**
     * Añade la señal de salto de párrafo/línea al versículo en construcción.
     */
    fun notificarFinDeParrafo() {
        if (contenidoVersiculoActual.isNotEmpty()) {
            val ultimo = contenidoVersiculoActual.lastOrNull()
            if (ultimo is TextoPlano && !ultimo.texto.endsWith("\n")) {
                contenidoVersiculoActual[contenidoVersiculoActual.lastIndex] =
                    TextoPlano(ultimo.texto + "\n")
            }
        }
    }
}