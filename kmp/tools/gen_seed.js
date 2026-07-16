// One-time generator: web/jazz_data.js -> kmp SeedData.kt
const fs = require('fs');
const path = require('path');

const root = '/Users/rothrock/Desktop/Standards Practice';
const src = fs.readFileSync(path.join(root, 'web/jazz_data.js'), 'utf8');
// jazz_data.js declares consts; eval in a function scope and return them.
const { ALL_SONGS, PRIORITY_ORDER } = new Function(
  src + '\nreturn { ALL_SONGS, PRIORITY_ORDER };'
)();

if (ALL_SONGS.length !== 272) throw new Error('expected 272 songs, got ' + ALL_SONGS.length);

const kEsc = (s) =>
  s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$');

const seedVoicings = [
  ['Major 7', 'Major', '7'],
  ['Major 9', 'Major', '9'],
  ['Major 6/9', 'Major', '6'],
  ['Major triad', 'Major', '3'],
  ['Minor 7', 'Minor', '7'],
  ['Minor 9', 'Minor', '9'],
  ['Minor 11', 'Minor', '11'],
  ['Minor 6', 'Minor', '6'],
  ['Dominant 7', 'Dominant', '7'],
  ['Dominant 9', 'Dominant', '9'],
  ['Dominant 13', 'Dominant', '13'],
  ['Dominant 7♭9', 'Dominant', '♭9'],
  ['Diminished 7', 'Diminished', '7'],
  ['Half-diminished 7', 'Diminished', '♭5'],
  ['Diminished triad', 'Diminished', '♭3'],
];

const songs = ALL_SONGS.map(
  ([t, s, c, y]) => `    SeedSong("${kEsc(t)}", "${kEsc(s)}", "${kEsc(c)}", ${y}),`
).join('\n');

const priority = PRIORITY_ORDER.map((t) => `    "${kEsc(t)}",`).join('\n');

const voicings = seedVoicings.map(
  ([n, c, t]) => `    SeedVoicing("${kEsc(n)}", "${kEsc(c)}", "${kEsc(t)}"),`
).join('\n');

const out = `package com.rothrockware.studyjazzstandards.data.seed

// GENERATED from web/jazz_data.js + web/jazz_practice.html seed constants.
// Regenerate with the gen_seed.js script if the web seed data changes.

data class SeedSong(
    val title: String,
    val style: String,
    val composer: String,
    val year: Int,
)

data class SeedVoicing(
    val name: String,
    val category: String,
    val topNote: String,
)

val INTERVALS: List<Int> = listOf(1, 3, 7, 21, 60, 180)

const val VOICINGS_SEED_VERSION: Int = 2

val VOICING_CATEGORIES: List<String> = listOf("Major", "Minor", "Dominant", "Diminished")

val LEVEL_DESCS: List<String> = listOf(
    "Complete unfamiliarity \\u2014 never played it",
    "Could fake my way through in a pinch",
    "Comfortable enough to play at a jam session",
    "Second nature \\u2014 fully performance-ready",
)

val VOICING_FAM_DESCS: List<String> = listOf(
    "No familiarity \\u2014 haven't worked it out yet",
    "Getting it under my fingers",
    "Mastered \\u2014 instant recall",
)

val SEED_VOICINGS: List<SeedVoicing> = listOf(
${voicings}
)

val PRIORITY_ORDER: List<String> = listOf(
${priority}
)

val ALL_SONGS: List<SeedSong> = listOf(
${songs}
)
`;

const dest = path.join(
  root,
  'kmp/data/src/commonMain/kotlin/com/rothrockware/studyjazzstandards/data/seed/SeedData.kt'
);
fs.writeFileSync(dest, out);
console.log('wrote', dest, '| songs:', ALL_SONGS.length, '| priority:', PRIORITY_ORDER.length);
