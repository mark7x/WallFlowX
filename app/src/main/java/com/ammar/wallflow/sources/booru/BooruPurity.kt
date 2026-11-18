package com.ammar.wallflow.sources.booru

import com.ammar.wallflow.model.Purity

/**
 * Maps a booru rating string to the app's [Purity] levels.
 * Danbooru uses g/s/q/e, Gelbooru uses general/sensitive/questionable/explicit,
 * and Moebooru uses safe/questionable/explicit. Unknown ratings map to NSFW,
 * so they stay hidden unless the user opts in.
 */
fun BooruPost.purity(): Purity = when (rating?.lowercase()) {
    "g", "general", "safe" -> Purity.SFW
    "s", "sensitive", "q", "questionable" -> Purity.SKETCHY
    "e", "explicit" -> Purity.NSFW
    else -> Purity.NSFW
}
