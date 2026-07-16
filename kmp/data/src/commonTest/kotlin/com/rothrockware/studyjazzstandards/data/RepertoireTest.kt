package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RepertoireTest {

    @Test
    fun addEditRemoveLifecycle() {
        val env = testEnv()
        assertIs<AddResult.Ok>(env.repo.addRepertoire("My Arrangement", "Me", "In F"))
        val p = env.db.repertoire.single()
        assertEquals("My Arrangement", p.name)
        assertEquals("Me", p.composer)
        assertEquals("In F", p.notes)
        assertEquals(TODAY, p.addedDate)
        assertNull(p.lastReviewed)
        assertEquals(ActivityType.REPERTOIRE_ADDED, env.db.activity.last().type)

        assertIs<AddResult.DuplicateName>(env.repo.addRepertoire("my arrangement", null, null))

        assertIs<AddResult.Ok>(env.repo.editRepertoire("My Arrangement", "My Arrangement v2", null, ""))
        val edited = env.db.repertoire.single()
        assertEquals("My Arrangement v2", edited.name)
        assertNull(edited.composer)
        assertNull(edited.notes, "blank notes stored as null")

        env.repo.removeRepertoire("My Arrangement v2")
        assertEquals(0, env.db.repertoire.size)
        assertEquals(ActivityType.REPERTOIRE_REMOVED, env.db.activity.last().type)
    }

    @Test
    fun featuredPrefersNeverReviewedThenLeastRecent() {
        val env = testEnv()
        env.clock.todayInt = 20260101
        env.repo.addRepertoire("Alpha", null, null)
        env.clock.todayInt = 20260201
        env.repo.addRepertoire("Beta", null, null)
        env.clock.todayInt = TODAY

        // Both never reviewed: earliest addedDate wins
        assertEquals("Alpha", env.repo.featuredRepertoire()?.name)

        // Review Alpha yesterday -> Beta (never reviewed) wins
        env.clock.todayInt = DateInt.addDays(TODAY, -1)
        env.repo.markRepertoireReviewed("Alpha")
        env.clock.todayInt = TODAY
        assertEquals("Beta", env.repo.featuredRepertoire()?.name)

        // Review Beta today -> reviewed-today precedence keeps Beta featured
        env.repo.markRepertoireReviewed("Beta")
        assertEquals("Beta", env.repo.featuredRepertoire()?.name)
    }

    @Test
    fun continueTomorrowPinsForNextDay() {
        val env = testEnv()
        env.repo.addRepertoire("Alpha", null, null)
        env.repo.addRepertoire("Beta", null, null)
        assertEquals("Alpha", env.repo.featuredRepertoire()?.name)

        env.repo.repertoireContinueTomorrow("Beta")
        assertEquals(DateInt.addDays(TODAY, 1), env.db.repertoire.first { it.name == "Beta" }.pinnedDate)
        assertEquals(ActivityType.REPERTOIRE_DEFERRED, env.db.activity.last().type)
        // Pin is for tomorrow, so today Alpha is still featured
        assertEquals("Alpha", env.repo.featuredRepertoire()?.name)

        env.clock.todayInt = DateInt.addDays(TODAY, 1)
        assertEquals("Beta", env.repo.featuredRepertoire()?.name)
    }

    @Test
    fun markReviewedClearsPin() {
        val env = testEnv()
        env.repo.addRepertoire("Alpha", null, null)
        env.repo.repertoireContinueTomorrow("Alpha")
        env.clock.todayInt = DateInt.addDays(TODAY, 1)
        env.repo.markRepertoireReviewed("Alpha")
        val p = env.db.repertoire.single()
        assertEquals(env.clock.todayInt, p.lastReviewed)
        assertNull(p.pinnedDate)
    }

    @Test
    fun featuredIsNullWhenEmpty() {
        val env = testEnv()
        assertNull(env.repo.featuredRepertoire())
    }
}
