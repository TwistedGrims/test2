package com.lagradost.mediaSources

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.extractors.BlackInkExtractor
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.utils.FStreamUtils.FrenchLinkData
import com.lagradost.utils.FStreamUtils.Category
import com.lagradost.utils.FStreamUtils.addTextToExtractorLink
import com.lagradost.utils.FStreamUtils.removeAccents
import com.lagradost.utils.FstreamMediaProvidersUtils.session
import com.lagradost.utils.MediaSource
import org.jsoup.nodes.Document


class BlackInkSource : MediaSource() {
    override val name = "BlackInk"
    override val sourceMainUrl = "https://www.darkiworld.com"
    override val categories = listOf(Category.MOVIE, Category.SERIE)
    override val censored = true


    override suspend fun loadContent(
        overwrittenUrl: String,
        data: FrenchLinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val frenchTitle =
            data.title ?: throw ErrorLoadingException("empty frenchTitle blackink")
        val wantedEpisode = data.episode
        val wantedSeason = data.season
        val tmdbId = data.tmdbId

        val isSerie = wantedSeason != null && wantedEpisode != null
        val searchRequest =
            session.get(
                "$overwrittenUrl/search/${frenchTitle}"
            )

        val html = searchRequest.document
        val jsonResponse = getBlackJson(html)

        val searchResult =
            tryParseJson<DarkInkMainResponse>(jsonResponse)?.loaders?.searchPage?.results?.firstOrNull {
                it.tmdbId == tmdbId
            }

        val id = searchResult?.id ?: throw ErrorLoadingException("ID not found on BlackInk")
        val title = parseBlackInkTitle(searchResult.name)

        val referer = if (isSerie) {
            "$overwrittenUrl/titles/$id/$title/season/$wantedSeason/episode/$wantedEpisode"
        } else {
            "$overwrittenUrl/titles/$id/$title"
        }
        val mainResult = if (isSerie) {
            session.get(
                "$overwrittenUrl/api/v1/titles/$id/seasons/$wantedSeason/episodes/$wantedEpisode?loader=episodePage",
                referer = referer
            ).text
        } else {
            session.get("$overwrittenUrl/api/v1/titles/$id?loader=titlePage", referer = referer).text
        }

        val parsedMainPage = tryParseJson<DarkTitlePage>(mainResult)
        val results = if (isSerie) {
            parsedMainPage?.episode?.videos?.plus(parsedMainPage.episode.primaryVideo)
        } else {
            val downloadId = parsedMainPage?.title?.primaryVideo?.idLien
            val apiDownloadResponse =
                session.get("$overwrittenUrl/api/v1/download/${downloadId}", referer = referer).text
            val apiDownladData = tryParseJson<DarkDownloadApiResponse>(apiDownloadResponse)
            apiDownladData?.alternativeVideos?.plus(apiDownladData.video)
        }

        results?.apmap { video ->
            val src = video?.src
                ?: (BlackInkExtractor().mainUrl + "/embed-" + video?.lien?.substringAfterLast("/") + ".html")
            val gb = video?.taille?.div((1024 * 1024 * 1024.0))
            val lang = video?.langues?.map { it.lang }?.joinToString(" ") ?: ""
            val size = gb.let{String.format("%.1f GB", gb)}
            val quality = video?.quality ?: ""
            loadExtractor(
                src, subtitleCallback, callback.addTextToExtractorLink(
                    " $quality $size $lang",
                    after = true
                )
            )
        }
    }





private fun parseBlackInkTitle(input: String?): String? {
    return input
        ?.lowercase()
        ?.replace(" ", "-")
        ?.filter { it.isLetter() || it.isDigit() || it == '-' || it == ',' }
        ?.removeAccents()
        ?.let { title -> // remove multiple following occurences of the - char
            var result = "" // exemple: "top--gun---maverick" =>  "top-gun-maverick"
            var foundDash = false
            for (c in title) {
                if (c == '-' && !foundDash) {
                    result += c
                    foundDash = true
                } else if (c != '-') {
                    result += c
                    foundDash = false
                }
            }
            result
        }
}


private fun getBlackJson(doc: Document): String {
    return doc.select("script").first{
        it.data().trimIndent().startsWith("window.bootstrapData = ")
    }.data().trimIndent().removePrefix("window.bootstrapData = ")
        .removeSuffix(";")
}


    data class DarkInkMainResponse(
        @JsonProperty("csrf_token")
        val csrfToken: String,
        val loaders: DarkLoaders,
    )

    data class DarkLoaders(
        val searchPage: DarkSearchPage?,
        val titlePage: DarkTitlePage?,
    )

    data class DarkSearchPage(
        val results: List<DarkInkResult>,
    )

    data class DarkTitlePage(
        val title: Title,
        val episode: DarkEpisode?,
    )

    data class Title(
        val id: Long?,
        @JsonProperty("primary_video")
        val primaryVideo: DarkVideo?,
        //val videos: List<Video>,
    )


    data class DarkInkResult(
        val id: Long,
        val name: String,
        val category: Long?,
        @JsonProperty("tmdb_id")
        val tmdbId: Int?,
        @JsonProperty("have_link")
        val haveLink: Long,
        @JsonProperty("have_streaming")
        val haveStreaming: Long,
        val isSeries: Boolean,
        val year: Long,
    )

    // darkino/api/download/id
    data class DarkDownloadApiResponse(
        val video: DarkVideo?,
        @JsonProperty("alternative_videos")
        val alternativeVideos: List<DarkVideo?>, //DarkAlternativeVideo
    )

    data class DarkEpisode(
        val id: Long,
        val name: String,
        val description: String,
        val poster: String,
        @JsonProperty("release_date")
        val releaseDate: String,
        @JsonProperty("title_id")
        val titleId: Long,
        @JsonProperty("season_id")
        val seasonId: Long,
        @JsonProperty("season_number")
        val seasonNumber: Long,
        @JsonProperty("episode_number")
        val episodeNumber: Long,
        val runtime: Long,
        @JsonProperty("allow_update")
        val allowUpdate: Boolean,
        @JsonProperty("have_link")
        val haveLink: Long,
        @JsonProperty("have_streaming")
        val haveStreaming: Long,
        @JsonProperty("created_at")
        val createdAt: String,
        @JsonProperty("updated_at")
        val updatedAt: String,
        @JsonProperty("temp_id")
        val tempId: Any?,
        val popularity: Any?,
        @JsonProperty("model_type")
        val modelType: String,
        val rating: Double,
        @JsonProperty("vote_count")
        val voteCount: Long,
        val status: String,
        val year: Long,
        val videos: List<DarkVideo?>,
        @JsonProperty("primary_video")
        val primaryVideo: DarkVideo?,
    )

    data class DarkVideo(
        @JsonProperty("id_lien")
        val idLien: Long?,
        val lien: String?,
        @JsonProperty("id_host")
        val idHost: Long?,
        @JsonProperty("id_post")
        val idPost: Long?,
        @JsonProperty("id_darkibox")
        val idDarkibox: Any?,
        @JsonProperty("title_id")
        val titleId: Long?,
        @JsonProperty("id_user")
        val idUser: String?,
        @JsonProperty("id_link")
        val idLink: Any?,
        val idallo: Any?,
        val taille: Long?,
        @JsonProperty("id_partie")
        val idPartie: String?,
        @JsonProperty("total_parts")
        val totalParts: Long?,
        val numero: Long?,
        val episode: Long?,
        @JsonProperty("episode_id")
        val episodeId: Any?,
        @JsonProperty("full_saison")
        val fullSaison: Long?,
        val reported: Boolean?,
        val langue: Any?,
        val qualite: Long?,
        val saison: Long?,
        val sub: Any?,
        val multilang: Any?,
        val active: Long?,
        val view: Long?,
        val streaming: Boolean?,
        val revived: Any?,
        @JsonProperty("from_user")
        val fromUser: Boolean?,
        @JsonProperty("queue_check")
        val queueCheck: Any?,
        @JsonProperty("last_dl")
        val lastDl: String?,
        val downvotes: Long?,
        val upvotes: Long?,
        @JsonProperty("checked_date")
        val checkedDate: String?,
        @JsonProperty("updated_at")
        val updatedAt: String?,
        @JsonProperty("created_at")
        val createdAt: String?,
        @JsonProperty("deleted_at")
        val deletedAt: Any?,
        val name: String?,
        val src: String?,
        val type: String?,
        val id: String?,
        val language: String?,
        val quality: String?,
        val title: DarkTitle?,
        @JsonProperty("model_type")
        val modelType: String?,
        val langues: List<Langue>?,
        val qual: Qual?,
        val reports: List<Any?>?,
    )

    data class DarkTitle(
        val id: Long?,
        val name: String?,
        val description: String?,
        val backdrop: String?,
        val poster: String?,
        @JsonProperty("is_series")
        val isSeries: Boolean?,
        @JsonProperty("seasons_count")
        val seasonsCount: Long?,
        val rating: Long?,
        @JsonProperty("model_type")
        val modelType: String?,
        @JsonProperty("vote_count")
        val voteCount: Long?,
        val status: String?,
        val year: Any?,
    )

    data class Langue(
        @JsonProperty("id_lang")
        val idLang: Long?,
        val lang: String?,
        val add: Long?,
        val flag: String?,
        val active: Long?,
        val pivot: Pivot?,
    )

    data class Pivot(
        @JsonProperty("lien_id")
        val lienId: Long?,
        val value: Long?,
        val type: Long?,
    )

    data class Qual(
        @JsonProperty("id_qual")
        val idQual: Long?,
        val qual: String?,
        val label: String?,
        @JsonProperty("Videos")
        val videos: Long?,
        @JsonProperty("Games")
        val games: Long?,
        @JsonProperty("Music")
        val music: Long?,
        @JsonProperty("Other")
        val other: Long?,
        @JsonProperty("Applications")
        val applications: Long?,
        @JsonProperty("Books")
        val books: Long?,
    )
}
