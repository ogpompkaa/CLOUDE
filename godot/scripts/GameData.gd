extends Node
# Static game data. Ported 1:1 from the web version (index.html) to keep
# balance identical between the browser and native builds.

const TITLES = [
	{"id": "tactical", "name": "Tactical Ops", "genre": "Taktyczna strzelanka 5v5", "icon": "crosshair", "accent": Color("#ff3d68"), "entry_cost": 2200},
	{"id": "rift", "name": "Rift Legends", "genre": "MOBA 5v5", "icon": "shield", "accent": Color("#8c6bff"), "entry_cost": 3000},
	{"id": "battlezone", "name": "Battle Zone", "genre": "Battle royale", "icon": "pin", "accent": Color("#29ffb0"), "entry_cost": 2600},
	{"id": "velocity", "name": "Velocity GP", "genre": "Wyścigi symulacyjne", "icon": "flag", "accent": Color("#21e6ff"), "entry_cost": 3600},
	{"id": "ironfist", "name": "Iron Fist", "genre": "Bijatyka 1v1", "icon": "impact", "accent": Color("#ff3d68"), "entry_cost": 1900},
	{"id": "cardmasters", "name": "Card Masters", "genre": "Karciana strategiczna", "icon": "cards", "accent": Color("#ffc857"), "entry_cost": 1500},
]

const DIVISION_TIERS = [
	{"name": "Amatorska", "prize_base": 900, "req_skill": 0, "req_rep": 0},
	{"name": "Tier 3", "prize_base": 1400, "req_skill": 25, "req_rep": 10},
	{"name": "Tier 2", "prize_base": 2100, "req_skill": 40, "req_rep": 25},
	{"name": "Tier 1", "prize_base": 3200, "req_skill": 55, "req_rep": 40},
	{"name": "Top Kontender", "prize_base": 4800, "req_skill": 70, "req_rep": 55},
	{"name": "Legenda", "prize_base": 7200, "req_skill": 85, "req_rep": 70},
]

const HQ_UPGRADES = [
	{"id": "facility", "name": "Dom Gamingowy", "desc": "+30% skuteczności treningu we wszystkich oddziałach", "cost": 3200, "upkeep": 0},
	{"id": "analysts", "name": "Zespół Analityków", "desc": "+50% wzrostu umiejętności z wygranych meczów", "cost": 2600, "upkeep": 100},
	{"id": "medical", "name": "Sztab Medyczny", "desc": "Szybsza regeneracja morale zawodników", "cost": 2000, "upkeep": 70},
	{"id": "pr", "name": "Zespół PR", "desc": "+30% reputacji ze zwycięstw i sponsorów", "cost": 2300, "upkeep": 80},
	{"id": "scouting", "name": "Dyrektor Skautingu", "desc": "Skauting pokazuje 2 kandydatów zamiast 1", "cost": 3000, "upkeep": 100},
	{"id": "management", "name": "Biuro Zarządu", "desc": "+1 Punkt Akcji na tydzień", "cost": 4000, "upkeep": 140},
]

const NAME_POOL = ["Zephyr","Kex","Rell","Nyx","Byte","Ghost","Vantage","Lumen","Frost","Blaze","Orbit","Static","Nova","Pixel","Rogue","Shade","Vertex","Cipher","Drift","Flux","Halo","Ion","Jinx","Krypt","Lynx","Mirage","Nex","Onyx","Prism","Quill","Raze","Sable","Tempo","Umbra","Volt","Wisp","Xen","Yara","Zed","Astra"]
const SPONSOR_NAMES = ["NitroFuel","ByteWear","PulseGear","QuantumChips","ZenithEnergy","VoltCola","ApexPeripherals","NovaBank","HyperNet","TitanChairs","CoreDrinks","PrimeAudio","FluxMonitors","OrbitTelecom","SparkWear"]
const RIVAL_ORG_NAMES = ["Crimson Circuit","Obsidian Wolves","Neon Dynasty","Ashen Vanguard","Solaris Union","Voidwalkers","Ironclad Syndicate","Prism Collective","Wraith Company","Frostbyte Guild","Zenith Rising","Rustline Crew"]

const SEASON_LENGTH = 10
const RIVALS_PER_SEASON = 4

const TRAITS = [
	{"id": "leader", "name": "Lider Drużyny", "icon": "🎖️", "desc": "Podnosi morale całej drużyny co tydzień."},
	{"id": "toxic", "name": "Utalentowany, ale Toksyczny", "icon": "☠️", "desc": "Szybciej się rozwija, ale obniża morale reszty składu."},
	{"id": "injury", "name": "Podatny na Kontuzje", "icon": "🩹", "desc": "Częściej łapie drobne kontuzje."},
	{"id": "consistent", "name": "Stabilny", "icon": "🧊", "desc": "Drużyna gra mniej losowo, gdy jest w składzie."},
	{"id": "media", "name": "Gwiazda Mediów", "icon": "📸", "desc": "Generuje dodatkową reputację co tydzień."},
	{"id": "mentor", "name": "Mentor", "icon": "🧑‍🏫", "desc": "Przyspiesza rozwój pozostałych zawodników w składzie."},
	{"id": "prodigy", "name": "Wschodzący Talent", "icon": "🌟", "desc": "Bardzo szybki rozwój, ale wymaga uwagi (obniża własne morale)."},
	{"id": "volatile", "name": "Niestabilny Emocjonalnie", "icon": "🎭", "desc": "Duże wahania morale po wygranych i porażkach."},
]

const MARKET_SIZE = 6
const MARKET_LISTING_LIFESPAN = 3

const BOARD_GOAL_DURATION = 10
const BOARD_GOAL_TYPES = ["money", "wins", "reputation", "sponsor"]

const ACHIEVEMENTS = [
	{"id": "first_division", "name": "Pierwsze Kroki", "icon": "grid", "desc": "Otwórz swój pierwszy oddział."},
	{"id": "first_win", "name": "Pierwsza Wygrana", "icon": "trophy", "desc": "Wygraj pierwszy mecz ligowy."},
	{"id": "first_promotion", "name": "W Górę!", "icon": "trending", "desc": "Awansuj oddział do wyższej ligi."},
	{"id": "legend", "name": "Legenda Sceny", "icon": "star", "desc": "Doprowadź oddział do najwyższej ligi (Legenda)."},
	{"id": "all_titles", "name": "Multi-Gaming Empire", "icon": "globe", "desc": "Otwórz wszystkie 6 tytułów jednocześnie."},
	{"id": "hundred_wins", "name": "Setka", "icon": "medal", "desc": "Zdobądź łącznie 100 zwycięstw."},
	{"id": "rich", "name": "Imperium Finansowe", "icon": "coin", "desc": "Zgromadź budżet 50 000 zł."},
	{"id": "all_sponsors", "name": "Ulubieniec Marek", "icon": "briefcase", "desc": "Podpisz maksymalną liczbę sponsorów (3)."},
	{"id": "all_upgrades", "name": "Nowoczesna Infrastruktura", "icon": "gear", "desc": "Wykup wszystkie ulepszenia HQ."},
	{"id": "market_shopper", "name": "Łowca Talentów", "icon": "swap", "desc": "Kup zawodnika z rynku transferowego."},
	{"id": "survivor", "name": "Powrót z Krawędzi", "icon": "trending", "desc": "Wróć na plus po tym, jak budżet spadł poniżej -2000 zł."},
	{"id": "board_favorite", "name": "Ulubieniec Zarządu", "icon": "target", "desc": "Zrealizuj cel zarządu."},
]

# RANDOM_EVENTS effects are implemented as code (see GameLogic.gd) rather than
# stored as callables, since GDScript resource dictionaries can't hold Callables
# cleanly across save/load. Chance/type/text stay here for display + rolling.
const RANDOM_EVENTS = [
	{"id": "viral_clip", "chance": 0.12, "type": "good", "text": "Viralowy klip jednego z Twoich zawodników przyciąga nowych fanów."},
	{"id": "invoice", "chance": 0.10, "type": "bad", "text": "Nieoczekiwana faktura za wynajem biura."},
	{"id": "promising_brand", "chance": 0.10, "type": "good", "text": "Organizacja trafia na listę najbardziej obiecujących marek esportowych."},
	{"id": "locker_conflict", "chance": 0.10, "type": "bad", "text": "Konflikt w szatni obniża morale jednego z oddziałów."},
	{"id": "sponsor_bonus", "chance": 0.08, "type": "good", "text": "Sponsor przekazuje dodatkowy bonus za dobre wyniki."},
	{"id": "sick_player", "chance": 0.08, "type": "bad", "text": "Jeden z zawodników choruje i traci nieco formy."},
	{"id": "interview", "chance": 0.08, "type": "info", "text": "Branżowy portal publikuje wywiad z Twoją organizacją."},
	{"id": "investor", "chance": 0.06, "type": "good", "text": "Inwestor oferuje drobny zastrzyk gotówki w zamian za promocję."},
]

func find_title(id: String) -> Dictionary:
	for t in TITLES:
		if t.id == id:
			return t
	return {}

func find_upgrade(id: String) -> Dictionary:
	for u in HQ_UPGRADES:
		if u.id == id:
			return u
	return {}

func find_trait(id: String) -> Dictionary:
	for t in TRAITS:
		if t.id == id:
			return t
	return {}

func find_achievement(id: String) -> Dictionary:
	for a in ACHIEVEMENTS:
		if a.id == id:
			return a
	return {}
