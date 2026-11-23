package com.ammar.wallflow.model.search

import com.ammar.wallflow.model.Purity
import com.ammar.wallflow.sources.booru.BooruStyle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A feed search over one saved booru board. */
@Serializable
@SerialName("BooruSearch")
data class BooruSearch(
    override val query: String = "",
    override val filters: BooruFilters,
    override val meta: SearchMeta? = null,
) : Search()

@Serializable
@SerialName("BooruFilters")
data class BooruFilters(
    val boardUrl: String,
    val style: BooruStyle,
    /** Purity names: sfw, sketchy, nsfw. All three are shown by default. */
    val purityNames: Set<String> = setOf(
        Purity.SFW.purityName,
        Purity.SKETCHY.purityName,
        Purity.NSFW.purityName,
    ),
    /** Set when the search is over a taught site instead of a booru board. */
    val siteId: String? = null,
) : Filters() {
    fun allowedPurities(): Set<Purity> = purityNames.map { Purity.fromName(it) }.toSet()
}
