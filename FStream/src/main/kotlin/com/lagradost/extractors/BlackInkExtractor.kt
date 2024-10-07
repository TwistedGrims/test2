package com.lagradost.extractors
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities

open class BlackInkExtractor: ExtractorApi() {
    override var mainUrl = "https://darkibox.com"
    override var name = "BlackInk"
    override val requiresReferer = false


    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val r = app.get(url)
        val response = r.text
        val document = r.document
        val videoplayer = document.select("video#vjsplayer")
        videoplayer.select("track").forEach{ track ->
            if(track.attr("srclang") != "th") {
                subtitleCallback.invoke(
                    SubtitleFile(
                        track.attr("label") ?: "",
                        track.attr("src")
                    )
                )
            }
        }
        val link = Regex("src: \"(.*?)\"").find(response)?.groupValues?.get(1)
        if (link != null) {
            callback.invoke(ExtractorLink(
                this.name,
                this.name,
                link,
                mainUrl, // none required
                Qualities.Unknown.value,
                true
            ))
        }

    }
}