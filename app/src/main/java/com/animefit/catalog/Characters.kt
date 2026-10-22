package com.animefit.catalog

import com.animefit.data.Measure
import com.animefit.data.Sex

enum class Category(val label: String) { ANIME("Anime"), FAMOSO("Famosi") }

/** Tipo di giornata della scheda e quanti esercizi per muscolo contiene. */
enum class DayType(val label: String, val slots: List<Pair<Muscle, Int>>) {
    PUSH("Spinta: petto, spalle, tricipiti",
        listOf(Muscle.PETTO to 2, Muscle.SPALLE to 2, Muscle.TRICIPITI to 1, Muscle.CORE to 1)),
    PULL("Tirata: dorso, bicipiti, avambracci",
        listOf(Muscle.DORSO to 2, Muscle.BICIPITI to 1, Muscle.AVAMBRACCI to 1, Muscle.CORE to 1)),
    GAMBE("Gambe e polpacci",
        listOf(Muscle.GAMBE to 3, Muscle.POLPACCI to 1, Muscle.CORE to 1)),
    UPPER("Parte alta completa",
        listOf(Muscle.PETTO to 1, Muscle.DORSO to 1, Muscle.SPALLE to 1, Muscle.BICIPITI to 1, Muscle.TRICIPITI to 1)),
    LOWER("Parte bassa e addome",
        listOf(Muscle.GAMBE to 2, Muscle.POLPACCI to 1, Muscle.CORE to 2)),
    FULL("Total body",
        listOf(Muscle.GAMBE to 1, Muscle.PETTO to 1, Muscle.DORSO to 1, Muscle.SPALLE to 1, Muscle.CORE to 1)),
    CORE_CARDIO("Addominali e cardio",
        listOf(Muscle.CARDIO to 2, Muscle.CORE to 3)),
    RIPOSO("Recupero attivo",
        listOf(Muscle.MOBILITA to 2, Muscle.CARDIO to 1)),
}

/** Stile di allenamento: ripetizioni, serie, recuperi e divisione settimanale (lun..dom). */
enum class Style(
    val label: String,
    val gymReps: Int,
    val bodyweightReps: Int,
    val sets: Int,
    val restSeconds: Int,
    val week: List<DayType>,
) {
    FORZA("Forza", 6, 8, 4, 120,
        listOf(DayType.PUSH, DayType.PULL, DayType.GAMBE, DayType.CORE_CARDIO, DayType.UPPER, DayType.LOWER, DayType.RIPOSO)),
    IPERTROFIA("Massa muscolare", 10, 12, 3, 90,
        listOf(DayType.PUSH, DayType.PULL, DayType.GAMBE, DayType.CORE_CARDIO, DayType.UPPER, DayType.LOWER, DayType.RIPOSO)),
    RESISTENZA("Resistenza", 15, 20, 3, 45,
        listOf(DayType.FULL, DayType.CORE_CARDIO, DayType.FULL, DayType.CORE_CARDIO, DayType.FULL, DayType.CORE_CARDIO, DayType.RIPOSO)),
    ATLETICO("Atletico", 10, 15, 3, 60,
        listOf(DayType.FULL, DayType.CORE_CARDIO, DayType.UPPER, DayType.LOWER, DayType.FULL, DayType.CORE_CARDIO, DayType.RIPOSO)),
    DEFINIZIONE("Definizione", 12, 18, 3, 40,
        listOf(DayType.UPPER, DayType.CORE_CARDIO, DayType.LOWER, DayType.CORE_CARDIO, DayType.FULL, DayType.CORE_CARDIO, DayType.RIPOSO)),
}

/**
 * Esercizio "firma" del personaggio. [amount] = quantità per livello
 * (principiante, intermedio, avanzato), aumentata di [weeklyInc] a settimana fino a [cap].
 */
data class SigItem(
    val exerciseId: String,
    val sets: Int,
    val amount: List<Int>,
    val weeklyInc: Int,
    val cap: Int,
)

data class Signature(
    val title: String,
    val note: String,
    /** Giorni della settimana in cui compare: 0 = lunedì ... 6 = domenica. */
    val days: Set<Int>,
    val items: List<SigItem>,
    /** true: nei giorni della firma si fa solo lei (più gli extra per i punti deboli). */
    val replacesWorkout: Boolean = false,
)

data class Character(
    val id: String,
    val name: String,
    val origin: String,
    val category: Category,
    val sex: Sex,
    val color: Long,
    /** 1..5 */
    val difficulty: Int,
    val style: Style,
    val description: String,
    val targets: Map<Measure, Double>,
    val focus: Set<Area>,
    val signature: Signature,
    /** Fisico originale esagerato: il 100% è una versione umana raggiungibile. */
    val fantasy: Boolean = false,
) {
    fun target(m: Measure): Double = targets.getValue(m)
}

private fun targets(
    spalle: Double, torace: Double, vita: Double, fianchi: Double,
    braccio: Double, avambraccio: Double, coscia: Double, polpaccio: Double, definizione: Double,
) = mapOf(
    Measure.SPALLE to spalle, Measure.TORACE to torace, Measure.VITA to vita,
    Measure.FIANCHI to fianchi, Measure.BRACCIO to braccio, Measure.AVAMBRACCIO to avambraccio,
    Measure.COSCIA to coscia, Measure.POLPACCIO to polpaccio, Measure.DEFINIZIONE to definizione,
)

private fun sig(id: String, sets: Int, a: Int, b: Int, c: Int, inc: Int, cap: Int) =
    SigItem(id, sets, listOf(a, b, c), inc, cap)

private const val CIRCUITO = "Circuito: fai gli esercizi uno dopo l'altro, riposa 1 minuto e ripeti per il numero di giri indicato."

object Characters {
    val all: List<Character> = listOf(
        // ---------------- ANIME ----------------
        Character(
            "saitama", "Saitama", "One Punch Man", Category.ANIME, Sex.UOMO, 0xFF00F5FF, 2, Style.RESISTENZA,
            "Fisico asciutto e atletico, senza esagerare. Il segreto è la costanza: piegamenti, addominali, squat e corsa ogni giorno.",
            targets(1.04, 0.74, 0.56, 0.70, 0.225, 0.185, 0.38, 0.25, 0.70),
            setOf(Area.VITA, Area.ADDOMINALI, Area.GAMBE),
            Signature("La routine di Saitama",
                "Ogni giorno, senza saltare. Dividi le ripetizioni in quante serie vuoi e la corsa in più uscite.",
                setOf(0, 1, 2, 3, 4, 5),
                listOf(
                    sig("piegamenti", 1, 30, 60, 100, 10, 100),
                    sig("sit_up", 1, 30, 60, 100, 10, 100),
                    sig("squat", 1, 30, 60, 100, 10, 100),
                    sig("corsa_km", 1, 2, 5, 10, 1, 10),
                ),
                replacesWorkout = true),
        ),
        Character(
            "goku", "Goku", "Dragon Ball", Category.ANIME, Sex.UOMO, 0xFFFF9100, 5, Style.IPERTROFIA,
            "Massa muscolare piena e potente: spalle enormi, petto largo e braccia grosse, ma con vita stretta.",
            targets(1.16, 0.82, 0.56, 0.72, 0.270, 0.205, 0.42, 0.27, 0.80),
            setOf(Area.SPALLE, Area.TORACE, Area.BRACCIA),
            Signature("Allenamento Saiyan", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("burpees", 4, 6, 10, 15, 1, 25),
                    sig("piegamenti", 4, 8, 15, 25, 1, 35),
                    sig("jump_squat", 4, 8, 12, 20, 1, 30),
                )),
            fantasy = true,
        ),
        Character(
            "vegeta", "Vegeta", "Dragon Ball", Category.ANIME, Sex.UOMO, 0xFF2979FF, 5, Style.FORZA,
            "Compatto, denso e scolpito. Allenamento di forza pura con addominali sempre in vista.",
            targets(1.13, 0.80, 0.55, 0.70, 0.260, 0.200, 0.41, 0.27, 0.85),
            setOf(Area.SPALLE, Area.ADDOMINALI, Area.BRACCIA),
            Signature("Orgoglio del principe", CIRCUITO, setOf(1, 4),
                listOf(
                    sig("piegamenti", 5, 5, 12, 20, 1, 30),
                    sig("plank", 3, 30, 60, 90, 5, 150),
                    sig("burpees", 3, 5, 10, 15, 1, 25),
                )),
            fantasy = true,
        ),
        Character(
            "levi", "Levi Ackerman", "L'attacco dei giganti", Category.ANIME, Sex.UOMO, 0xFF39FF14, 4, Style.DEFINIZIONE,
            "Piccolo ma scolpito: ogni muscolo è visibile. Core d'acciaio, agilità e pochissimo grasso.",
            targets(1.02, 0.72, 0.53, 0.68, 0.215, 0.185, 0.36, 0.245, 0.90),
            setOf(Area.ADDOMINALI, Area.VITA, Area.FORMA_V),
            Signature("Manovra tridimensionale", CIRCUITO, setOf(1, 3, 5),
                listOf(
                    sig("hollow_hold", 3, 15, 30, 45, 5, 75),
                    sig("plank_laterale", 3, 20, 35, 50, 5, 75),
                    sig("mountain_climbers", 3, 20, 40, 60, 5, 90),
                )),
        ),
        Character(
            "zoro", "Roronoa Zoro", "One Piece", Category.ANIME, Sex.UOMO, 0xFF00FF9C, 4, Style.IPERTROFIA,
            "Spadaccino potente: spalle larghe, avambracci d'acciaio e addome forte per reggere le tre spade.",
            targets(1.12, 0.80, 0.56, 0.71, 0.255, 0.215, 0.40, 0.26, 0.80),
            setOf(Area.AVAMBRACCI, Area.SPALLE, Area.ADDOMINALI),
            Signature("Tecnica delle tre spade", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("contadino_borse", 3, 30, 45, 60, 5, 90),
                    sig("strizza_asciugamano", 3, 15, 25, 40, 2, 60),
                    sig("plank", 3, 30, 60, 90, 5, 150),
                )),
        ),
        Character(
            "luffy", "Monkey D. Luffy", "One Piece", Category.ANIME, Sex.UOMO, 0xFFFF1744, 3, Style.ATLETICO,
            "Snello ed elastico, con addominali marcati e tanta energia. Fisico da combattente agile.",
            targets(1.00, 0.72, 0.53, 0.68, 0.215, 0.180, 0.36, 0.24, 0.75),
            setOf(Area.ADDOMINALI, Area.VITA, Area.FORMA_V),
            Signature("Gear Second", CIRCUITO, setOf(1, 4),
                listOf(
                    sig("jumping_jacks", 3, 30, 45, 60, 5, 90),
                    sig("burpees", 3, 5, 10, 15, 1, 25),
                    sig("skip_alto", 3, 20, 30, 45, 5, 75),
                )),
        ),
        Character(
            "baki", "Baki Hanma", "Baki", Category.ANIME, Sex.UOMO, 0xFFFF2BD6, 5, Style.IPERTROFIA,
            "Il fisico più estremo: ogni muscolo al massimo. Braccia, spalle e avambracci da lottatore.",
            targets(1.20, 0.85, 0.57, 0.72, 0.290, 0.220, 0.43, 0.28, 0.90),
            setOf(Area.BRACCIA, Area.SPALLE, Area.TORACE, Area.AVAMBRACCI),
            Signature("Allenamento da lottatore", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("piegamenti", 5, 8, 15, 25, 1, 35),
                    sig("squat", 5, 15, 25, 40, 2, 60),
                    sig("strizza_asciugamano", 3, 15, 25, 40, 2, 60),
                )),
            fantasy = true,
        ),
        Character(
            "toji", "Toji Fushiguro", "Jujutsu Kaisen", Category.ANIME, Sex.UOMO, 0xFFB026FF, 4, Style.ATLETICO,
            "Muscoli pieni ma da atleta: forma a V marcata, addominali scolpiti e braccia potenti.",
            targets(1.12, 0.80, 0.55, 0.70, 0.255, 0.200, 0.41, 0.265, 0.85),
            setOf(Area.FORMA_V, Area.ADDOMINALI, Area.BRACCIA),
            Signature("Restrizione celeste", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("burpees", 4, 6, 10, 15, 1, 25),
                    sig("hollow_hold", 3, 15, 30, 45, 5, 75),
                    sig("affondi", 3, 10, 16, 24, 2, 40),
                )),
        ),
        Character(
            "allmight", "All Might", "My Hero Academia", Category.ANIME, Sex.UOMO, 0xFFFFE600, 5, Style.FORZA,
            "Il simbolo della pace: spalle e petto giganteschi. Il 100% è una versione umana del suo fisico.",
            targets(1.22, 0.86, 0.60, 0.74, 0.290, 0.215, 0.43, 0.28, 0.75),
            setOf(Area.SPALLE, Area.TORACE, Area.FORMA_V),
            Signature("Plus Ultra", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("piegamenti", 5, 8, 15, 25, 1, 35),
                    sig("squat", 5, 15, 25, 35, 2, 60),
                    sig("plank", 3, 30, 60, 90, 5, 150),
                )),
            fantasy = true,
        ),
        Character(
            "rocklee", "Rock Lee", "Naruto", Category.ANIME, Sex.UOMO, 0xFF76FF03, 3, Style.RESISTENZA,
            "Gambe d'acciaio e resistenza infinita. Fisico asciutto costruito con ripetizioni e calci.",
            targets(1.00, 0.72, 0.54, 0.69, 0.215, 0.185, 0.40, 0.27, 0.75),
            setOf(Area.GAMBE, Area.POLPACCI, Area.ADDOMINALI),
            Signature("Fiamme della giovinezza", "Calci, skip e squat: se non riesci a finire, fai 50 squat in più! (Scherzo: fermati se senti dolore.)",
                setOf(0, 2, 4),
                listOf(
                    sig("calci_frontali", 2, 30, 50, 100, 5, 150),
                    sig("skip_alto", 3, 30, 45, 60, 5, 90),
                    sig("squat", 3, 20, 35, 50, 3, 80),
                )),
        ),
        Character(
            "ippo", "Ippo Makunouchi", "Hajime no Ippo", Category.ANIME, Sex.UOMO, 0xFFFF6E40, 3, Style.ATLETICO,
            "Pugile: spalle tonde, gambe potenti per il Dempsey Roll e addome che incassa i colpi.",
            targets(1.10, 0.79, 0.56, 0.71, 0.250, 0.210, 0.41, 0.27, 0.75),
            setOf(Area.SPALLE, Area.GAMBE, Area.ADDOMINALI),
            Signature("Dempsey Roll", "Come in palestra di boxe: round da tre minuti con un minuto di pausa.",
                setOf(1, 3, 5),
                listOf(
                    sig("shadow_boxing", 3, 60, 120, 180, 10, 180),
                    sig("salti_corda", 3, 45, 60, 90, 10, 180),
                    sig("crunch", 3, 15, 25, 40, 2, 60),
                )),
        ),
        Character(
            "yuji", "Yuji Itadori", "Jujutsu Kaisen", Category.ANIME, Sex.UOMO, 0xFFFF4081, 3, Style.ATLETICO,
            "Atleta naturale: gambe esplosive, busto a V e addominali definiti.",
            targets(1.08, 0.77, 0.55, 0.70, 0.240, 0.195, 0.41, 0.265, 0.80),
            setOf(Area.GAMBE, Area.FORMA_V, Area.ADDOMINALI),
            Signature("Divergent Fist", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("jump_squat", 4, 8, 12, 20, 1, 30),
                    sig("piegamenti", 4, 6, 12, 20, 1, 30),
                    sig("shadow_boxing", 3, 45, 60, 90, 10, 150),
                )),
        ),
        Character(
            "mikasa", "Mikasa Ackerman", "L'attacco dei giganti", Category.ANIME, Sex.DONNA, 0xFFFF1744, 4, Style.ATLETICO,
            "Atletica e forte: addominali visibili, spalle toniche e gambe da soldato.",
            targets(0.94, 0.68, 0.50, 0.76, 0.205, 0.165, 0.40, 0.25, 0.70),
            setOf(Area.ADDOMINALI, Area.SPALLE, Area.FORMA_V),
            Signature("Forza Ackerman", CIRCUITO, setOf(1, 4),
                listOf(
                    sig("mountain_climbers", 3, 20, 40, 60, 5, 90),
                    sig("plank", 3, 30, 50, 75, 5, 120),
                    sig("affondi", 3, 10, 16, 24, 2, 40),
                )),
        ),
        Character(
            "maki", "Maki Zenin", "Jujutsu Kaisen", Category.ANIME, Sex.DONNA, 0xFF00E676, 4, Style.ATLETICO,
            "Combattente muscolosa: braccia forti, gambe potenti e core solidissimo.",
            targets(0.96, 0.69, 0.50, 0.76, 0.215, 0.170, 0.41, 0.255, 0.75),
            setOf(Area.BRACCIA, Area.GAMBE, Area.ADDOMINALI),
            Signature("Arti marziali", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("jump_squat", 3, 8, 12, 18, 1, 30),
                    sig("piegamenti", 3, 5, 10, 15, 1, 25),
                    sig("plank_laterale", 3, 20, 35, 50, 5, 75),
                )),
        ),
        Character(
            "erza", "Erza Scarlet", "Fairy Tail", Category.ANIME, Sex.DONNA, 0xFFFF2BD6, 3, Style.ATLETICO,
            "Guerriera: gambe forti, addome tonico e spalle che reggono l'armatura.",
            targets(0.93, 0.67, 0.50, 0.77, 0.200, 0.160, 0.40, 0.25, 0.65),
            setOf(Area.GAMBE, Area.ADDOMINALI, Area.SPALLE),
            Signature("Cambio d'armatura", CIRCUITO, setOf(1, 4),
                listOf(
                    sig("squat", 3, 15, 25, 40, 2, 60),
                    sig("affondi", 3, 10, 16, 24, 2, 40),
                    sig("hollow_hold", 3, 15, 25, 40, 5, 75),
                )),
        ),

        // ---------------- FAMOSI ----------------
        Character(
            "bradpitt", "Brad Pitt", "Fight Club (1999)", Category.FAMOSO, Sex.UOMO, 0xFF18FFFF, 3, Style.DEFINIZIONE,
            "Il fisico asciutto più famoso del cinema: poco grasso, addominali scolpiti, muscoli non enormi ma definiti.",
            targets(1.02, 0.72, 0.53, 0.68, 0.225, 0.185, 0.36, 0.24, 0.95),
            setOf(Area.ADDOMINALI, Area.VITA, Area.FORMA_V),
            Signature("Regole del Fight Club", CIRCUITO, setOf(1, 3, 5),
                listOf(
                    sig("crunch", 3, 15, 25, 40, 2, 60),
                    sig("piegamenti", 3, 8, 15, 25, 1, 35),
                    sig("mountain_climbers", 3, 20, 40, 60, 5, 90),
                )),
        ),
        Character(
            "hemsworth", "Chris Hemsworth", "Thor", Category.FAMOSO, Sex.UOMO, 0xFF2979FF, 5, Style.IPERTROFIA,
            "Un dio nordico: spalle e braccia enormi, petto pieno, ma comunque atletico.",
            targets(1.18, 0.84, 0.58, 0.72, 0.280, 0.210, 0.42, 0.27, 0.75),
            setOf(Area.SPALLE, Area.BRACCIA, Area.TORACE),
            Signature("Dio del tuono", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("burpees", 4, 6, 10, 15, 1, 25),
                    sig("piegamenti", 4, 8, 15, 25, 1, 35),
                    sig("affondi", 4, 10, 16, 24, 2, 40),
                )),
        ),
        Character(
            "brucelee", "Bruce Lee", "Arti marziali", Category.FAMOSO, Sex.UOMO, 0xFFFFEA00, 4, Style.ATLETICO,
            "Leggero e incredibilmente definito: dorsali larghi, avambracci d'acciaio e addominali leggendari.",
            targets(1.00, 0.75, 0.52, 0.67, 0.215, 0.200, 0.36, 0.245, 0.95),
            setOf(Area.ADDOMINALI, Area.AVAMBRACCI, Area.FORMA_V),
            Signature("Il drago", "Velocità e controllo: pugni rapidi, addome e presa.", setOf(1, 3, 5),
                listOf(
                    sig("shadow_boxing", 3, 45, 60, 90, 10, 150),
                    sig("leg_raise", 3, 8, 12, 20, 1, 30),
                    sig("strizza_asciugamano", 3, 15, 25, 40, 2, 60),
                )),
        ),
        Character(
            "ronaldo", "Cristiano Ronaldo", "Calcio", Category.FAMOSO, Sex.UOMO, 0xFF39FF14, 4, Style.ATLETICO,
            "Atleta completo: gambe esplosive, addominali sempre visibili, fisico pulito e potente.",
            targets(1.06, 0.76, 0.54, 0.69, 0.235, 0.190, 0.42, 0.27, 0.90),
            setOf(Area.GAMBE, Area.ADDOMINALI, Area.POLPACCI),
            Signature("CR7", CIRCUITO, setOf(1, 4),
                listOf(
                    sig("jump_squat", 4, 8, 12, 20, 1, 30),
                    sig("skip_alto", 4, 20, 30, 45, 5, 75),
                    sig("crunch", 3, 20, 30, 50, 2, 70),
                )),
        ),
        Character(
            "jackman", "Hugh Jackman", "Wolverine", Category.FAMOSO, Sex.UOMO, 0xFFFF9100, 5, Style.IPERTROFIA,
            "Muscoloso e tirato: spalle larghe, braccia grosse e vene in vista. Molto impegnativo.",
            targets(1.14, 0.82, 0.55, 0.70, 0.270, 0.205, 0.40, 0.26, 0.90),
            setOf(Area.SPALLE, Area.BRACCIA, Area.ADDOMINALI, Area.FORMA_V),
            Signature("Artigli", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("piegamenti", 4, 8, 15, 25, 1, 35),
                    sig("rematore_zaino", 4, 10, 15, 20, 1, 30),
                    sig("plank", 3, 30, 60, 90, 5, 150),
                )),
        ),
        Character(
            "cavill", "Henry Cavill", "Superman", Category.FAMOSO, Sex.UOMO, 0xFF2962FF, 5, Style.IPERTROFIA,
            "Il supereroe classico: petto ampio, spalle larghe e forma a V da fumetto.",
            targets(1.18, 0.86, 0.58, 0.72, 0.275, 0.205, 0.42, 0.27, 0.75),
            setOf(Area.TORACE, Area.SPALLE, Area.FORMA_V),
            Signature("Uomo d'acciaio", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("burpees", 4, 6, 10, 15, 1, 25),
                    sig("squat", 4, 15, 25, 40, 2, 60),
                    sig("piegamenti", 4, 8, 15, 25, 1, 35),
                )),
        ),
        Character(
            "efron", "Zac Efron", "Baywatch", Category.FAMOSO, Sex.UOMO, 0xFFFF2BD6, 4, Style.DEFINIZIONE,
            "Definizione estrema: addominali scolpiti, braccia piene e grasso al minimo.",
            targets(1.10, 0.80, 0.53, 0.69, 0.255, 0.195, 0.39, 0.255, 1.00),
            setOf(Area.ADDOMINALI, Area.VITA, Area.BRACCIA),
            Signature("Baywatch", CIRCUITO, setOf(1, 3, 5),
                listOf(
                    sig("burpees", 3, 6, 10, 15, 1, 25),
                    sig("mountain_climbers", 3, 20, 40, 60, 5, 90),
                    sig("crunch", 3, 15, 25, 40, 2, 60),
                )),
        ),
        Character(
            "stallone", "Sylvester Stallone", "Rocky", Category.FAMOSO, Sex.UOMO, 0xFFFF6E40, 4, Style.ATLETICO,
            "Pugile da film: spalle e braccia forti, addome duro e tanta corsa (scalinata compresa).",
            targets(1.12, 0.80, 0.56, 0.71, 0.265, 0.210, 0.40, 0.26, 0.85),
            setOf(Area.SPALLE, Area.BRACCIA, Area.ADDOMINALI),
            Signature("Gonna Fly Now", "Corsa al mattino, poi addominali e piegamenti.", setOf(0, 3),
                listOf(
                    sig("corsa_km", 1, 2, 3, 5, 1, 8),
                    sig("sit_up", 3, 15, 25, 40, 2, 60),
                    sig("piegamenti", 3, 8, 15, 25, 1, 35),
                )),
        ),
        Character(
            "dwayne", "Dwayne Johnson", "The Rock", Category.FAMOSO, Sex.UOMO, 0xFFD500F9, 5, Style.FORZA,
            "Massa enorme: spalle, petto e braccia gigantesche. Il più difficile della lista.",
            targets(1.22, 0.88, 0.62, 0.74, 0.300, 0.220, 0.43, 0.28, 0.70),
            setOf(Area.SPALLE, Area.TORACE, Area.BRACCIA),
            Signature("Iron Paradise", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("squat", 4, 15, 25, 40, 2, 60),
                    sig("piegamenti", 4, 8, 15, 25, 1, 35),
                    sig("rematore_zaino", 4, 10, 15, 20, 1, 30),
                )),
        ),
        Character(
            "gadot", "Gal Gadot", "Wonder Woman", Category.FAMOSO, Sex.DONNA, 0xFFFFD600, 3, Style.ATLETICO,
            "Fisico da amazzone: gambe forti, spalle toniche e addome solido.",
            targets(0.92, 0.66, 0.50, 0.77, 0.200, 0.160, 0.39, 0.245, 0.60),
            setOf(Area.GAMBE, Area.SPALLE, Area.ADDOMINALI),
            Signature("Amazzone", CIRCUITO, setOf(1, 4),
                listOf(
                    sig("affondi", 3, 10, 16, 24, 2, 40),
                    sig("plank", 3, 30, 50, 75, 5, 120),
                    sig("piegamenti_ginocchia", 3, 8, 15, 25, 1, 35),
                )),
        ),
        Character(
            "johansson", "Scarlett Johansson", "Vedova Nera", Category.FAMOSO, Sex.DONNA, 0xFFFF1744, 3, Style.ATLETICO,
            "Agile e tonica: gambe e glutei forti, addome definito, pronta all'azione.",
            targets(0.90, 0.65, 0.50, 0.77, 0.195, 0.160, 0.40, 0.245, 0.60),
            setOf(Area.GAMBE, Area.ADDOMINALI, Area.VITA),
            Signature("Vedova Nera", CIRCUITO, setOf(2, 5),
                listOf(
                    sig("mountain_climbers", 3, 20, 40, 60, 5, 90),
                    sig("jump_squat", 3, 8, 12, 18, 1, 30),
                    sig("plank_laterale", 3, 20, 35, 50, 5, 75),
                )),
        ),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Character? = byId[id]
}
