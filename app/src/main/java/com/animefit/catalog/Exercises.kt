package com.animefit.catalog

import com.animefit.data.Equipment

enum class Muscle(val label: String) {
    PETTO("Petto"),
    SPALLE("Spalle"),
    DORSO("Dorso"),
    BICIPITI("Bicipiti"),
    TRICIPITI("Tricipiti"),
    AVAMBRACCI("Avambracci"),
    CORE("Addominali"),
    GAMBE("Gambe"),
    POLPACCI("Polpacci"),
    CARDIO("Cardio"),
    MOBILITA("Mobilità"),
}

/** Cosa serve per fare l'esercizio. */
enum class Kit { CORPO_LIBERO, SBARRA, PALESTRA }

enum class Unit(val short: String) { REPS("rip."), SECONDI("sec"), MINUTI("min"), KM("km") }

data class Exercise(
    val id: String,
    val name: String,
    val muscle: Muscle,
    val kit: Kit,
    /** 1 = facile, 3 = difficile. */
    val difficulty: Int,
    val unit: Unit,
    /** Moltiplicatore delle ripetizioni (o dei secondi) rispetto allo schema base. */
    val scale: Double,
    val tip: String,
)

fun Equipment.allows(kit: Kit): Boolean = when (this) {
    Equipment.CASA -> kit == Kit.CORPO_LIBERO
    Equipment.CASA_SBARRA -> kit != Kit.PALESTRA
    Equipment.PALESTRA -> true
}

object Exercises {
    private fun ex(
        id: String, name: String, muscle: Muscle, kit: Kit, difficulty: Int,
        unit: Unit, scale: Double, tip: String,
    ) = Exercise(id, name, muscle, kit, difficulty, unit, scale, tip)

    private val CL = Kit.CORPO_LIBERO
    private val SB = Kit.SBARRA
    private val PA = Kit.PALESTRA
    private val R = Unit.REPS
    private val S = Unit.SECONDI
    private val M = Unit.MINUTI

    val all: List<Exercise> = listOf(
        // PETTO
        ex("piegamenti_ginocchia", "Piegamenti sulle ginocchia", Muscle.PETTO, CL, 1, R, 1.0,
            "Mani poco più larghe delle spalle, corpo dritto dalle ginocchia alla testa. Scendi col petto quasi a terra e spingi."),
        ex("piegamenti", "Piegamenti classici", Muscle.PETTO, CL, 1, R, 0.7,
            "Corpo rigido come una tavola, gomiti a 45° dal busto. Petto a un pugno da terra, poi su fino a braccia tese."),
        ex("piegamenti_larghi", "Piegamenti a mani larghe", Muscle.PETTO, CL, 2, R, 0.8,
            "Mani molto più larghe delle spalle: lavora di più il petto. Scendi lento (2 secondi)."),
        ex("piegamenti_declinati", "Piegamenti con piedi rialzati", Muscle.PETTO, CL, 2, R, 0.7,
            "Piedi su una sedia o sul letto. Allena la parte alta del petto e le spalle."),
        ex("piegamenti_esplosivi", "Piegamenti esplosivi", Muscle.PETTO, CL, 3, R, 0.5,
            "Spingi così forte che le mani si staccano da terra. Atterra morbido e riparti."),
        ex("piegamenti_arciere", "Piegamenti arciere (per lato)", Muscle.PETTO, CL, 3, R, 0.4,
            "Mani larghissime: scendi verso un braccio tenendo l'altro quasi teso. Alterna i lati."),
        ex("dip_parallele", "Dip alle parallele", Muscle.PETTO, PA, 2, R, 0.6,
            "Busto leggermente in avanti per il petto. Scendi finché le spalle sono all'altezza dei gomiti."),
        ex("panca_piana", "Panca piana con bilanciere", Muscle.PETTO, PA, 1, R, 1.0,
            "Scapole strette, piedi a terra. Bilanciere al petto basso, poi spingi. Usa un peso che ti lasci 1-2 ripetizioni di riserva."),
        ex("panca_inclinata", "Panca inclinata con manubri", Muscle.PETTO, PA, 1, R, 1.0,
            "Panca a 30°. Manubri ai lati del petto, spingi verso l'alto avvicinandoli."),
        ex("croci_cavi", "Croci ai cavi", Muscle.PETTO, PA, 1, R, 1.2,
            "Gomiti leggermente piegati, chiudi le braccia davanti al petto come in un abbraccio."),

        // SPALLE
        ex("alzate_laterali_bottiglie", "Alzate laterali con bottiglie d'acqua", Muscle.SPALLE, CL, 1, R, 1.3,
            "Una bottiglia piena per mano. Alza le braccia di lato fino alle spalle, gomiti morbidi. Scendi lento."),
        ex("spinte_zaino", "Spinte sopra la testa con zaino", Muscle.SPALLE, CL, 1, R, 1.0,
            "Zaino carico di libri tenuto per i lati all'altezza del petto: spingilo sopra la testa a braccia tese."),
        ex("plank_tocco_spalle", "Plank con tocco spalle", Muscle.SPALLE, CL, 1, R, 1.4,
            "In posizione di piegamento, tocca la spalla opposta alternando le mani senza ruotare il bacino."),
        ex("pike", "Piegamenti pike (a V rovesciata)", Muscle.SPALLE, CL, 2, R, 0.6,
            "Sedere in alto, corpo a V capovolta. Porta la testa verso terra tra le mani e spingi."),
        ex("pike_rialzati", "Pike con piedi rialzati", Muscle.SPALLE, CL, 3, R, 0.5,
            "Piedi su una sedia, busto quasi verticale. È il passo prima dei piegamenti in verticale."),
        ex("verticale_muro", "Verticale al muro (tenuta)", Muscle.SPALLE, CL, 3, S, 0.6,
            "Pancia verso il muro, braccia tese, corpo dritto. Spingi il pavimento lontano."),
        ex("lento_manubri", "Lento avanti con manubri", Muscle.SPALLE, PA, 1, R, 1.0,
            "Seduto con schienale. Manubri all'altezza delle orecchie, spingi sopra la testa."),
        ex("alzate_laterali", "Alzate laterali con manubri", Muscle.SPALLE, PA, 1, R, 1.3,
            "Peso leggero, movimento controllato. Il deltoide laterale è ciò che allarga le spalle."),
        ex("face_pull", "Face pull ai cavi", Muscle.SPALLE, PA, 1, R, 1.3,
            "Corda all'altezza del viso, tira verso la fronte aprendo i gomiti. Ottimo per la postura."),
        ex("military_press", "Military press con bilanciere", Muscle.SPALLE, PA, 2, R, 0.9,
            "In piedi, glutei e addome contratti. Bilanciere dal mento a sopra la testa."),

        // DORSO
        ex("superman", "Superman a terra", Muscle.DORSO, CL, 1, R, 1.4,
            "A pancia in giù, solleva insieme braccia e gambe. Tieni 2 secondi in alto."),
        ex("rematore_zaino", "Rematore con zaino", Muscle.DORSO, CL, 1, R, 1.0,
            "Busto inclinato in avanti, schiena dritta. Tira lo zaino verso l'ombelico stringendo le scapole."),
        ex("rematore_inverso", "Rematore inverso sotto un tavolo", Muscle.DORSO, CL, 2, R, 0.8,
            "Sdraiati sotto un tavolo robusto, afferra il bordo e tira il petto verso il tavolo. Corpo dritto."),
        ex("trazioni_negative", "Trazioni negative (discesa lenta)", Muscle.DORSO, SB, 1, R, 0.4,
            "Parti col mento sopra la sbarra (aiutati con una sedia) e scendi in 4-5 secondi."),
        ex("trazioni", "Trazioni alla sbarra", Muscle.DORSO, SB, 2, R, 0.45,
            "Presa poco più larga delle spalle. Tira il petto verso la sbarra, scendi fino a braccia tese."),
        ex("trazioni_larghe", "Trazioni a presa larga", Muscle.DORSO, SB, 3, R, 0.4,
            "Presa larga: è l'esercizio principe per i dorsali larghi e la forma a V."),
        ex("lat_machine", "Lat machine", Muscle.DORSO, PA, 1, R, 1.0,
            "Tira la barra al petto alto portando i gomiti verso i fianchi. Petto in fuori."),
        ex("rematore_bilanciere", "Rematore con bilanciere", Muscle.DORSO, PA, 2, R, 1.0,
            "Busto a 45°, schiena neutra. Tira il bilanciere all'ombelico."),
        ex("pulley", "Pulley basso", Muscle.DORSO, PA, 1, R, 1.0,
            "Seduto, schiena dritta. Tira il triangolo all'addome stringendo le scapole."),
        ex("rematore_manubrio", "Rematore con manubrio (per lato)", Muscle.DORSO, PA, 1, R, 1.0,
            "Mano e ginocchio sulla panca, tira il manubrio verso l'anca."),

        // BICIPITI
        ex("curl_zaino", "Curl con zaino", Muscle.BICIPITI, CL, 1, R, 1.0,
            "Tieni lo zaino per le maniglie, gomiti fermi ai fianchi. Piega le braccia fino al petto."),
        ex("curl_martello_bottiglie", "Curl a martello con bottiglie", Muscle.BICIPITI, CL, 1, R, 1.2,
            "Bottiglie o taniche d'acqua, palmi che si guardano. Lavora bicipiti e avambracci."),
        ex("rematore_inverso_supino", "Rematore inverso a presa inversa", Muscle.BICIPITI, CL, 2, R, 0.8,
            "Come il rematore sotto il tavolo ma con i palmi verso di te: più lavoro sui bicipiti."),
        ex("chin_up", "Trazioni a presa inversa (chin-up)", Muscle.BICIPITI, SB, 2, R, 0.5,
            "Palmi verso di te, mani alla larghezza delle spalle. Mento sopra la sbarra."),
        ex("curl_bilanciere", "Curl con bilanciere", Muscle.BICIPITI, PA, 1, R, 1.0,
            "Gomiti fermi, niente slanci con la schiena. Scendi controllato."),
        ex("curl_martello", "Curl a martello con manubri", Muscle.BICIPITI, PA, 1, R, 1.0,
            "Palmi neutri. Ingrossa il braccio e l'avambraccio."),
        ex("curl_concentrato", "Curl concentrato (per lato)", Muscle.BICIPITI, PA, 1, R, 1.0,
            "Seduto, gomito appoggiato all'interno coscia. Stringi forte in alto."),

        // TRICIPITI
        ex("dip_sedia", "Dip sulla sedia", Muscle.TRICIPITI, CL, 1, R, 0.9,
            "Mani sul bordo di una sedia stabile dietro di te, scendi piegando i gomiti all'indietro."),
        ex("estensioni_zaino", "Estensioni sopra la testa con zaino", Muscle.TRICIPITI, CL, 1, R, 1.0,
            "Zaino dietro la testa, gomiti puntati in alto. Distendi le braccia."),
        ex("piegamenti_diamante", "Piegamenti diamante", Muscle.TRICIPITI, CL, 2, R, 0.6,
            "Pollici e indici uniti a formare un rombo sotto il petto. Gomiti stretti."),
        ex("pushdown", "Pushdown ai cavi", Muscle.TRICIPITI, PA, 1, R, 1.1,
            "Gomiti incollati ai fianchi, spingi la corda verso il basso aprendola alla fine."),
        ex("french_press", "French press con bilanciere EZ", Muscle.TRICIPITI, PA, 2, R, 1.0,
            "Sdraiato, porta il bilanciere verso la fronte piegando solo i gomiti."),
        ex("panca_stretta", "Panca presa stretta", Muscle.TRICIPITI, PA, 2, R, 0.9,
            "Mani alla larghezza delle spalle, gomiti vicini al busto."),

        // AVAMBRACCI
        ex("strizza_asciugamano", "Strizza l'asciugamano", Muscle.AVAMBRACCI, CL, 1, R, 1.0,
            "Asciugamano bagnato o arrotolato: torcilo forte in un verso e poi nell'altro (1 torsione = 1 ripetizione)."),
        ex("flessioni_polso", "Flessioni del polso con bottiglia", Muscle.AVAMBRACCI, CL, 1, R, 1.6,
            "Avambraccio appoggiato sulla coscia, solo il polso si muove su e giù."),
        ex("contadino_borse", "Camminata del contadino con borse pesanti", Muscle.AVAMBRACCI, CL, 1, S, 1.0,
            "Due borse o taniche pesanti, cammina a passi corti tenendo le spalle basse."),
        ex("piegamenti_dita", "Piegamenti sulla punta delle dita", Muscle.AVAMBRACCI, CL, 3, R, 0.3,
            "Come i piegamenti ma sulle dita aperte. Inizia sulle ginocchia se serve."),
        ex("dead_hang", "Appeso alla sbarra (dead hang)", Muscle.AVAMBRACCI, SB, 1, S, 0.8,
            "Resta appeso a braccia tese stringendo forte la sbarra."),
        ex("farmer_walk", "Farmer walk con manubri", Muscle.AVAMBRACCI, PA, 1, S, 1.0,
            "Manubri pesanti, cammina dritto tenendo la presa salda."),
        ex("wrist_curl", "Wrist curl con bilanciere", Muscle.AVAMBRACCI, PA, 1, R, 1.5,
            "Avambracci sulla panca, palmi in su: solo i polsi lavorano."),
        ex("curl_presa_inversa", "Curl a presa inversa", Muscle.AVAMBRACCI, PA, 2, R, 1.0,
            "Palmi verso il basso: allena l'avambraccio esterno."),

        // CORE
        ex("plank", "Plank", Muscle.CORE, CL, 1, S, 1.0,
            "Avambracci a terra, corpo dritto, glutei e addome contratti. Respira."),
        ex("crunch", "Crunch", Muscle.CORE, CL, 1, R, 1.5,
            "Solleva solo le spalle da terra, schiacciando la zona lombare a terra."),
        ex("sit_up", "Sit-up", Muscle.CORE, CL, 1, R, 1.2,
            "Piedi bloccati, sali fino a seduto e torna giù controllando."),
        ex("bicycle", "Bicycle crunch", Muscle.CORE, CL, 1, R, 1.6,
            "Gomito verso il ginocchio opposto, pedalando in aria. Lento e controllato."),
        ex("plank_laterale", "Plank laterale (per lato)", Muscle.CORE, CL, 1, S, 0.8,
            "Su un avambraccio, bacino alto e corpo in linea. Allena gli obliqui."),
        ex("russian_twist", "Russian twist", Muscle.CORE, CL, 1, R, 1.6,
            "Seduto, busto inclinato indietro, ruota toccando terra a destra e a sinistra."),
        ex("mountain_climbers", "Mountain climbers", Muscle.CORE, CL, 1, S, 0.9,
            "In posizione di piegamento, porta le ginocchia al petto alternando veloce."),
        ex("leg_raise", "Leg raise da sdraiato", Muscle.CORE, CL, 2, R, 1.0,
            "Gambe tese, sollevale fino a 90° e scendi senza toccare terra."),
        ex("hollow_hold", "Hollow hold", Muscle.CORE, CL, 2, S, 0.8,
            "Sdraiato, schiena bassa incollata a terra, braccia e gambe sollevate a 'banana'."),
        ex("v_up", "V-up", Muscle.CORE, CL, 2, R, 0.9,
            "Sali con busto e gambe insieme, tocca i piedi con le mani."),
        ex("dragon_flag", "Dragon flag (negativi)", Muscle.CORE, CL, 3, R, 0.3,
            "Sdraiato su una panca, aggrappato dietro la testa: scendi lentissimo col corpo rigido."),
        ex("ginocchia_sbarra", "Ginocchia al petto alla sbarra", Muscle.CORE, SB, 2, R, 0.8,
            "Appeso alla sbarra, porta le ginocchia al petto senza dondolare."),
        ex("leg_raise_sbarra", "Leg raise alla sbarra", Muscle.CORE, SB, 3, R, 0.6,
            "Appeso, solleva le gambe tese fino all'altezza dei fianchi o più su."),
        ex("ab_wheel", "Ruota per addominali", Muscle.CORE, PA, 2, R, 0.7,
            "In ginocchio, rotola in avanti con la schiena neutra e torna indietro con l'addome."),
        ex("crunch_cavi", "Crunch ai cavi", Muscle.CORE, PA, 1, R, 1.2,
            "In ginocchio, corda dietro la testa: arrotola il busto verso il basso."),

        // GAMBE
        ex("squat", "Squat a corpo libero", Muscle.GAMBE, CL, 1, R, 1.5,
            "Piedi alla larghezza delle spalle, scendi come per sederti, talloni a terra, petto alto."),
        ex("affondi", "Affondi alternati", Muscle.GAMBE, CL, 1, R, 1.0,
            "Passo lungo in avanti, ginocchio dietro quasi a terra. Alterna le gambe."),
        ex("ponte_glutei", "Ponte glutei", Muscle.GAMBE, CL, 1, R, 1.5,
            "Sdraiato, piedi vicini al sedere, spingi il bacino in alto stringendo i glutei."),
        ex("sedia_muro", "Sedia al muro", Muscle.GAMBE, CL, 1, S, 1.0,
            "Schiena al muro, cosce parallele al pavimento come seduto su una sedia invisibile."),
        ex("squat_bulgaro", "Squat bulgaro (per gamba)", Muscle.GAMBE, CL, 2, R, 0.8,
            "Piede dietro su una sedia, scendi sulla gamba davanti. Brucia, ed è normale."),
        ex("jump_squat", "Squat con salto", Muscle.GAMBE, CL, 2, R, 1.0,
            "Squat e poi salto esplosivo verso l'alto. Atterra morbido."),
        ex("affondi_saltati", "Affondi saltati", Muscle.GAMBE, CL, 3, R, 0.8,
            "Dall'affondo, salta e cambia gamba in aria."),
        ex("pistol", "Pistol squat assistito (per gamba)", Muscle.GAMBE, CL, 3, R, 0.4,
            "Su una gamba sola, tenendoti a una porta. L'altra gamba tesa in avanti."),
        ex("squat_bilanciere", "Squat con bilanciere", Muscle.GAMBE, PA, 2, R, 1.0,
            "Bilanciere sulle spalle, scendi almeno a cosce parallele. Peso con tecnica perfetta."),
        ex("leg_press", "Leg press", Muscle.GAMBE, PA, 1, R, 1.0,
            "Piedi al centro della pedana, scendi a 90° senza staccare il bacino."),
        ex("stacco_rumeno", "Stacco rumeno", Muscle.GAMBE, PA, 2, R, 1.0,
            "Ginocchia morbide, spingi il sedere indietro facendo scendere il bilanciere lungo le gambe."),
        ex("leg_extension", "Leg extension", Muscle.GAMBE, PA, 1, R, 1.2,
            "Distendi le gambe e tieni un secondo in alto."),
        ex("leg_curl", "Leg curl", Muscle.GAMBE, PA, 1, R, 1.2,
            "Porta i talloni verso i glutei controllando la discesa."),
        ex("affondi_manubri", "Affondi con manubri", Muscle.GAMBE, PA, 1, R, 1.0,
            "Un manubrio per mano, passo lungo, busto dritto."),

        // POLPACCI
        ex("calf_raise", "Sollevamenti sulle punte", Muscle.POLPACCI, CL, 1, R, 2.0,
            "Sali sulle punte più in alto possibile, tieni 1 secondo, scendi lento."),
        ex("calf_gradino", "Sollevamenti su un piede sul gradino (per lato)", Muscle.POLPACCI, CL, 2, R, 1.2,
            "Avampiede sul bordo di un gradino, tallone che scende sotto il livello e poi su."),
        ex("salti_corda", "Salti con la corda (anche simulati)", Muscle.POLPACCI, CL, 1, S, 1.0,
            "Saltelli rapidi sugli avampiedi. Senza corda va bene uguale."),
        ex("calf_macchina", "Calf raise alla macchina", Muscle.POLPACCI, PA, 1, R, 1.5,
            "Escursione completa: tallone giù in fondo e poi spingi sulle punte."),
        ex("calf_seduto", "Calf raise da seduto", Muscle.POLPACCI, PA, 1, R, 1.5,
            "Allena il soleo, che dà spessore al polpaccio."),

        // CARDIO
        ex("camminata_veloce", "Camminata veloce", Muscle.CARDIO, CL, 1, M, 1.2,
            "Passo svelto, devi riuscire a parlare ma con un po' di fiatone."),
        ex("corsa", "Corsa leggera", Muscle.CARDIO, CL, 1, M, 1.0,
            "Ritmo con cui riesci ancora a parlare. Meglio costante che veloce."),
        ex("jumping_jacks", "Jumping jacks", Muscle.CARDIO, CL, 1, S, 1.0,
            "Salta aprendo braccia e gambe e torna chiuso. Ritmo costante."),
        ex("skip_alto", "Skip alto (ginocchia al petto)", Muscle.CARDIO, CL, 1, S, 0.8,
            "Corsa sul posto portando le ginocchia all'altezza dei fianchi."),
        ex("shadow_boxing", "Shadow boxing", Muscle.CARDIO, CL, 1, S, 1.5,
            "Combinazioni di pugni nel vuoto, guardia alta, piedi sempre in movimento."),
        ex("burpees", "Burpees", Muscle.CARDIO, CL, 2, R, 0.7,
            "Giù in piegamento, su in piedi e salto con le mani in alto. Senza fermarti."),
        ex("sprint", "Sprint a intervalli (20\" forte / 40\" piano)", Muscle.CARDIO, CL, 3, M, 0.6,
            "Scatto massimo per 20 secondi, poi 40 secondi di corsa lenta. Ripeti."),
        ex("cyclette", "Cyclette a intervalli", Muscle.CARDIO, PA, 1, M, 1.0,
            "1 minuto forte e 1 minuto piano, alternati."),
        ex("tapis_salita", "Tapis roulant in salita", Muscle.CARDIO, PA, 1, M, 1.0,
            "Pendenza 8-12%, camminata veloce senza tenerti alle maniglie."),
        ex("vogatore", "Vogatore", Muscle.CARDIO, PA, 2, M, 0.8,
            "Spingi prima con le gambe, poi tira con le braccia. Ritmo costante."),

        // MOBILITÀ (giorni di recupero attivo)
        ex("stretching", "Stretching completo", Muscle.MOBILITA, CL, 1, M, 1.0,
            "Allunga petto, spalle, schiena, cosce e polpacci: 30 secondi per posizione, senza molleggiare."),
        ex("mobilita", "Mobilità spalle e anche", Muscle.MOBILITA, CL, 1, M, 0.8,
            "Cerchi con le braccia, rotazioni del busto, affondi lenti con apertura delle anche."),
        ex("saluto_sole", "Saluto al sole (yoga)", Muscle.MOBILITA, CL, 1, M, 0.8,
            "Sequenza lenta di allungamenti sincronizzata col respiro."),

        // Esercizi speciali dei personaggi
        ex("corsa_km", "Corsa", Muscle.CARDIO, CL, 1, Unit.KM, 1.0,
            "Anche divisa in più uscite durante il giorno. Ascolta il corpo: se fa male, rallenta."),
        ex("calci_frontali", "Calci frontali alternati", Muscle.GAMBE, CL, 1, R, 1.0,
            "Ginocchio su e calcio in avanti, controllato, alternando le gambe. Guardia alta."),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): Exercise = byId[id] ?: error("Esercizio sconosciuto: $id")
}
