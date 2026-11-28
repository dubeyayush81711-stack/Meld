package com.metrolist.music.wavy

/** Keyword check for devotional (bhakti) songs on title and artist. A rule list, so it can miss a song with no such words in its title. */
object Devotion {
    private val strong = Regex("(?i)(bhajan|bhakti|aarti|chalisa|mantra|stotram|kirtan|jaap|devotional|hanuman|ganesh|bholenath|mahadev|shiv|krishna|radhe|durga|mata |maiya|waheguru|gurbani|shabad|shri ram|sri ram|jai shri|jai mata)")
    fun isDevotional(t: Track): Boolean { val s = t.title + " " + t.artist; return strong.containsMatchIn(s) }

    private val sad = Regex("(?i)(sad|dard|bewafa|judai|tanha|alvida|aansu|rula|toota|broken|heartbreak|bichad|yaad|tadap|lonely|cry)")
    private val party = Regex("(?i)(party|dj|remix|club|dance|nachle|nachne|thumka|balam|gallan|beat|bass|lit|pani pani|naagin|garba|dhol)")
    private val love = Regex("(?i)(love|ishq|pyar|pyaar|mohabbat|tum hi|dil|sajna|sanam|mahiya|kesariya|raataan|hamari adhuri|tera ban)")
    /** Approximate mood tag from title and artist keywords: "sad", "party", "romantic" or null. Not a classifier, can be wrong or miss. */
    fun mood(t: Track): String? { val s = t.title + " " + t.artist
        val a = sad.containsMatchIn(s); val b = party.containsMatchIn(s); val c = love.containsMatchIn(s)
        return when { a && !b -> "sad"; b && !a -> "party"; c && !a && !b -> "romantic"; else -> null } }
}
