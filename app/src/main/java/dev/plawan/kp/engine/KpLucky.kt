package dev.plawan.kp.engine

import java.time.LocalDate

// Lucky colours / numbers / directions per planet, plus the "how to use them today" kit.
//
// SOURCE RULE (AGENTS.md): every field below carries its Reader citation. A field whose *Src is empty is a
// traditional correspondence that is NOT in the Readers; the UI flags those as "traditional".
// All line numbers refer to the .txt files in kp-readers/ ("R2" = J_KP reader_2_fundamental Principles of Astrology,
// chapter 18 "To each planet ... Lucky colour, Lucky number"; "R3"/"R4" = Reader 3 / Reader 4 planet-colour table).
//
// What the Readers actually say: colour, number, day and Moon-star are tied to a planet and are used when that planet
// rules the period (e.g. R2 ~l.17084-17100: during Mercury's period the choice is mostly green, houses/cars/taxis with
// number 5). Applying them to ONE day's "lucky planet" is this app's extension, and is labelled as such in the UI.

data class PlanetLore(
    val planet: String,
    val colours: List<String>, val coloursSrc: String,
    val numbers: List<Int>, val numbersSrc: String, val series: Boolean = false,   // series: book lists n, n+9, n+18 ...
    val direction: String, val directionSrc: String,
    val day: String, val daySrc: String,
    val stars: List<String> = emptyList(), val starsSrc: String = "",
    val avoidNumbers: List<Int> = emptyList(), val avoidNumbersSrc: String = "",
    val note: String = "",
)

val PLANET_LORE: Map<String, PlanetLore> = listOf(
    PlanetLore(
        "Sun",
        listOf("Orange", "Dark red"), "R2 ~l.15223 (orange); R3 ~l.25414 / R4 ~l.17217 (orange or dark red)",
        listOf(1, 4), "R2 ~l.15522",
        direction = "East", directionSrc = "R2 ~l.15222",
        day = "Sunday", daySrc = "R2 ~l.15513",
        stars = listOf("Krittika", "Uttara Phalguni", "Uttara Ashadha"), starsSrc = "R2 ~l.15517 (Karthigai, Uthrapalguni, Uthrashada)",
        note = "Sun stands for government and high officials; its gems are advised for security in service and favour from officials (R2 ~l.15525-15531).",
    ),
    PlanetLore(
        "Moon",
        listOf("White", "Silvery"), "R2 ~l.15912 (white; some say sea-green); R3 ~l.25415 / R4 ~l.17218 (white, silvery)",
        listOf(2, 7), "R2 ~l.15905",
        direction = "North-West", directionSrc = "",
        day = "Monday", daySrc = "R2 ~l.15898",
        avoidNumbers = listOf(8), avoidNumbersSrc = "R2 ~l.15908 (avoid 8)",
    ),
    PlanetLore(
        "Mars",
        listOf("Red", "Deep red"), "R2 ~l.16533 (red is ruled by Mars); R3 ~l.25436 (deep red)",
        listOf(9), "R2 ~l.16526 (1, 2, 4, 7 also auspicious)",
        direction = "South", directionSrc = "",
        day = "Tuesday", daySrc = "R2 ~l.16512",
        note = "Red offers authority and power and helps against dullness; blue or green cool a rash, impulsive person (R2 ~l.16533-16567).",
    ),
    PlanetLore(
        "Mercury",
        listOf("Green"), "R2 ~l.17084; R3 ~l.25418 / R4 ~l.17220 (slate green)",
        listOf(5), "R2 ~l.17096-17099 (5, 14, 23, 32, 41, 50)", series = true,
        direction = "North", directionSrc = "R2 ~l.17086",
        day = "Wednesday", daySrc = "R2 ~l.6608",
        note = "Mercury is the chief governor of education (R2 ~l.17104).",
    ),
    PlanetLore(
        "Jupiter",
        listOf("Purple", "Violet", "Blue", "White"), "R3 ~l.25437 colour table (OCR-garbled; Jupiter's label sits next to this row, R4 ~l.17225 has the row without labels)",
        listOf(3), "",
        direction = "North-East", directionSrc = "R2 ~l.17918",
        day = "Thursday", daySrc = "",
    ),
    PlanetLore(
        "Venus",
        listOf("White", "Cream", "Ivory"), "R2 ~l.18273",
        listOf(6), "R2 ~l.18274 (6, 15, 24, 33, 42, 51)", series = true,
        direction = "South-East", directionSrc = "",
        day = "Friday", daySrc = "R2 ~l.18275",
        stars = listOf("Bharani", "Purva Phalguni", "Purva Ashadha"), starsSrc = "R2 ~l.18287",
    ),
    PlanetLore(
        "Saturn",
        listOf("Blue", "Black", "Dark brown", "Indigo"), "R2 ~l.19348 (blue, if Saturn is a benefic), ~l.19092 (black, dark brown, indigo); R3 ~l.25440",
        listOf(8), "R2 ~l.19352 (8, 17, 26, 35, 44, 53)", series = true,
        direction = "West", directionSrc = "",
        day = "Saturday", daySrc = "R2 ~l.19353",
        stars = listOf("Pushya", "Anuradha", "Uttara Bhadrapada"), starsSrc = "R2 ~l.19357",
        note = "The Reader calls blue lucky only if Saturn is a benefic (R2 ~l.19348).",
    ),
    PlanetLore(
        "Rahu",
        listOf("Mixed colours"), "R3 ~l.25441 (Rahu & Ketu: mixed colour)",
        listOf(4), "",
        direction = "South-West", directionSrc = "",
        day = "", daySrc = "",
    ),
    PlanetLore(
        "Ketu",
        listOf("Mixed colours"), "R3 ~l.25441 / R4 ~l.17229 (Rahu & Ketu: mixed colour)",
        listOf(7), "",
        direction = "South-West", directionSrc = "",
        day = "", daySrc = "",
    ),
).associateBy { it.planet }

fun digitalRoot(n: Int): Int = if (n <= 0) 0 else 1 + (n - 1) % 9
fun numberSeries(base: Int, max: Int = 55): List<Int> = generateSequence(base) { it + 9 }.takeWhile { it <= max }.toList()

data class LuckyUse(
    val title: String,
    val tips: List<String>,
    val windows: List<TWindow>,
    val windowLabel: String,
    val avoid: List<TWindow>,
    val basis: String,
)

data class LuckyKit(
    val primary: PlanetLore, val secondary: PlanetLore?, val stress: PlanetLore,
    val colours: List<String>, val numbers: List<Int>, val numberSeries: List<Int>,
    val avoidNumbers: List<Int>, val avoidColours: List<String>,
    val dateNote: String?, val dayLordIsLucky: Boolean,
    val uses: List<LuckyUse>, val footer: String,
)

private const val EXTENSION_NOTE =
    "The Readers tie colour and number to a planet's period; using them for a single day's lucky planet is this app's extension."

private fun numTxt(l: PlanetLore) = l.numbers.joinToString(", ") + if (l.numbersSrc.isEmpty()) " (traditional)" else ""
private fun dirTxt(l: PlanetLore) = l.direction + if (l.directionSrc.isEmpty()) " (traditional)" else ""

fun luckyKit(date: LocalDate, luckyP: String, secondP: String?, stressP: String, dayLord: String, areas: List<AreaReport>): LuckyKit {
    val p = PLANET_LORE.getValue(luckyP)
    val s = secondP?.takeIf { it != luckyP }?.let { PLANET_LORE.getValue(it) }
    val st = PLANET_LORE.getValue(stressP)

    val colours = (p.colours + (s?.colours?.take(1) ?: emptyList())).distinctBy { it.lowercase() }
    val numbers = (p.numbers + (s?.numbers ?: emptyList())).distinct()
    val series = if (p.series) numberSeries(p.numbers.first()) else emptyList()
    val avoidColours = if (st.planet == p.planet) emptyList() else st.colours.filter { c -> colours.none { it.equals(c, true) } }.take(2)

    val root = digitalRoot(date.dayOfMonth)
    fun hits(l: PlanetLore) = l.numbers.any { digitalRoot(it) == root }
    val dateNote = when {
        hits(p) -> "Today's date number ${date.dayOfMonth} reduces to $root, one of ${p.planet}'s numbers."
        s != null && hits(s) -> "Today's date number ${date.dayOfMonth} reduces to $root, one of ${s.planet}'s numbers."
        else -> null
    }

    fun area(k: String) = areas.first { it.key == k }
    fun win(a: AreaReport) = a.best.take(2) to (if (a.score >= 40) "Best window" else "Least heavy window")
    val cs = p.colours.joinToString(" / ").lowercase()
    val n0 = numTxt(p)
    val dir = dirTxt(p)

    val study = area("study"); val sw = win(study)
    val studyLine = when {
        study.score >= 60 -> "Study score ${study.score}: a better-than-usual day for sitting exams and learning new topics."
        study.score >= 40 -> "Study score ${study.score}: an ordinary day for study; steady revision works."
        else -> "Study score ${study.score}: heavier than usual; revise what you already know and avoid cramming new subjects."
    }
    val exam = LuckyUse(
        "Exams & study",
        listOf(
            "Wear or carry $cs (shirt, pen case, bag, scarf).",
            "Where the choice is yours (seat, roll or slot number, locker, room), prefer $n0.",
            "Study or sit facing $dir.",
            studyLine,
            "Mercury is the chief governor of education (R2 ~l.17104), so green and number 5 are a natural backup on any day.",
        ),
        sw.first, sw.second, study.avoid.take(2), EXTENSION_NOTE,
    )

    val career = area("career"); val cw = win(career)
    val interview = LuckyUse(
        "Interviews & meetings",
        buildList {
            add("Wear $cs; if you choose a slot or seat, prefer $n0.")
            if (p.note.isNotEmpty() && (p.planet == "Sun" || p.planet == "Mars")) add(p.note)
            add("Start the important part inside the window below and keep big decisions out of the caution window.")
        },
        cw.first, cw.second, career.avoid.take(2), EXTENSION_NOTE,
    )

    val money = area("money"); val mw = win(money)
    val deals = LuckyUse(
        "Negotiating & paperwork",
        listOf(
            "Sign, negotiate or collect dues in the window below; postpone impulsive spending and lending in the caution window.",
            "Colour ($cs) is a calm anchor, not a reason to take risks.",
        ),
        mw.first, mw.second, money.avoid.take(2), EXTENSION_NOTE,
    )

    val travel = area("travel"); val tw = win(travel)
    val trips = LuckyUse(
        "Travel & bookings",
        listOf(
            "Where you can choose (seat, berth, room, flight or train number), prefer $n0.",
            "Lucky direction today: $dir; Mercury's direction, north, is tied in R2 ~l.17086 to moving ahead in Mercury's period.",
        ),
        tw.first, tw.second, travel.avoid.take(2), EXTENSION_NOTE,
    )

    val mood = LuckyUse(
        "Tune your mood with colour",
        listOf(
            "Feeling rash or impulsive: choose blue or green to cool down and act with caution.",
            "Feeling dull, gloomy or lethargic: choose red for energy and a fresh start.",
        ),
        emptyList(), "", emptyList(), "R2 ~l.16533-16549 (Mars colour passage); this is the Reader's own use of colour.",
    )

    return LuckyKit(
        p, s, st, colours, numbers, series, p.avoidNumbers, avoidColours, dateNote, dayLord == luckyP,
        listOf(exam, interview, deals, trips, mood),
        "Colour and number are supportive habits, not a guarantee; preparation and effort decide results. Not for lottery or betting.",
    )
}
