package com.metrolist.music.wavy

/**
 * Strict "only my selected languages" filter for Home and recommendations.
 * Honest limit: there is no language field on a song, so this guesses from the script of the title/artist
 * (Tamil, Telugu, Kannada, Malayalam, Gurmukhi...), a list of well known artists and typical title words.
 * A song with a plain Latin title and an unknown artist cannot be told apart and is kept.
 */
object LangGuard {
    private val scripts = listOf(
        "tamil" to Regex("[\\u0B80-\\u0BFF]"), "telugu" to Regex("[\\u0C00-\\u0C7F]"), "kannada" to Regex("[\\u0C80-\\u0CFF]"),
        "malayalam" to Regex("[\\u0D00-\\u0D7F]"), "punjabi" to Regex("[\\u0A00-\\u0A7F]"), "bengali" to Regex("[\\u0980-\\u09FF]"), "gujarati" to Regex("[\\u0A80-\\u0AFF]"))
    private fun rx(s: String) = Regex("(?i)(?<![a-z])(" + s + ")(?![a-z])")
    private val words = listOf(
        "telugu" to rx("telugu|naa|nuvve|nuvvu|nenu|pellam|ninne|ammayi|abbayi|chinnadi|vennela|premante|sid sriram|devi sri prasad|thaman s?|adithya rk|anurag kulkarni|ram miriyala|mangli|sai abhyankkar"),
        "tamil" to rx("tamil|kadhal|kaadhal|vaathi|thalaiva|thalapathy|anirudh(?: ravichander)?|yuvan shankar raja|harris jayaraj|g\\.? ?v\\.? prakash|dhee|sean roldan|santhosh narayanan"),
        "kannada" to rx("kannada|sanjith hegde|vijay prakash"),
        "malayalam" to rx("malayalam|vineeth sreenivasan|sushin shyam"),
        "punjabi" to rx("punjabi|jatt|jatti|bhangra|karan aujla|ap dhillon|diljit dosanjh|sidhu moose ?wala|shubh|avvy sra|jasmine sandlas|gurnam bhullar|ammy virk|sharry mann|babbu maan|jazzy b|gippy grewal|b praak|harrdy sandhu|navaan sandhu|sunanda sharma|ninja|tegi pannu|satinder sartaaj|sartaaj|goldie sohel|mxrci"),
        "haryanvi" to rx("haryanvi|raj mawar|aman jaji|manisha sharma|renuka panwar|masoom sharma|sapna choudhary|ajay hooda|diler kharkiya|ruchika jangid|pranjal dahiya"),
        "bhojpuri" to rx("bhojpuri|pawan singh|khesari lal|neelkamal singh|dinesh lal yadav"),
        "marathi" to rx("marathi|ajay-atul|ajay atul"))
    private val south = setOf("tamil", "telugu", "kannada", "malayalam")
    @Volatile private var allowed: Set<String>? = null
    @Volatile private var forUid: String? = null

    private fun allow(): Set<String>? {
        val u = Api.uid ?: return null
        if (forUid == u) return allowed
        val l = S.s("my_langs_$u").split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        forUid = u; allowed = if (l.isEmpty()) null else if ("south" in l) l + south else l
        return allowed
    }
    /** Call after the saved language list changes. */
    fun reset() { forUid = null }

    fun lang(t: Track): String? {
        val txt = t.title + " " + t.artist
        scripts.firstOrNull { it.second.containsMatchIn(txt) }?.let { return it.first }
        return words.firstOrNull { it.second.containsMatchIn(txt) }?.first
    }
    private val junk = Regex("(?i)\\b(mashup|teaser|trailer|audio launch|jukebox|full album|status video|whatsapp status|reaction|making of|behind the scenes|promo|nightcore|bass boosted|8d audio|slowed|reverb|sped up|karaoke|cover version|lyrical video mashup|dialogue|ringtone|bgm|shorts?)\\b")
    fun quality(t: Track): Boolean = !junk.containsMatchIn(t.title) && (t.dur <= 0 || (t.dur in 75..600))
    fun ok(t: Track): Boolean { if (!quality(t)) return false; return lang_ok(t) }
    private fun lang_ok(t: Track): Boolean { val a = allow() ?: return true; val l = lang(t) ?: return true; return l in a }
    fun filter(l: List<Track>): List<Track> = l.filter { ok(it) }
}
