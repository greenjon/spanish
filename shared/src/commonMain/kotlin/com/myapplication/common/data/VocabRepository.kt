package com.myapplication.common.data

import com.myapplication.common.ui.ProgressionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlin.math.max

class VocabRepository(driverFactory: DatabaseDriverFactory) {
    private val driver = driverFactory.createDriver()
    private val db = AppDatabase(driver)
    
    suspend fun populateIfEmpty(jsonCards: List<VocabCardDto>) {
        withContext(Dispatchers.Default) {
            val count = db.appDatabaseQueries.getAllCards().executeAsList().size
            if (count == 0) {
                db.transaction {
                    jsonCards.forEach { card ->
                        db.appDatabaseQueries.insertVocabCard(
                            id = card.id,
                            spanish = card.spanish,
                            english = card.english,
                            tags = card.tags.joinToString(",")
                        )
                    }
                }
            }
        }
    }

    suspend fun getMatchingCardCount(filterSpec: TagFilterSpec = TagFilterSpec()): Int {
        return withContext(Dispatchers.Default) {
            db.appDatabaseQueries.getAllCards().executeAsList().count { card ->
                filterSpec.matches(card.tags)
            }
        }
    }

    suspend fun getMatchingCards(filterSpec: TagFilterSpec = TagFilterSpec()): List<VocabCard> {
        return withContext(Dispatchers.Default) {
            db.appDatabaseQueries.getAllCards().executeAsList().filter { card ->
                filterSpec.matches(card.tags)
            }
        }
    }

    suspend fun getNextCard(
        filterSpec: TagFilterSpec = TagFilterSpec(),
        progressionMode: ProgressionMode = ProgressionMode.RANDOM
    ): Pair<VocabCard?, ReviewState?> {
        return withContext(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            
            // 1. Try to get a due card that matches filter
            val dueCards = db.appDatabaseQueries.getDueCards(now).executeAsList().filter { due ->
                filterSpec.matches(due.tags)
            }
            if (dueCards.isNotEmpty()) {
                val nextDue = if (progressionMode == ProgressionMode.RANDOM) dueCards.shuffled().first() else dueCards.first()
                val card = VocabCard(nextDue.id, nextDue.spanish, nextDue.english, nextDue.tags)
                val state = ReviewState(nextDue.id, nextDue.nextReviewDate, nextDue.interval, nextDue.easeFactor, nextDue.repetitions)
                return@withContext Pair(card, state)
            }

            // 2. If no due cards, get unseen cards that match filter
            val unseenList = db.appDatabaseQueries.getUnseenCards().executeAsList().filter { unseen ->
                filterSpec.matches(unseen.tags)
            }

            if (unseenList.isNotEmpty()) {
                val nextUnseen = if (progressionMode == ProgressionMode.RANDOM) unseenList.shuffled().first() else unseenList.first()
                return@withContext Pair(nextUnseen, null)
            }

            // 3. Fallback: pick from all cards matching filter
            val allMatchingCards = db.appDatabaseQueries.getAllCards().executeAsList().filter { card ->
                filterSpec.matches(card.tags)
            }

            if (allMatchingCards.isNotEmpty()) {
                val selectedCard = if (progressionMode == ProgressionMode.RANDOM) allMatchingCards.shuffled().first() else allMatchingCards.first()
                val reviewState = db.appDatabaseQueries.getReviewState(selectedCard.id).executeAsList().firstOrNull()?.let {
                    ReviewState(it.cardId, it.nextReviewDate, it.interval, it.easeFactor, it.repetitions)
                }
                return@withContext Pair(selectedCard, reviewState)
            }

            Pair(null, null)
        }
    }

    // Overload for backward compatibility with string tag filter
    suspend fun getNextCard(
        tagFilter: String?,
        progressionMode: ProgressionMode
    ): Pair<VocabCard?, ReviewState?> {
        val filterSpec = if (tagFilter.isNullOrBlank()) {
            TagFilterSpec()
        } else {
            TagFilterSpec(chapters = setOf(tagFilter.trim()))
        }
        return getNextCard(filterSpec, progressionMode)
    }

    suspend fun submitReview(cardId: String, grade: Int, currentState: ReviewState?) {
        withContext(Dispatchers.Default) {
            // SM-2 logic
            // grade: 0-5 (0=Blackout, 5=Perfect)
            var repetitions = currentState?.repetitions ?: 0
            var interval = currentState?.interval ?: 0L
            var easeFactor = currentState?.easeFactor ?: 2.5

            if (grade >= 3) {
                if (repetitions == 0L) {
                    interval = 1L
                } else if (repetitions == 1L) {
                    interval = 6L
                } else {
                    interval = (interval * easeFactor).toLong()
                }
                repetitions++
            } else {
                repetitions = 0
                interval = 1L
            }

            easeFactor += (0.1 - (5 - grade) * (0.08 + (5 - grade) * 0.02))
            if (easeFactor < 1.3) easeFactor = 1.3

            val nextReviewDate = Clock.System.now().toEpochMilliseconds() + (interval * 24 * 60 * 60 * 1000)

            db.appDatabaseQueries.insertReviewState(
                cardId = cardId,
                nextReviewDate = nextReviewDate,
                interval = interval,
                easeFactor = easeFactor,
                repetitions = repetitions
            )
        }
    }
}
