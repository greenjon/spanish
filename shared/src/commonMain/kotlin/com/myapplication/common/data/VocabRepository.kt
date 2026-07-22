package com.myapplication.common.data

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

    suspend fun getNextCard(tagFilter: String? = null): Pair<VocabCard?, ReviewState?> {
        return withContext(Dispatchers.Default) {
            val now = Clock.System.now().toEpochMilliseconds()
            
            // 1. Try to get a due card
            val dueCards = db.appDatabaseQueries.getDueCards(now).executeAsList()
            if (dueCards.isNotEmpty()) {
                val nextDue = dueCards.first()
                val card = VocabCard(nextDue.id, nextDue.spanish, nextDue.english, nextDue.tags)
                val state = ReviewState(nextDue.id, nextDue.nextReviewDate, nextDue.interval, nextDue.easeFactor, nextDue.repetitions)
                return@withContext Pair(card, state)
            }

            // 2. If no due cards, get an unseen card
            val unseen = if (tagFilter.isNullOrBlank()) {
                db.appDatabaseQueries.getUnseenCards().executeAsList().firstOrNull()
            } else {
                db.appDatabaseQueries.getUnseenCardsByTag(tagFilter).executeAsList().firstOrNull()
            }

            if (unseen != null) {
                return@withContext Pair(unseen, null)
            }

            Pair(null, null)
        }
    }

    suspend fun submitReview(cardId: String, grade: Int, currentState: ReviewState?) {
        withContext(Dispatchers.Default) {
            // SM-2 logic
            // grade: 0-5 (0=Blackout, 5=Perfect)
            var repetitions = currentState?.repetitions ?: 0
            var interval = currentState?.interval ?: 0L
            var easeFactor = currentState?.easeFactor ?: 2.5

            if (grade >= 3) {
                if (repetitions == 0) {
                    interval = 1L
                } else if (repetitions == 1) {
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
