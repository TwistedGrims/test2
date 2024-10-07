package com.lagradost.mediaSources




import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.utils.FStreamUtils.FrenchLinkData
import com.lagradost.utils.FStreamUtils.Category
import com.lagradost.utils.MediaSource

class KatrovSource : MediaSource() {
    override val name = "Katrov"
    override val sourceMainUrl = "https://titrov.com"
    override val categories = listOf(Category.MOVIE)


    override suspend fun loadContent(
        overwrittenUrl: String,
        data: FrenchLinkData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val frenchTitle = data.title
        val year = data.year
        val root = overwrittenUrl.substringAfter("https://").substringBeforeLast(".")
        val homepath = app.get(overwrittenUrl).document.select("a#${root}c").attr("href")
        frenchTitle ?: throw ErrorLoadingException("Empty searched title")
        val document = app.post(
            "$overwrittenUrl/$homepath/home/$root",
            data = mapOf("searchword" to frenchTitle.replace(" ", "+").take(20))
        ).document
        val url = document.select("div.column1 > div#hann").first { element ->
            element.select("a").text().contains("($year)", ignoreCase = true)
        }.select("a").attr("href")
        val mainPage = app.get("$overwrittenUrl$url").document
        val iframe = mainPage.selectFirst("iframe")?.attr("src")
            ?: throw ErrorLoadingException(" empty iframe 1212")
        val content = app.get(iframe).document.select("div.video > script").html().toString()
        val m3u8 = Regex("file: \"(.*?)\"").find(content)?.groupValues?.get(1)
            ?: throw ErrorLoadingException(" empty m3u8")
        callback.invoke(
            ExtractorLink(
                overwrittenUrl,
                name,
                m3u8,
                "",
                quality = Qualities.P720.value,
                isM3u8 = true,
            )
        )
    }
}
