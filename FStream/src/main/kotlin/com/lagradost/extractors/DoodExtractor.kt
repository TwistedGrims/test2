package com.lagradost.extractors
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink

class DoodsProExtractor : DoodLaExtractor() {
    override var mainUrl = "https://doods.pro"
}
open class DoodsZeroExtractor : DoodLaExtractor() {
    override var mainUrl = "https://d0000d.com"
}

class DooodsNetExtractor : DoodLaExtractor() {
    override var mainUrl = "https://d000d.net"
}

class DoodsOZExtractor : DoodsZeroExtractor() {
    override var mainUrl = "https://d0o0d.com" // redirects to d0000d for some reason and no api available

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val doodZero = DoodsZeroExtractor()
        return doodZero.getUrl(url.replace(this.mainUrl, doodZero.mainUrl))
    }
}