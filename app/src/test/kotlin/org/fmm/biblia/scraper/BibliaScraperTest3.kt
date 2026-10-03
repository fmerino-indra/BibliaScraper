package org.fmm.biblia.scraper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.fmm.biblia.scraper.data.dto.LibroDto
import org.junit.jupiter.api.Test
import org.fmmp.biblia.scraper.utils.RecursosUtils

class BibliaScraperTest3 {
    @Test
    fun generarJsonConteosReales() {
        val fileName = "textos-es.json"
        val rutaHtml = "textos-json"

        val jsonTexto = RecursosUtils.leerFicheroComoTexto(rutaArchivo = "$rutaHtml/", archivo=fileName)
        val jsonElement = Json.parseToJsonElement(jsonTexto).jsonObject

        val resultado = jsonElement.mapValues { (libroKey, libroObj) ->
            println("libroKey: $libroKey")
            libroObj.jsonObject.mapValues { (key, capituloArray) ->
                val num = capituloArray.jsonArray.count { it.jsonObject.containsKey("verse") }
                println("${key}: $num")
                // Cuenta únicamente los elementos de tipo versículo (que contienen la propiedad "verse")
                num
            }
        }

        guardarJson(resultado)
        println(Json { prettyPrint = true }.encodeToString(resultado))

        val jsonTexto2 = RecursosUtils.leerFicheroComoTexto(rutaArchivo = "json", archivo = fileName)
//        val json = Json.decodeFromString<>(jsonTexto2)
    }
    fun guardarJson(json: Map<String,Map<String, Int>>) {
//        val rutaArchivo: String="json/%02d-${documento.key}.json".format(documento.id)
        val rutaArchivo: String="validaciones-json-v2/bible-chapters.json"

        RecursosUtils.guardarDocumentoBiblicoJson(json, rutaArchivo)
    }


}