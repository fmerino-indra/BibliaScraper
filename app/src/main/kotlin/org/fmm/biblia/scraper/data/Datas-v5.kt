package org.fmm.biblia.scraper.data

import kotlinx.serialization.Serializable

@Serializable
sealed interface Notable {
    val id: String
    val idHtml: String?
    val llamadasNotas: List<NotaAlPie>
}

@Serializable
sealed interface Paralelizable {
    val paralelos: List<Paralelo>
}

@Serializable
sealed interface Titulable : Notable {
    val titulosPrevios: List<Titulo>
}

@Serializable // <-- Indispensable en la interfaz sellada
sealed interface IElementoVersiculo

@Serializable
enum class TipoSalto { NINGUNO, PUNTO_APARTE, SANGRA_POETICA, SALTO_LINEA, BLOQUE_CITACION, CABECERA_ACROSTICO}

@Serializable
data class TextoPlano(
    val texto: String,
    val saltoPrevio: TipoSalto? = null
):IElementoVersiculo


@Serializable
data class NotaAlPie(
    val idHtml: String,
    val href: String,
    val capitulo: Int,
    val texto: String = "" // Pendiente de definir
    // Las notas al pie van a tener párrafos y citas entre sus frases.
):IElementoVersiculo
@Serializable
data class Paralelo( // Pendiente de definir
    val cita: String,
    val destino: String
    // Falta definición de Cita From, Cita To
)

@Serializable
data class Titulo(
    override val id: String,
    override val idHtml: String? = null,
    override val llamadasNotas: List<NotaAlPie> = mutableListOf(),
    override val paralelos: List<Paralelo> = mutableListOf(),
    val texto: String, // Pendiente de definir
    val nivel: Int = 1
) : Notable, Paralelizable, IElementoVersiculo

@Serializable
data class Parrafo(
    override val id: String,
    override val idHtml: String? = null,
    override val llamadasNotas: List<NotaAlPie> = emptyList(),
    val texto: String // Pendiente de definir
) : Notable

@Serializable
data class Versiculo(
    override val id: String,
    override val idHtml: String? = null,
    override val llamadasNotas: List<NotaAlPie> = mutableListOf(),
    override val titulosPrevios: List<Titulo> = emptyList(),       // Complemento: perícopas intermedias
    override val paralelos: MutableList<Paralelo> = mutableListOf(),
    val capitulo: Int,
    val numero: Int,
    val esPoesia: Boolean = false,                         // Complemento: indicador de formato poético
    val contenido: MutableList<IElementoVersiculo> = mutableListOf()
) : Notable, Paralelizable, Titulable {

    constructor(
        id: String,
        idHtml: String? = null,
        llamadasNotas: List<NotaAlPie> = mutableListOf(),
        titulosPrevios: List<Titulo> = emptyList(),       // Complemento: perícopas intermedias
        paralelos: MutableList<Paralelo> = mutableListOf(),
        capitulo: Int,
        numero: Int,
        esPoesia: Boolean = false,                         // Complemento: indicador de formato poético
        elemento:IElementoVersiculo
    ) : this(
        id = id,
        idHtml = idHtml,
        llamadasNotas = llamadasNotas,
        titulosPrevios = titulosPrevios,
        paralelos = paralelos,
        capitulo = capitulo,
        numero = numero,
        esPoesia = esPoesia,
        contenido = mutableListOf(elemento) // <-- Convierte y crea la lista
    )

    constructor(
        id: String,
        idHtml: String? = null,
        llamadasNotas: List<NotaAlPie> = mutableListOf(),
        titulosPrevios: List<Titulo> = emptyList(),       // Complemento: perícopas intermedias
        paralelos: MutableList<Paralelo> = mutableListOf(),
        capitulo: Int,
        numero: Int,
        esPoesia: Boolean = false,                         // Complemento: indicador de formato poético
        textoString: String
    ) : this(
        id = id,
        idHtml = idHtml,
        llamadasNotas = llamadasNotas,
        titulosPrevios = titulosPrevios,
        paralelos = paralelos,
        capitulo = capitulo,
        numero = numero,
        esPoesia = esPoesia,
        elemento = TextoPlano(textoString) // <-- Convierte y crea la lista
    )
    fun addParagraph() {
        contenido.addStringToVersiculo("<p>")
    }
}

@Serializable
data class Capitulo(
    override val id: String,
    override val idHtml: String? = null,
    val numero: Int,
    override val titulosPrevios: List<Titulo> = emptyList(),
    val versiculos: List<Versiculo> = emptyList(),
    override val llamadasNotas: List<NotaAlPie> = mutableListOf(),
) : Titulable

@Serializable
data class SeccionIntroduccion(
    override val id: String,
    override val idHtml: String? = null,
    override val titulosPrevios: List<Titulo> = emptyList(),
    val parrafos: List<Parrafo> = emptyList(),
    override val llamadasNotas: List<NotaAlPie> = mutableListOf(),
) : Titulable

fun MutableList<NotaAlPie>.registrarNota(idHtml: String, href: String, capitulo: Int):NotaAlPie? {
    return NotaAlPie(idHtml, href, capitulo)
        .takeIf { href.isNotBlank() }
        ?.also { add(it) }
/*
    if (href.isNotBlank()) {
        val nota = NotaAlPie(idHtml = idHtml, href = href, capitulo = capitulo)
        add(nota)
        return nota
    }
 */
}

fun MutableList<NotaAlPie>.vincularA(notable: Notable) {
    if (isNotEmpty()) {
        (notable.llamadasNotas as? MutableList<NotaAlPie>)?.addAll(this.distinctBy { it.href })
        clear()
    }
}

fun MutableList<Paralelo>.registrarParalelo(cita: String, destino: String) {
    if (cita.isNotBlank()) {
        add(Paralelo(cita = cita, destino = destino))
    }
}

fun MutableList<Paralelo>.vincularA(contenido: Paralelizable) {
    if (isNotEmpty()) {
        (contenido.paralelos as? MutableList<Paralelo>)?.addAll(this.distinctBy { it.cita })
        clear()
    }
}

fun MutableList<IElementoVersiculo>.addStringToVersiculo(textoLimpio: String, saltoPrevio: TipoSalto? = null) {
    if (textoLimpio.isBlank() || textoLimpio == "*")
        return
    val ultimo = this.lastOrNull()
    if (ultimo is TextoPlano) {
        val indiceUltimo = this.lastIndex

        // 1. Tomamos el primer carácter del texto que va a entrar
        val primerCaracter = textoLimpio.first()
        // 2. Definimos los signos que NO llevan espacio antes (puedes añadir más si quieres)
        val signosSinEspacioPrevio = setOf('.', ',', ';', ':', '?', '!', ')', ']', '»')
        // 3. Decidimos el conector: si es un signo, nada ""; si es palabra, un espacio " "
        val conector = if (primerCaracter in signosSinEspacioPrevio) "" else " "

        // 4. Reemplazamos el último elemento con los textos combinados
        this[indiceUltimo]=TextoPlano(ultimo.texto + conector + textoLimpio, saltoPrevio = saltoPrevio)

    } else {
        this.add(TextoPlano(textoLimpio))
    }
}

fun MutableList<IElementoVersiculo>.addTextoPlanoVersiculo(textoPlano: TextoPlano) {
    val ultimo = this.lastOrNull()
    this.addStringToVersiculo(textoPlano.texto, textoPlano.saltoPrevio)
}

fun MutableList<IElementoVersiculo>.addListIElementVersiculoToVersiculo(contenido: List<IElementoVersiculo>) {
    if (contenido.isEmpty())
        return

    for (elemento: IElementoVersiculo in contenido) {
        if (elemento is TextoPlano) {
            this.addTextoPlanoVersiculo(elemento)
        } else {
            this.add(elemento)
        }

    }
}

enum class CategoriaDocumento { LIBRO, INTRODUCCION }
enum class TipoTestamento { ANTIGUO_TESTAMENTO, NUEVO_TESTAMENTO, OTRO }

@Serializable
data class DocumentoBiblico(
    val id: String,
    val idHtml: String? = null,
    val titulo: String,
    val abreviatura: String? = null,                       // Complemento: abreviatura del libro/documento
    val categoria: CategoriaDocumento,
    val testamento: TipoTestamento = TipoTestamento.OTRO,
    val orden: Int,
    val urlOrigen: String,
    val seccionesIntroduccion: List<SeccionIntroduccion> = emptyList(),
    val capitulosLibro: List<Capitulo> = emptyList()
)

class ContextoParseo {
    var numCapituloActual: Int = 1
    val capitulos: MutableList<Capitulo> = mutableListOf()
    var versiculosActuales: MutableList<Versiculo> = mutableListOf()
    var titulosPendientes: MutableList<Titulo> = mutableListOf()

    // Un método auxiliar para consolidar el capítulo actual cuando toca cambio
    fun consolidarCapitulo() {
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
    }
}