package com.lagradost.utils

import com.lagradost.utils.FStreamUtils.FrenchLinkData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.mediaSources.BlackInkSource
import com.lagradost.mediaSources.BlueSeriesSource
import com.lagradost.mediaSources.FrembedSource
import com.lagradost.mediaSources.KatrovSource
import com.lagradost.mediaSources.SandStoneSource
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.Session
import com.lagradost.utils.FStreamUtils.Category
import com.lagradost.utils.FStreamUtils.getProvider
import com.lagradost.utils.FStreamUtils.listOfProviders
import java.net.URI
import java.net.URL

abstract class MediaSource {
    abstract val name: String
    abstract val sourceMainUrl: String
    abstract val categories: List<Category>
    open val censored: Boolean = false

    abstract suspend fun loadContent(
            overwrittenUrl: String,
            data: FrenchLinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    )


}

@OptIn(kotlin.ExperimentalStdlibApi::class)
object FstreamMediaProvidersUtils {
    val session = Session(Requests().baseClient)

    val mediaSources =
            listOf<MediaSource>(
                KatrovSource(),
                SandStoneSource(),
                BlueSeriesSource(),
                FrembedSource(),
                BlackInkSource()
            )


    suspend fun invokeSources(
        category: Category,
        data: FrenchLinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        listOfProviders.filter{it.value.first == 1}.toList().amap {
            try {
                val provider = getProvider(it)
                if (provider.categories.contains(category)) {
                    val url = it.second.second?.takeIf {storedUrl ->  storedUrl.contains("://") } ?: provider.sourceMainUrl
                    provider.loadContent(url, data, subtitleCallback, callback)
                }
            } catch (e: Exception) {
                //logError(e)
            }
        }
    }

    fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    fun getEpisodeSlug(
            season: Int? = null,
            episode: Int? = null,
    ): Pair<String, String> {
        return if (season == null && episode == null) {
            "" to ""
        } else {
            (if (season!! < 10) "0$season" else "$season") to
                    (if (episode!! < 10) "0$episode" else "$episode")
        }
    }

    fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Qualities.Unknown.value
    }

    fun getIndexQualityTags(str: String?, fullTag: Boolean = false): String {
        return if (fullTag)
                Regex("(?i)(.*)\\.(?:mkv|mp4|avi)").find(str ?: "")?.groupValues?.get(1)?.trim()
                        ?: str ?: ""
        else
                Regex("(?i)\\d{3,4}[pP]\\.?(.*?)\\.(mkv|mp4|avi)")
                        .find(str ?: "")
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.replace(".", " ")
                        ?.trim()
                        ?: str ?: ""
    }

    fun String.encodeUrl(): String {
        val url = URL(this)
        val uri = URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
        return uri.toURL().toString()
    }

    fun String?.createSlug(): String? {
        return this?.filter { it.isWhitespace() || it.isLetterOrDigit() }
                ?.trim()
                ?.replace("\\s+".toRegex(), "-")
                ?.lowercase()
    }
}
