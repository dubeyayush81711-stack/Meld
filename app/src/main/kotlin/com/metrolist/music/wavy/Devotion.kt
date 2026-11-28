package com.metrolist.music.wavy

/** Keyword check for devotional (bhakti) songs on title and artist. A rule list, so it can miss a song with no such words in its title. */
object Devotion {
    private val strong = Regex("(?i)(bhajan|bhakti|aarti|chalisa|mantra|stotram|kirtan|jaap|devotional|hanuman|ganesh|bholenath|mahadev|shiv|krishna|radhe|durga|mata |maiya|waheguru|gurbani|shabad|shri ram|sri ram|jai shri|jai mata)")
    fun isDevotional(t: Track): Boolean { val s = t.title + " " + t.artist; return strong.containsMatchIn(s) }
}
