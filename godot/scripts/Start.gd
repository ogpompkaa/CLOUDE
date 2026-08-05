extends Control

@onready var name_input: LineEdit = $Center/Panel/VBox/NameInput
@onready var start_button: Button = $Center/Panel/VBox/StartButton
@onready var continue_button: Button = $Center/Panel/VBox/ContinueButton

func _ready() -> void:
	continue_button.visible = GameState.has_save()
	start_button.pressed.connect(_on_start_pressed)
	continue_button.pressed.connect(_on_continue_pressed)
	name_input.text_submitted.connect(func(_t): _on_start_pressed())

func _on_start_pressed() -> void:
	var org_name = name_input.text.strip_edges()
	GameState.new_game(org_name)
	GameState.data.board_goal = GameLogic.generate_board_goal()
	GameState.save()
	get_tree().change_scene_to_file("res://scenes/HQ.tscn")

func _on_continue_pressed() -> void:
	if GameState.load_save():
		get_tree().change_scene_to_file("res://scenes/HQ.tscn")
