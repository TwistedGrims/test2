package com.lagradost.mediaSources

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.utils.FStreamUtils.FrenchLinkData
import com.lagradost.utils.FStreamUtils.Category
import com.lagradost.utils.FStreamUtils.addTextToExtractorLink
import com.lagradost.utils.FStreamUtils.yearsMatching
import com.lagradost.utils.MediaSource


class BlueSeriesSource : MediaSource() {
    override val name = "BlueSeries"
    override val sourceMainUrl = "https://www.blueseries.cc"
    override val categories = listOf(Category.SERIE)


    override suspend fun loadContent(
        overwrittenUrl: String,
        data: FrenchLinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val invokedSourceName = "BlueSeries"
        val frenchTitle = data.title ?: throw ErrorLoadingException(" empty frenchTitle")
        val wantedSeason = data.season ?: throw ErrorLoadingException(" invalid wantedSeason")
        val wantedEpisode = data.episode ?: throw ErrorLoadingException(" invalid wantedEpisode")
        val wantedYear = data.year ?: throw ErrorLoadingException(" no year supplied")
        val document = app.get(overwrittenUrl).document
        val hashText = document.select("script").first {
            it.html().contains("var dle_login_hash =")
        }.html().take(300)
        val userHash = Regex("var dle_login_hash = '(.*?)';").find(hashText)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Empty user hash")

        val searchDoc = app.post(
            "$overwrittenUrl/index.php?do=search", data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "1",
                "result_from" to "1",
                "story" to frenchTitle.replace(" ", "+"),
                "titleonly" to "3",
                "searchuser" to	"",
                "replyless" to "0",
                "replylimit" to "0",
                "searchdate" to "0",
                "beforeafter" to "after",
                "sortby" to "title",
                "resorder" to "desc",
                "showpost" to "0",
                //"catlist[]" to yearCategory, // date   72 => 81, 71 => 1982
                "user_hash" to userHash,
            )
        ).document

        //Log.i("debug_to_delete", searchDoc.toString())
        val url = searchDoc.selectFirst("article > a")?.attr("href")
            ?: throw ErrorLoadingException("invalid url !!")


        val serieMainPage = app.get(url).document
        val seasons = serieMainPage.select("div.seasontab > div > div.content1")
        val year = serieMainPage.selectFirst("div.cast > div.list")?.ownText()?.toIntOrNull() ?: throw ErrorLoadingException("No year found")

        if(!yearsMatching(year, wantedYear)) throw ErrorLoadingException("Years not matching")
        val seasonIndex = seasons.size - wantedSeason
        val episodes = seasons[seasonIndex].select("div.spoiler1 > div.spoiler1-body > a")
        val episodeIndex = episodes.size - wantedEpisode
        val episodeUrl = episodes[episodeIndex].attr("href")
            ?: throw ErrorLoadingException("invalid episode url")
        val episodeMainPage = app.get(episodeUrl).document
        val players = episodeMainPage.select("ul.player-list > li").mapNotNull {
            val playerText = it.select("div.lien").attr("onclick")
            val playerData = Regex("this, '(.*?)', '(.*?)'").find(playerText)
            val playerId = playerData?.groupValues?.get(1) ?: return@mapNotNull null
            val playerName = playerData.groupValues?.get(2) ?: return@mapNotNull null
            Pair(playerId, playerName)
        }
        players.apmap { player ->
            val playerResponse = app.post(
                "$overwrittenUrl/engine/ajax/Season.php", data = mapOf(
                    "id" to player.first,
                    "xfield" to player.second,
                    "action" to "playEpisode"
                ),
                referer = episodeUrl
            )

            val iframe = playerResponse.document.select("iframe").attr("src")

            loadExtractor(iframe, overwrittenUrl, subtitleCallback, callback.addTextToExtractorLink(invokedSourceName + " " + player.second.replace("_", " ")))
        }
    }
}
