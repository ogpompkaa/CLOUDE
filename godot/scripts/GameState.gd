extends Node
# Mutable game state singleton + persistence. Mirrors defaultState() / save() /
# load() from the web version's index.html.

const SAVE_PATH = "user://esports-org-sim-save-v2.json"

var data: Dictionary = {}

func default_state(org_name: String) -> Dictionary:
	return {
		"org_name": org_name if org_name != "" else "Moja Organizacja",
		"week": 0,
		"year": 1,
		"money": 15000,
		"reputation": 10,
		"action_points": 3,
		"divisions": [],
		"divisions_ever_opened": 0,
		"selected_division_id": "",
		"sponsors": [],
		"upgrades": {},
		"transfer_market": [],
		"total_weeks": 0,
		"board_goal": {},
		"board_goals_completed": 0,
		"market_purchases_ever": 0,
		"hit_near_bankruptcy": false,
		"achievements_unlocked": {},
		"over": false,
		"log": [],
	}

func new_game(org_name: String) -> void:
	data = default_state(org_name)

func save() -> void:
	var f = FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(data))
		f.close()

func load_save() -> bool:
	if not FileAccess.file_exists(SAVE_PATH):
		return false
	var f = FileAccess.open(SAVE_PATH, FileAccess.READ)
	if not f:
		return false
	var text = f.get_as_text()
	f.close()
	var parsed = JSON.parse_string(text)
	if typeof(parsed) != TYPE_DICTIONARY:
		return false
	data = parsed
	return true

func has_save() -> bool:
	return FileAccess.file_exists(SAVE_PATH)

func delete_save() -> void:
	if FileAccess.file_exists(SAVE_PATH):
		DirAccess.remove_absolute(SAVE_PATH)
