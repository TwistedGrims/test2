package com.lagradost.mediaSources

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
//import com.lagradost.DooplayTemplateResponse
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.utils.FStreamUtils.FrenchLinkData
import com.lagradost.utils.FStreamUtils.Category
import com.lagradost.utils.FStreamUtils.addSubtitleName
import com.lagradost.utils.MediaSource


class SandStoneSource : MediaSource() {
    override val name = "SandStone"
    override val sourceMainUrl = "https://1jour1film.shop"
    override val categories = listOf(Category.MOVIE, Category.SERIE)


    override suspend fun loadContent(
        overwrittenUrl: String,
        data: FrenchLinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val query = data.title ?: throw ErrorLoadingException("Empty title")
        val episode = data.episode
        val season = data.season
        val foundResult = app.get("$overwrittenUrl/wp-json/dooplay/search/?nonce=286f6fae56&keyword=$query").text
        val objectMapper = jacksonObjectMapper()

        // Parse the JSON file into your data class
        val searchResponse = objectMapper.readTree(foundResult)
        val link: String = searchResponse.firstOrNull {
            it?.get("title").toString().lowercase().removeSurrounding("\"") == query.lowercase()
        }?.get("url")?.toString()?.removeSurrounding("\"") ?: throw ErrorLoadingException("No match found")
        val isMovie = (episode == null && season == null)


        val fixedLink = if(isMovie) {
            link
        } else {
            // get the episode link
            //Log.i("debug_to_delete", link)
            val serieDocument = app.get(link).document
            val episodeUrl = serieDocument.select("div#seasons").select("li.mark-$episode").firstOrNull {
                //Log.i("debug_to_delete", it.toString())
                //Log.i("debug_to_delete", it.select("div.episodiotitle > a").attr("href").toString())
                it.select("div.episodiotitle").select("a").attr("href").contains("-s$season") ||
                        it.select("div.episodiotitle").select("a").attr("href").contains("-s0$season")

            }?.select("div.episodiotitle")?.select("a")?.attr("href")

            episodeUrl ?: throw ErrorLoadingException("No episode found !!!")
        }

        //Log.i( "debug_to_delete", "HERE URL IS :::::")
        //Log.i( "debug_to_delete", fixedLink)
        val mainDocument = app.get(fixedLink).document

        val iframeList = mainDocument.select("ul#playeroptionsul > li") ?: throw ErrorLoadingException("No iframe found !") // last iframe is not trailer and is the number of available links
        // <li id="player-option-2" class="dooplay_player_option" data-type="movie" data-post="3928" data-nume="2">

        val embedList = mutableListOf<Pair<String, String?>>()
        //Log.i( "debug_to_delete", "iframelist")
        //Log.i( "debug_to_delete", iframeList.toString())
        iframeList.apmap {
            val iframeNumber = it.attr("data-nume").toIntOrNull() ?: return@apmap
            val mediaId = it.attr("data-post")
            val title = it.select("span.title").text()
            val response = app.post(
                "$overwrittenUrl/wp-admin/admin-ajax.php", data = mapOf(
                    "action" to "doo_player_ajax",
                    "post" to mediaId,
                    "nume" to iframeNumber.toString(),
                    "type" to "movie",
                ),
                //referer = episodeUrl
            ).text
            //Log.i( "debug_to_delete", response)
            val embedUrl = tryParseJson<DooplayTemplateResponse>(response)?.embed_url ?: return@apmap
            if (embedUrl.startsWith("https://frembed")) {
                return@apmap // frembed will be implemented somewhere else
            }
            if (embedUrl.startsWith("https://qatar")) {
                val document = app.get(embedUrl, referer = fixedLink).document
                document.select("ul.content > li").forEach{ row ->
                    val dataUrl = row.attr("data-url")
                    embedList.add(Pair(dataUrl, null))
                }
            } else {
                embedList.add(Pair(embedUrl, title))
            }
        }


        //Log.i( "debug_to_delete", "embed_list $embedList")
        embedList.forEach { (embed, title) ->
            loadExtractor(embed, referer = overwrittenUrl, subtitleCallback = subtitleCallback.addSubtitleName(name),
                callback = {
                    if (!it.url.contains(".zcdn.") || it.url.contains("video.m3u8")){
                        // false when a link comes from zcdn without the video.m3u8 keyword

                        callback.invoke(
                            ExtractorLink(
                                it.name,
                                name + " " + it.name + " " + (title ?: ""),
                                it.url,
                                it.referer,
                                it.quality,
                                it.isM3u8,
                                it.headers + mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/116.0"),
                                it.extractorData
                            )
                        )
                    }
                }
            )
        }
    }
    data class DooplayTemplateResponse( // often used by doodplay ?
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )


}
