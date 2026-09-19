package org.fmmp.biblia.scraper.utils

import kotlin.time.Instant
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.coroutines.*
import kotlin.time.ExperimentalTime
import java.io.InputStream
import java.io.OutputStream

@Serializable
class Printer(val message: String) {
    @OptIn(ExperimentalTime::class)
    fun printMessage() = runBlocking {
        val now: Instant = Clock.System.now()
        launch {
            delay(1000L)
            println(now.toString())
        }
        println(message)
    }
}

object RecursosUtils {

    /**
     * Lee un archivo desde la carpeta src/main/resources (o src/test/resources)
     * y devuelve su contenido como String.
     *
     * @param rutaRelativa Nombre o ruta del recurso (ej: "datos.json" o "json/genesis.json").
     * @return El contenido en texto del archivo.
     * @throws IllegalArgumentException Si el recurso no se encuentra en el ClassLoader.
     */
    fun leerRecursoComoTexto(rutaRelativa: String): String {
        val stream: InputStream = object {}.javaClass.classLoader.getResourceAsStream(rutaRelativa)
            ?: throw IllegalArgumentException("No se encontró el recurso en la ruta: $rutaRelativa")

        return stream.bufferedReader().use { it.readText() }
    }
}