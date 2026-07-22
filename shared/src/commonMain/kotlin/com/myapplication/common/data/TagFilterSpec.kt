package com.myapplication.common.data

import kotlinx.serialization.Serializable

@Serializable
data class TagFilterSpec(
    val chapters: Set<String> = emptySet(),
    val partsOfSpeech: Set<String> = emptySet(),
    val verbTypes: Set<String> = emptySet(),
    val topics: Set<String> = emptySet(),
    val grammarTags: Set<String> = emptySet()
) {
    val isEmpty: Boolean 
        get() = chapters.isEmpty() && partsOfSpeech.isEmpty() && verbTypes.isEmpty() && topics.isEmpty() && grammarTags.isEmpty()

    fun matches(cardTags: List<String>): Boolean {
        if (isEmpty) return true
        val tagSet = cardTags.map { it.trim() }.toSet()

        if (chapters.isNotEmpty() && chapters.none { tagSet.contains(it) }) {
            return false
        }
        if (partsOfSpeech.isNotEmpty() && partsOfSpeech.none { tagSet.contains(it) }) {
            return false
        }
        if (verbTypes.isNotEmpty() && verbTypes.none { tagSet.contains(it) }) {
            return false
        }
        if (topics.isNotEmpty() && topics.none { tagSet.contains(it) }) {
            return false
        }
        if (grammarTags.isNotEmpty() && grammarTags.none { tagSet.contains(it) }) {
            return false
        }

        return true
    }

    fun matches(csvTags: String): Boolean {
        if (isEmpty) return true
        val tagsList = csvTags.split(",")
        return matches(tagsList)
    }

    val totalSelectedCount: Int
        get() = chapters.size + partsOfSpeech.size + verbTypes.size + topics.size + grammarTags.size
}

data class TagCategory(
    val title: String,
    val options: List<TagOption>
)

data class TagOption(
    val tag: String,
    val label: String
)

object TagCategories {
    val chapters = TagCategory(
        title = "📚 Chapters",
        options = listOf(
            TagOption("1A", "Spanish 1A"),
            TagOption("ch1", "Ch 1"),
            TagOption("ch2", "Ch 2"),
            TagOption("ch3", "Ch 3"),
            TagOption("ch4", "Ch 4"),
            TagOption("ch5", "Ch 5")
        )
    )

    val partsOfSpeech = TagCategory(
        title = "🏷️ Part of Speech",
        options = listOf(
            TagOption("noun", "Noun"),
            TagOption("verb", "Verb"),
            TagOption("adj", "Adjective"),
            TagOption("phrase", "Phrase"),
            TagOption("preposition", "Preposition"),
            TagOption("infinitive", "Infinitive"),
            TagOption("adj-phrase", "Adj Phrase"),
            TagOption("verb-form", "Verb Form")
        )
    )

    val verbTypes = TagCategory(
        title = "⚡ Verb Properties",
        options = listOf(
            TagOption("reg", "Regular"),
            TagOption("irreg", "Irregular"),
            TagOption("stem-change", "Stem-Change"),
            TagOption("-ar", "-ar"),
            TagOption("-er", "-er"),
            TagOption("-ir", "-ir"),
            TagOption("present-tense", "Present Tense")
        )
    )

    val topics = TagCategory(
        title = "🏫 Topics & Themes",
        options = listOf(
            TagOption("family", "Family"),
            TagOption("school", "School"),
            TagOption("greetings", "Greetings"),
            TagOption("travel", "Travel"),
            TagOption("hotel", "Hotel"),
            TagOption("sports", "Sports"),
            TagOption("numbers", "Numbers"),
            TagOption("time", "Time"),
            TagOption("people", "People"),
            TagOption("places", "Places")
        )
    )

    val grammarTags = TagCategory(
        title = "👥 Gender, Number & Pronouns",
        options = listOf(
            TagOption("masc", "Masculine"),
            TagOption("fem", "Feminine"),
            TagOption("sing", "Singular"),
            TagOption("plur", "Plural"),
            TagOption("yo", "yo"),
            TagOption("tú", "tú"),
            TagOption("él", "él"),
            TagOption("ella", "ella"),
            TagOption("nosotros", "nosotros"),
            TagOption("ellos", "ellos"),
            TagOption("ellas", "ellas")
        )
    )

    val allCategories = listOf(chapters, partsOfSpeech, verbTypes, topics, grammarTags)
}
