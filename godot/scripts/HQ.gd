extends Control

@onready var org_label: Label = $Margin/VBox/TopBar/OrgLabel
@onready var money_label: Label = $Margin/VBox/TopBar/MoneyLabel
@onready var rep_label: Label = $Margin/VBox/TopBar/RepLabel
@onready var ap_label: Label = $Margin/VBox/TopBar/APLabel
@onready var week_label: Label = $Margin/VBox/TopBar/WeekLabel
@onready var end_week_button: Button = $Margin/VBox/TopBar/EndWeekButton

@onready var titles_list: VBoxContainer = $Margin/VBox/Body/LeftPanel/TitlesList
@onready var divisions_list: VBoxContainer = $Margin/VBox/Body/LeftPanel/DivisionsList

@onready var division_title: Label = $Margin/VBox/Body/CenterPanel/DivisionTitle
@onready var roster_list: VBoxContainer = $Margin/VBox/Body/CenterPanel/RosterList
@onready var candidate_panel: VBoxContainer = $Margin/VBox/Body/CenterPanel/CandidatePanel
@onready var scout_button: Button = $Margin/VBox/Body/CenterPanel/ActionsBar/ScoutButton
@onready var train_button: Button = $Margin/VBox/Body/CenterPanel/ActionsBar/TrainButton
@onready var compete_button: Button = $Margin/VBox/Body/CenterPanel/ActionsBar/CompeteButton
@onready var close_button: Button = $Margin/VBox/Body/CenterPanel/ActionsBar/CloseButton

@onready var log_list: VBoxContainer = $Margin/VBox/Body/RightPanel/LogScroll/LogList

func _ready() -> void:
	end_week_button.pressed.connect(func(): GameLogic.end_week())
	close_button.pressed.connect(_on_close_pressed)
	scout_button.pressed.connect(_on_scout_pressed)
	train_button.pressed.connect(_on_train_pressed)
	compete_button.pressed.connect(_on_compete_pressed)
	GameLogic.state_changed.connect(_on_state_changed)
	GameLogic.season_recap_ready.connect(_on_season_recap)
	GameLogic.game_over.connect(_on_game_over)
	refresh()

func _on_state_changed() -> void:
	GameState.save()
	refresh()

func _on_season_recap(recaps: Array) -> void:
	var lines = []
	for r in recaps:
		lines.append("%s: %d. miejsce (%s), +%s" % [r.div_title, r.rank, r.outcome, GameLogic.money_str(r.prize)])
	_show_dialog("Koniec sezonu", "\n".join(lines))

func _on_game_over(title: String, text: String) -> void:
	GameState.save()
	_show_dialog(title, text, true)

func _show_dialog(title: String, text: String, is_final: bool = false) -> void:
	var dlg = AcceptDialog.new()
	dlg.title = title
	dlg.dialog_text = text
	add_child(dlg)
	dlg.popup_centered(Vector2(420, 260))
	if is_final:
		dlg.confirmed.connect(func(): get_tree().change_scene_to_file("res://scenes/Start.tscn"))
		dlg.close_requested.connect(func(): get_tree().change_scene_to_file("res://scenes/Start.tscn"))

func _on_close_pressed() -> void:
	var div = GameLogic.selected_division()
	if not div.is_empty():
		GameLogic.close_division(div.id)

func _on_scout_pressed() -> void:
	var div = GameLogic.selected_division()
	if not div.is_empty():
		GameLogic.scout_player(div.id)

func _on_train_pressed() -> void:
	var div = GameLogic.selected_division()
	if not div.is_empty():
		GameLogic.train_division(div.id)

func _on_compete_pressed() -> void:
	var div = GameLogic.selected_division()
	if not div.is_empty():
		GameLogic.compete_division(div.id)

func refresh() -> void:
	var s = GameState.data
	org_label.text = s.org_name
	money_label.text = GameLogic.money_str(s.money)
	rep_label.text = "Reputacja %d/100" % round(s.reputation)
	ap_label.text = "AP %d/%d" % [s.action_points, GameLogic.max_action_points()]
	week_label.text = "Tydzień %d, Rok %d" % [s.week, s.year]
	end_week_button.disabled = s.over

	_refresh_titles()
	_refresh_divisions_list()
	_refresh_selected_division()
	_refresh_log()

func _clear(container: Node) -> void:
	for c in container.get_children():
		c.queue_free()

func _refresh_titles() -> void:
	_clear(titles_list)
	var s = GameState.data
	for t in GameData.TITLES:
		var already_open = not GameLogic.get_division(t.id).is_empty()
		var btn = Button.new()
		btn.text = "%s — %s" % [t.name, GameLogic.money_str(t.entry_cost)] if not already_open else "%s (otwarty)" % t.name
		btn.disabled = already_open or s.over or s.action_points < 1 or s.money < t.entry_cost
		btn.pressed.connect(func(): GameLogic.open_division(t.id))
		titles_list.add_child(btn)

func _refresh_divisions_list() -> void:
	_clear(divisions_list)
	var s = GameState.data
	for div in s.divisions:
		var btn = Button.new()
		var tier_name = GameData.DIVISION_TIERS[div.tier].name
		btn.text = "%s [%s] %dW-%dL" % [div.title, tier_name, div.wins, div.losses]
		btn.toggle_mode = true
		btn.button_pressed = (s.selected_division_id == div.id)
		btn.pressed.connect(func(): _select_division(div.id))
		divisions_list.add_child(btn)

func _select_division(id: String) -> void:
	GameState.data.selected_division_id = id
	GameState.save()
	refresh()

func _refresh_selected_division() -> void:
	_clear(roster_list)
	_clear(candidate_panel)
	var div = GameLogic.selected_division()
	var s = GameState.data
	if div.is_empty():
		division_title.text = "Wybierz lub otwórz oddział"
		scout_button.disabled = true
		train_button.disabled = true
		compete_button.disabled = true
		close_button.disabled = true
		return

	var tier_name = GameData.DIVISION_TIERS[div.tier].name
	division_title.text = "%s — Liga: %s" % [div.title, tier_name]

	for i in range(div.roster.size()):
		var p = div.roster[i]
		var row = HBoxContainer.new()
		var lbl = Label.new()
		if p == null:
			lbl.text = "Slot %d: pusty" % (i + 1)
		else:
			var traits_txt = ""
			if p.traits.size() > 0:
				var names = []
				for tid in p.traits:
					names.append(GameData.find_trait(tid).name)
				traits_txt = " [" + ", ".join(names) + "]"
			lbl.text = "%s — Skill %d, Morale %d, Pensja %d zł%s" % [p.name, round(p.skill), round(p.morale), p.salary, traits_txt]
		lbl.size_flags_horizontal = 3
		row.add_child(lbl)
		if p != null:
			var release_btn = Button.new()
			release_btn.text = "Zwolnij"
			release_btn.pressed.connect(func(): GameLogic.release_player(div.id, i))
			row.add_child(release_btn)
			var sell_btn = Button.new()
			sell_btn.text = "Sprzedaj"
			sell_btn.pressed.connect(func(): GameLogic.sell_player(div.id, i))
			row.add_child(sell_btn)
		roster_list.add_child(row)

	if div.pending_candidate.size() > 0:
		var hdr = Label.new()
		hdr.text = "Kandydaci do podpisania:"
		candidate_panel.add_child(hdr)
		for i in range(div.pending_candidate.size()):
			var c = div.pending_candidate[i]
			var row = HBoxContainer.new()
			var lbl = Label.new()
			lbl.text = "%s — Skill %d, Bonus %s" % [c.name, round(c.skill), GameLogic.money_str(c.salary * 2)]
			lbl.size_flags_horizontal = 3
			row.add_child(lbl)
			var sign_btn = Button.new()
			sign_btn.text = "Podpisz"
			sign_btn.pressed.connect(func(): GameLogic.sign_candidate(div.id, i))
			row.add_child(sign_btn)
			candidate_panel.add_child(row)
		var reject_btn = Button.new()
		reject_btn.text = "Odrzuć wszystkich"
		reject_btn.pressed.connect(func(): GameLogic.reject_candidates(div.id))
		candidate_panel.add_child(reject_btn)

	scout_button.disabled = s.over or s.action_points < 1 or div.pending_candidate.size() > 0
	train_button.disabled = s.over or s.action_points < 1
	compete_button.disabled = s.over or s.action_points < 1
	close_button.disabled = s.over

func _refresh_log() -> void:
	_clear(log_list)
	var s = GameState.data
	var count = min(30, s.log.size())
	for i in range(count):
		var entry = s.log[i]
		var lbl = Label.new()
		lbl.text = entry.text
		lbl.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		match entry.type:
			"good": lbl.add_theme_color_override("font_color", Color("#29ffb0"))
			"bad": lbl.add_theme_color_override("font_color", Color("#ff3b60"))
			_: lbl.add_theme_color_override("font_color", Color("#8991a6"))
		log_list.add_child(lbl)
