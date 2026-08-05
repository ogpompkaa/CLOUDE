extends Control
# Placeholder HQ screen for Phase 1 verification.
# The real HQ view (divisions, roster, actions) is built in Phase 2.

@onready var info_label: Label = $Center/VBox/Info

func _ready() -> void:
	var d = GameState.data
	info_label.text = "%s\nBudżet: %d zł\nReputacja: %d\nPunkty Akcji: %d\nTydzień %d, Rok %d" % [
		d.org_name, d.money, d.reputation, d.action_points, d.week, d.year
	]
