package com.lagradost.extractors

import com.lagradost.cloudstream3.extractors.XStreamCdn

class FembedOneExtractor: XStreamCdn() {
    override val name: String = "FEmbed"
    override val mainUrl: String = "https://fembed.one"
}