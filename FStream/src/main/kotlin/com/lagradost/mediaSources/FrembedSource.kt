package com.lagradost.mediaSources


import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.utils.FStreamUtils.FrenchLinkData
import com.lagradost.utils.FStreamUtils.Category
import com.lagradost.utils.FStreamUtils.addTextToExtractorLink
import com.lagradost.utils.MediaSource
import java.net.URLDecoder


class FrembedSource : MediaSource() {
    override val name = "Frembed"
    override val sourceMainUrl = "https://frembed.pro"
    override val categories = listOf(Category.MOVIE, Category.SERIE)


    override suspend fun loadContent(
        overwrittenUrl: String,
        data: FrenchLinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val season = data.season
        val episode = data.episode
        val tmdbId = data.tmdbId
        val embedList = if (season == null || episode == null){
            val document = app.get("$overwrittenUrl/api/film.php?id=$tmdbId").document
            document.select("ul#drop > li > a").map {
                URLDecoder.decode(base64Decode(it.attr("data-link")), "UTF-8")
            }
        } else {
            val document = app.get("$overwrittenUrl/api/serie.php?id=$tmdbId&sa=$season&epi=$episode").document
            document.select("ul#drop > li > a").map {
                URLDecoder.decode(base64Decode(it.attr("data-link")), "UTF-8").substringAfterLast("?url=")
            }
        }
        //Log.i("debug_to_delete", embedList.toString())
        embedList.forEach{embed ->
            loadExtractor(embed, subtitleCallback, callback.addTextToExtractorLink(name))
        }
    }

}
