package com.rothrockware.studyjazzstandards.data

import com.rothrockware.studyjazzstandards.data.model.ActivityEntry
import com.rothrockware.studyjazzstandards.data.model.ActivityType
import com.rothrockware.studyjazzstandards.data.model.JazzDb
import com.rothrockware.studyjazzstandards.data.model.RepertoirePiece
import com.rothrockware.studyjazzstandards.data.model.Review
import com.rothrockware.studyjazzstandards.data.model.Song
import com.rothrockware.studyjazzstandards.data.model.SongStatus
import com.rothrockware.studyjazzstandards.data.model.Voicing
import com.rothrockware.studyjazzstandards.data.seed.ALL_SONGS
import com.rothrockware.studyjazzstandards.data.seed.INTERVALS
import com.rothrockware.studyjazzstandards.data.seed.PRIORITY_ORDER
import com.rothrockware.studyjazzstandards.data.seed.SEED_VOICINGS
import com.rothrockware.studyjazzstandards.data.seed.VOICINGS_SEED_VERSION
import com.rothrockware.studyjazzstandards.data.store.BlobStore
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException

/**
 * Port of the web app's business logic (web/jazz_practice.html script block).
 * All persisted state lives in one JSON blob whose schema matches the web
 * app's localStorage value, so backups round-trip between implementations.
 */
class DefaultJazzRepository(
    private val store: BlobStore,
    private val clock: JazzClock = SystemJazzClock(),
    private val random: Random = Random.Default,
) : JazzRepository {

    private val _db = MutableStateFlow(loadAndMigrate())
    override val db: StateFlow<JazzDb> = _db.asStateFlow()

    override fun today(): Int = clock.today()

    // ---------- Init & migrations (web initDB) ----------

    private fun loadAndMigrate(): JazzDb {
        var db = readStore()
        var needsSave = false

        if (db == null) {
            db = freshDb()
            needsSave = true
        }

        // Voicings seed migration (voicingsSeedVersion 2): merge user familiarity
        // and image onto the fresh seed, keep custom voicings not in the seed.
        if (db.voicingsSeedVersion != VOICINGS_SEED_VERSION) {
            val prevByKey = db.voicings.associateBy { seedKey(it.name) }
            val fresh = seedVoicings().map { v ->
                val old = prevByKey[seedKey(v.name)]
                if (old != null) {
                    v.copy(
                        familiarity = old.familiarity,
                        image = old.image ?: v.image,
                    )
                } else v
            }
            val freshKeys = fresh.map { seedKey(it.name) }.toSet()
            val custom = db.voicings
                .filter { seedKey(it.name) !in freshKeys }
                .map { it.copy(name = stripTrailingParens(it.name)) }
            db = db.copy(voicings = fresh + custom, voicingsSeedVersion = VOICINGS_SEED_VERSION)
            needsSave = true
        }

        // Web parity: existing DBs missing the key are treated as already onboarded.
        if (db.onboardingComplete == null) {
            db = db.copy(onboardingComplete = true)
            needsSave = true
        }

        // Backfill sortSeed / composer / year from seed data.
        val seedMap = ALL_SONGS.associateBy { it.title }
        val patched = db.songs.mapValues { (_, sg) ->
            var s = sg
            if (s.sortSeed == 0.0) s = s.copy(sortSeed = random.nextDouble())
            val seed = seedMap[s.name]
            if (seed != null) {
                if (s.composer == null) s = s.copy(composer = seed.composer)
                if (s.year == null) s = s.copy(year = seed.year)
            }
            s
        }
        if (patched != db.songs) {
            db = db.copy(songs = patched)
            needsSave = true
        }

        if (needsSave) writeStore(db)
        return db
    }

    private fun readStore(): JazzDb? {
        val raw = store.read() ?: return null
        return try {
            JazzJson.decodeFromString<JazzDb>(raw)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun freshDb(): JazzDb {
        return JazzDb(
            songs = ALL_SONGS.associate { seed ->
                seed.title to Song(
                    name = seed.title,
                    style = seed.style,
                    composer = seed.composer,
                    year = seed.year,
                    sortSeed = random.nextDouble(),
                )
            },
            onboardingComplete = false,
            voicings = seedVoicings(),
            voicingsSeedVersion = VOICINGS_SEED_VERSION,
        )
    }

    private fun seedVoicings(): List<Voicing> {
        val tod = clock.today()
        return SEED_VOICINGS.map {
            Voicing(
                name = it.name,
                category = it.category,
                topNote = it.topNote,
                image = null,
                familiarity = 0,
                addedDate = tod,
                sortSeed = random.nextDouble(),
            )
        }
    }

    private fun stripTrailingParens(s: String): String =
        s.replace(Regex("""\s*\([^)]*\)\s*$"""), "").trim()

    private fun seedKey(name: String): String = stripTrailingParens(name).lowercase()

    // ---------- Persistence & mutation plumbing ----------

    private fun writeStore(db: JazzDb) {
        store.write(JazzJson.encodeToString(JazzDb.serializer(), db))
    }

    private inline fun mutate(transform: (JazzDb) -> JazzDb) {
        val prev = _db.value
        val next = transform(prev)
        if (next == prev) return
        writeStore(next)
        _db.value = next
    }

    private fun JazzDb.log(
        type: String,
        name: String? = null,
        passed: Boolean? = null,
        oldName: String? = null,
        oldLevel: Int? = null,
        level: Int? = null,
        style: String? = null,
        familiarity: Int? = null,
    ): JazzDb = copy(
        activity = activity + ActivityEntry(
            ts = clock.nowEpochMillis(),
            date = clock.today(),
            type = type,
            name = name,
            passed = passed,
            oldName = oldName,
            oldLevel = oldLevel,
            level = level,
            style = style,
            familiarity = familiarity,
        ),
    )

    private fun JazzDb.updateSong(name: String, transform: (Song) -> Song): JazzDb {
        val sg = songs[name] ?: return this
        return copy(songs = songs + (name to transform(sg)))
    }

    /** Web updateStreak: no-op if already counted today; +1 if yesterday, else reset to 1. */
    private fun JazzDb.updateStreak(): JazzDb {
        val tod = clock.today()
        if (lastPracticeDay == tod) return this
        val yesterday = DateInt.addDays(tod, -1)
        return copy(
            streak = if (lastPracticeDay == yesterday) streak + 1 else 1,
            lastPracticeDay = tod,
        )
    }

    // ---------- Learning lifecycle ----------

    override fun startLearning(name: String, confirmSwitch: Boolean): StartLearningResult {
        val current = _db.value.currentNewSong
        if (current != null && current != name && !confirmSwitch) {
            return StartLearningResult.NeedsConfirmation(current)
        }
        mutate { db ->
            var next = db
            val old = next.currentNewSong
            if (old != null && old != name) {
                next = next.updateSong(old) { it.copy(status = SongStatus.UNTOUCHED) }
                next = next.log(ActivityType.STOPPED_LEARNING, name = old)
            }
            next = next.updateSong(name) { it.copy(status = SongStatus.LEARNING) }
            next = next.copy(currentNewSong = name)
            next.log(ActivityType.STARTED_LEARNING, name = name)
        }
        return StartLearningResult.Started
    }

    override fun practicedToday(name: String): Boolean {
        val tod = clock.today()
        return _db.value.activity.any {
            it.type == ActivityType.PRACTICED && it.name == name && it.date == tod
        }
    }

    override fun markPracticed(name: String) {
        if (practicedToday(name)) return
        mutate { db ->
            db.log(ActivityType.PRACTICED, name = name).updateStreak()
        }
    }

    override fun markLearned(name: String) {
        if (_db.value.songs[name] == null) return
        val tod = clock.today()
        mutate { db ->
            db.updateSong(name) {
                it.copy(
                    status = SongStatus.ACTIVE,
                    currentLevel = maxOf(it.currentLevel, 2),
                    intervalIdx = 0,
                    learnedDate = tod,
                    nextReview = DateInt.addDays(tod, INTERVALS[0]),
                )
            }
                .copy(currentNewSong = null)
                .log(ActivityType.LEARNED, name = name)
                .updateStreak()
        }
    }

    // ---------- Review ----------

    override fun doReview(name: String, passed: Boolean): ReviewOutcome? {
        val sg = _db.value.songs[name] ?: return null
        val tod = clock.today()
        val outcome: ReviewOutcome
        val updated: Song
        if (passed) {
            val nextIdx = minOf(sg.intervalIdx + 1, INTERVALS.size - 1)
            val graduated = nextIdx >= INTERVALS.size - 1
            updated = sg.copy(
                intervalIdx = nextIdx,
                nextReview = DateInt.addDays(tod, INTERVALS[nextIdx]),
                status = if (graduated) SongStatus.GRADUATED else sg.status,
                currentLevel = if (graduated) 3 else sg.currentLevel,
                reviews = sg.reviews + Review(tod, passed = true),
            )
            outcome = if (graduated) ReviewOutcome.Graduated else ReviewOutcome.NextInDays(INTERVALS[nextIdx])
        } else {
            updated = sg.copy(
                intervalIdx = 0,
                nextReview = DateInt.addDays(tod, INTERVALS[0]),
                reviews = sg.reviews + Review(tod, passed = false),
            )
            outcome = ReviewOutcome.BackTomorrow
        }
        mutate { db ->
            db.copy(songs = db.songs + (name to updated))
                .log(ActivityType.REVIEW, name = name, passed = passed)
                .updateStreak()
        }
        return outcome
    }

    internal fun scoreSong(sg: Song, tod: Int): Double {
        val overdue = maxOf(0, DateInt.daysBetween(sg.nextReview ?: tod, tod))
        val fragility = (INTERVALS.size - 1) - maxOf(0, sg.intervalIdx)
        val recent = sg.reviews.takeLast(5)
        val failRate = if (recent.isNotEmpty()) {
            recent.count { !it.passed }.toDouble() / recent.size
        } else 0.0
        return overdue * 0.5 + fragility * 10 + failRate * 20
    }

    private fun scoreComparator(tod: Int): Comparator<Song> =
        compareByDescending<Song> { scoreSong(it, tod) }.thenBy { it.sortSeed }

    private fun Song.reviewedOn(tod: Int): Boolean = reviews.any { it.date == tod }

    private fun extraReviewCount(db: JazzDb, tod: Int): Int {
        val extra = db.extraReviewsToday ?: return 0
        return if (extra.date == tod) extra.count else 0
    }

    override fun normalizeReviewQueue(): Boolean {
        val tod = clock.today()
        var changed = false
        mutate { db ->
            val max = (if (db.reviewMax > 0) db.reviewMax else 1) + extraReviewCount(db, tod)
            val active = db.songs.values
                .filter { it.status == SongStatus.ACTIVE && it.nextReview != null }
                .sortedWith(scoreComparator(tod))
            val due = active.filter { it.nextReview!! <= tod && !it.reviewedOn(tod) }
            val reviewedCount = active.count { it.reviewedOn(tod) }
            val slotsUsed = due.size + reviewedCount
            var next = db
            if (due.size > max - reviewedCount) {
                // Web parity note: when reviewedCount > max the JS negative slice
                // takes from the end; here we push ALL due songs forward, which is
                // the sane interpretation of "over cap" (only reachable by lowering
                // reviewMax after reviewing more than the new cap).
                due.drop((max - reviewedCount).coerceAtLeast(0))
                    .forEachIndexed { i, sg ->
                        next = next.updateSong(sg.name) { it.copy(nextReview = DateInt.addDays(tod, i + 1)) }
                        changed = true
                    }
            } else if (slotsUsed < max && reviewedCount == 0) {
                val upcoming = active.filter { it.nextReview!! > tod && !it.reviewedOn(tod) }
                upcoming.take(max - slotsUsed).forEach { sg ->
                    next = next.updateSong(sg.name) { it.copy(nextReview = tod) }
                    changed = true
                }
            }
            next
        }
        return changed
    }

    override fun reviewAnotherSong(): Boolean {
        val tod = clock.today()
        val upcoming = _db.value.songs.values
            .filter {
                it.status == SongStatus.ACTIVE && it.nextReview != null &&
                    it.nextReview > tod && !it.reviewedOn(tod)
            }
            .sortedWith(scoreComparator(tod))
        val target = upcoming.firstOrNull() ?: return false
        mutate { db ->
            val extra = db.extraReviewsToday
            db.updateSong(target.name) { it.copy(nextReview = tod) }
                .copy(
                    extraReviewsToday = if (extra == null || extra.date != tod) {
                        com.rothrockware.studyjazzstandards.data.model.ExtraReviews(tod, 1)
                    } else {
                        extra.copy(count = extra.count + 1)
                    },
                )
        }
        return true
    }

    override fun setReviewMax(value: Int) {
        if (value < 1) return
        mutate { it.copy(reviewMax = value) }
    }

    override fun dueReviews(): List<Song> {
        val tod = clock.today()
        return _db.value.songs.values
            .filter {
                it.status == SongStatus.ACTIVE && it.nextReview != null &&
                    it.nextReview <= tod && !it.reviewedOn(tod)
            }
            .sortedWith(scoreComparator(tod))
    }

    override fun reviewedToday(): Boolean {
        val tod = clock.today()
        return _db.value.songs.values.any { it.reviewedOn(tod) }
    }

    override fun hasUpcomingReviews(): Boolean {
        val tod = clock.today()
        return _db.value.songs.values.any {
            it.status == SongStatus.ACTIVE && it.nextReview != null &&
                it.nextReview > tod && !it.reviewedOn(tod)
        }
    }

    // ---------- Songs ----------

    private fun JazzDb.hasSongNamed(name: String): Boolean =
        songs.keys.any { it.lowercase() == name.lowercase() }

    override fun addSong(name: String, style: String, level: Int): AddResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddResult.DuplicateName
        if (_db.value.hasSongNamed(trimmed)) return AddResult.DuplicateName
        val tod = clock.today()
        mutate { db ->
            db.copy(
                songs = db.songs + (trimmed to Song(
                    name = trimmed,
                    style = style,
                    baseLevel = level,
                    currentLevel = level,
                    status = if (level >= 2) SongStatus.ACTIVE else SongStatus.UNTOUCHED,
                    intervalIdx = if (level >= 2) 0 else -1,
                    nextReview = if (level >= 2) DateInt.addDays(tod, 1) else null,
                    learnedDate = if (level >= 2) tod else null,
                    sortSeed = random.nextDouble(),
                )),
            ).log(ActivityType.SONG_ADDED, name = trimmed, style = style, level = level)
        }
        return AddResult.Ok
    }

    override fun editSong(oldName: String, newName: String, style: String, baseLevel: Int): AddResult {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return AddResult.DuplicateName
        val db0 = _db.value
        val sg = db0.songs[oldName] ?: return AddResult.DuplicateName
        val nameChanged = trimmed != oldName
        if (nameChanged && db0.hasSongNamed(trimmed)) return AddResult.DuplicateName
        val oldLevel = sg.baseLevel
        mutate { db ->
            val updated = sg.copy(name = trimmed, style = style, baseLevel = baseLevel)
            var songs = db.songs
            if (nameChanged) {
                songs = songs - oldName + (trimmed to updated)
            } else {
                songs = songs + (oldName to updated)
            }
            db.copy(
                songs = songs,
                currentNewSong = if (nameChanged && db.currentNewSong == oldName) trimmed else db.currentNewSong,
            ).log(ActivityType.SONG_EDITED, oldName = oldName, name = trimmed, style = style, level = baseLevel, oldLevel = oldLevel)
        }
        return AddResult.Ok
    }

    override fun removeSong(name: String) {
        if (_db.value.songs[name] == null) return
        mutate { db ->
            db.log(ActivityType.SONG_REMOVED, name = name)
                .copy(
                    songs = db.songs - name,
                    currentNewSong = if (db.currentNewSong == name) null else db.currentNewSong,
                )
        }
    }

    override fun nextSuggestedSong(): Song? {
        val db = _db.value
        for (name in PRIORITY_ORDER) {
            val sg = db.songs[name]
            if (sg != null && sg.status == SongStatus.UNTOUCHED) return sg
        }
        return db.songs.values.firstOrNull { it.status == SongStatus.UNTOUCHED }
    }

    // ---------- Repertoire ----------

    override fun addRepertoire(name: String, composer: String?, notes: String?): AddResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddResult.DuplicateName
        if (_db.value.repertoire.any { it.name.lowercase() == trimmed.lowercase() }) {
            return AddResult.DuplicateName
        }
        mutate { db ->
            db.copy(
                repertoire = db.repertoire + RepertoirePiece(
                    name = trimmed,
                    composer = composer?.trim()?.takeIf { it.isNotEmpty() },
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    addedDate = clock.today(),
                ),
            ).log(ActivityType.REPERTOIRE_ADDED, name = trimmed)
        }
        return AddResult.Ok
    }

    override fun editRepertoire(oldName: String, newName: String, composer: String?, notes: String?): AddResult {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return AddResult.DuplicateName
        val db0 = _db.value
        if (db0.repertoire.none { it.name == oldName }) return AddResult.DuplicateName
        val nameChanged = trimmed.lowercase() != oldName.lowercase()
        if (nameChanged && db0.repertoire.any { it.name.lowercase() == trimmed.lowercase() }) {
            return AddResult.DuplicateName
        }
        mutate { db ->
            db.copy(
                repertoire = db.repertoire.map { p ->
                    if (p.name == oldName) {
                        p.copy(
                            name = trimmed,
                            composer = composer?.trim()?.takeIf { it.isNotEmpty() },
                            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                        )
                    } else p
                },
            )
        }
        return AddResult.Ok
    }

    override fun removeRepertoire(name: String) {
        mutate { db ->
            db.copy(repertoire = db.repertoire.filter { it.name != name })
                .log(ActivityType.REPERTOIRE_REMOVED, name = name)
        }
    }

    override fun markRepertoireReviewed(name: String) {
        if (_db.value.repertoire.none { it.name == name }) return
        mutate { db ->
            db.copy(
                repertoire = db.repertoire.map { p ->
                    if (p.name == name) p.copy(lastReviewed = clock.today(), pinnedDate = null) else p
                },
            ).log(ActivityType.REPERTOIRE_REVIEWED, name = name)
        }
    }

    override fun repertoireContinueTomorrow(name: String) {
        if (_db.value.repertoire.none { it.name == name }) return
        mutate { db ->
            db.copy(
                repertoire = db.repertoire.map { p ->
                    if (p.name == name) p.copy(pinnedDate = DateInt.addDays(clock.today(), 1)) else p
                },
            ).log(ActivityType.REPERTOIRE_DEFERRED, name = name)
        }
    }

    override fun featuredRepertoire(): RepertoirePiece? {
        val pieces = _db.value.repertoire
        if (pieces.isEmpty()) return null
        val tod = clock.today()
        pieces.firstOrNull { it.pinnedDate == tod }?.let { return it }
        pieces.firstOrNull { it.lastReviewed == tod }?.let { return it }
        return pieces.sortedWith(
            Comparator { a, b ->
                when {
                    a.lastReviewed == null && b.lastReviewed == null -> a.addedDate - b.addedDate
                    a.lastReviewed == null -> -1
                    b.lastReviewed == null -> 1
                    else -> a.lastReviewed - b.lastReviewed
                }
            },
        ).first()
    }

    // ---------- Voicings ----------

    override fun addVoicing(name: String, category: String, topNote: String?, image: String?, familiarity: Int): AddResult {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return AddResult.DuplicateName
        if (_db.value.voicings.any { it.name.lowercase() == trimmed.lowercase() }) {
            return AddResult.DuplicateName
        }
        mutate { db ->
            db.copy(
                voicings = db.voicings + Voicing(
                    name = trimmed,
                    category = category,
                    topNote = topNote?.trim()?.takeIf { it.isNotEmpty() }?.let(::normTopNote),
                    image = image?.trim()?.takeIf { it.isNotEmpty() },
                    familiarity = familiarity,
                    addedDate = clock.today(),
                    sortSeed = random.nextDouble(),
                ),
            ).log(ActivityType.VOICING_ADDED, name = trimmed)
        }
        return AddResult.Ok
    }

    override fun editVoicing(oldName: String, newName: String, category: String, topNote: String?, image: String?, familiarity: Int): AddResult {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return AddResult.DuplicateName
        val db0 = _db.value
        if (db0.voicings.none { it.name == oldName }) return AddResult.DuplicateName
        val nameChanged = trimmed.lowercase() != oldName.lowercase()
        if (nameChanged && db0.voicings.any { it.name.lowercase() == trimmed.lowercase() }) {
            return AddResult.DuplicateName
        }
        mutate { db ->
            db.copy(
                voicings = db.voicings.map { v ->
                    if (v.name == oldName) {
                        v.copy(
                            name = trimmed,
                            category = category,
                            topNote = topNote?.trim()?.takeIf { it.isNotEmpty() }?.let(::normTopNote),
                            image = image?.trim()?.takeIf { it.isNotEmpty() },
                            familiarity = familiarity,
                        )
                    } else v
                },
            )
        }
        return AddResult.Ok
    }

    override fun removeVoicing(name: String) {
        mutate { db ->
            db.copy(voicings = db.voicings.filter { it.name != name })
                .log(ActivityType.VOICING_REMOVED, name = name)
        }
    }

    override fun voicingPracticedToday(name: String): Boolean {
        val tod = clock.today()
        return _db.value.activity.any {
            it.type == ActivityType.VOICING_PRACTICED && it.name == name && it.date == tod
        }
    }

    override fun voicingPracticed(name: String) {
        val v = _db.value.voicings.firstOrNull { it.name == name } ?: return
        if (voicingPracticedToday(name)) return
        val newFam = minOf(v.familiarity + 1, 2)
        mutate { db ->
            db.copy(
                voicings = db.voicings.map { if (it.name == name) it.copy(familiarity = newFam) else it },
            )
                .log(ActivityType.VOICING_PRACTICED, name = name, familiarity = newFam)
                .updateStreak()
        }
    }

    override fun featuredVoicing(): Voicing? {
        val vs = _db.value.voicings
        if (vs.isEmpty()) return null
        val minFam = vs.minOf { it.familiarity }
        if (minFam >= 2) return null
        val candidates = vs.filter { it.familiarity == minFam }.sortedBy { it.sortSeed }
        val dayCount = DateInt.daysBetween(20200101, clock.today())
        val idx = ((dayCount % candidates.size) + candidates.size) % candidates.size
        return candidates[idx]
    }

    // ---------- Onboarding / reset ----------

    override fun completeOnboarding(levels: Map<String, Int>) {
        val tod = clock.today()
        mutate { db ->
            var songs = db.songs
            levels.forEach { (name, lvl) ->
                val sg = songs[name] ?: return@forEach
                songs = songs + (name to sg.copy(
                    baseLevel = lvl,
                    currentLevel = lvl,
                    status = if (lvl >= 2) SongStatus.ACTIVE else SongStatus.UNTOUCHED,
                    intervalIdx = if (lvl >= 2) 0 else -1,
                    nextReview = if (lvl >= 2) DateInt.addDays(tod, 1) else null,
                    learnedDate = if (lvl >= 2) (sg.learnedDate ?: tod) else null,
                ))
            }
            db.copy(songs = songs, onboardingComplete = true)
        }
    }

    override fun skipOnboarding() {
        mutate { it.copy(onboardingComplete = true) }
    }

    override fun resetAll() {
        store.clear()
        _db.value = loadAndMigrate()
    }
}
