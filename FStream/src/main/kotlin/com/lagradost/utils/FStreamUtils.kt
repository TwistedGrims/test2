package com.lagradost.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.utils.FstreamMediaProvidersUtils.mediaSources
import java.text.Normalizer
import kotlin.math.abs

object FStreamUtils {
    var listOfProviders: MutableMap<String, Pair<Int, String?>> = mediaSources.associateWith { // default state of the media
        Pair(1, it.sourceMainUrl)
    }.mapKeys { it.key.name }.toMutableMap()

    fun isSourceEnabled(name: String): Boolean{
        return listOfProviders[name]?.first == 1
    }
    fun getSourceMainUrl(name: String): String? {
        return listOfProviders[name]?.second.takeIf { it?.startsWith("http") == true }
    }

    fun isSourceCensored(name: String): Boolean? {
        return mediaSources.find {it.name == name}?.censored
    }

    fun writeToKey(key: String, map: MutableMap<String, Pair<Int, String?>>) {
        val objectMapper = jacksonObjectMapper()
        val content = objectMapper.writeValueAsString(map)
        setKey(key, content)
    } // TODO REMOVE THE LOGIC FROM THE FRAGMENT :



    data class FrenchLinkData(
        val tmdbId: Int? = null,
        val imdbId: String? = null,
        val type: String? = null, // "anime" | "movie" | "tv" | "live"
        val season: Int? = null,
        val episode: Int? = null,
        val title: String? = null,
        val orgTitle: String? = null,
        val year: Int? = null, // val airedYear: Int? = null,
        val epsTitle: String? = null,
        val frenchSynopsis: String? = null,
        val originalSynopsis: String? = null,
        val frenchPosterPath: String? = null,
        val isAnime: Boolean = false,
        val isAnimation: Boolean? = false,
        val liveChannelsData: String? = null,
    )


    enum class Category {
        MOVIE,
        SERIE,
        //ANIME
    }

    fun getProvider(mediaSourceState: Pair<String, Pair<Int, String?>>): MediaSource {
        val name = mediaSourceState.first
        return mediaSources.find { it.name == name }
            ?: throw Exception("Unable to find media provider for $name")
    }


    fun String.removeAccents(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace("æ", "ae")
            .replace("ø", "o")
            .replace("å", "a")
    }


    fun yearsMatching(a: Int, b: Int): Boolean { // check if years are distant of max 1 year
        return abs(a - b) <= 1
    }

    fun ((ExtractorLink) -> Unit).addTextToExtractorLink(sourceName: String?, after: Boolean?= false): (ExtractorLink) -> Unit {
        return {
            val text = if (after == true) {
                it.name + " " + (sourceName ?: "")
            } else {
                (sourceName ?: "") + " " + it.name
            }
            this.invoke (
                ExtractorLink(
                    it.source,
                    text,
                    it.url,
                    it.referer,
                    it.quality,
                    it.isM3u8,
                    it.headers,
                    it.extractorData
                )
            )
        }
    }


    fun ((SubtitleFile) -> Unit).addSubtitleName(sourceName: String?): (SubtitleFile) -> Unit {
        return {
            this.invoke (
                SubtitleFile(
                    sourceName + " " + it.lang,
                    it.url
                )
            )
        }
    }

}