package org.fmm.biblia.scraper.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
sealed interface Contenido {
    val id: String
    val idHtml: String?
    val llamadasNotas: List<NotaAlPie>
}

@Serializable
sealed interface ContenedorBloque : Contenido {
    val titulosPrevios: List<Titulo>
}

@Serializable
data class Titulo(
    override val id: String,
    override val idHtml: String? = null,
    val texto: String,
    val nivel: Int = 1,
    override val llamadasNotas: List<NotaAlPie> = emptyList(),
) : Contenido

@Serializable
data class Parrafo(
    override val id: String,
    override val idHtml: String? = null,
    val texto: String,
    override val llamadasNotas: List<NotaAlPie> = emptyList(),
) : Contenido

@Serializable
data class Versiculo(
    override val id: String,
    override val idHtml: String? = null,
    val numero: Int,
    val texto: String,
    override val llamadasNotas: List<NotaAlPie> = mutableListOf(),
    val paralelos: List<Paralelo> = emptyList()
) : Contenido

@Serializable
data class Capitulo(
    override val id: String,
    override val idHtml: String? = null,
    val numero: Int,
    override val titulosPrevios: List<Titulo> = emptyList(),
    val versiculos: List<Versiculo> = emptyList(),
    override val llamadasNotas: List<NotaAlPie> = emptyList(),
) : ContenedorBloque

@Serializable
data class SeccionIntroduccion(
    override val id: String,
    override val idHtml: String? = null,
    override val titulosPrevios: List<Titulo> = emptyList(),
    val parrafos: List<Parrafo> = emptyList(),
    override val llamadasNotas: List<NotaAlPie> = emptyList(),
) : ContenedorBloque

@Serializable
data class NotaAlPie(
//    val id: Int=0,
    val idHtml: String, // Ej: ngn002
    val href: String, // Ej: "notas01gn.xhtml#ngn002ref"
    val capitulo: Int,
    val texto:String="" // Se rellenará al parsear el archivo de notas correspondiente
//    val referencia: String? = null
)

// 2. Funciones de extensión sobre la lista de notas del ámbito del parser
fun MutableList<NotaAlPie>.registrarNota(idHtml:String, href: String, capitulo: Int) {
    if (href.isNotBlank()) {
        add(NotaAlPie(idHtml=idHtml, href = href, capitulo = capitulo))
    }
}

fun MutableList<NotaAlPie>.vincularA(contenido: Contenido) {
    if (isNotEmpty()) {
        (contenido.llamadasNotas as? MutableList<NotaAlPie>)?.addAll(this.distinctBy { it.href })

        clear() // Se vacía el buffer local para el siguiente bloque
    }
}

@Serializable
data class Paralelo(
    val cita: String,         // Texto visible (ej: "Mt 3,13")
    val destino: String       // Enlace interno/archivo (ej: "m01gn.xhtml#m2")
)

enum class CategoriaDocumento { LIBRO, INTRODUCCION }
enum class TipoTestamento { ANTIGUO_TESTAMENTO, NUEVO_TESTAMENTO, OTRO }

@Serializable
data class DocumentoBiblico(
    val id: String,
    val idHtml: String? = null,
    val titulo: String,
    val categoria: CategoriaDocumento,
    val testamento: TipoTestamento = TipoTestamento.OTRO,
    val orden: Int,
    val urlOrigen: String,
    val seccionesIntroduccion: List<SeccionIntroduccion> = emptyList(),
    val capitulosLibro: List<Capitulo> = emptyList(),
//    val notasAlPieGlobales: List<NotaAlPie> = emptyList()
)