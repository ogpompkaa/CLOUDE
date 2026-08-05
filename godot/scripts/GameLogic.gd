extends Node
# Pure game-logic autoload. Ported 1:1 from index.html's action functions.
# Operates on GameState.data (a Dictionary) so save/load stays trivial JSON.

signal state_changed
signal season_recap_ready(recaps: Array)
signal game_over(title: String, text: String)

func d() -> Dictionary:
	return GameState.data

func clamp_f(v: float, lo: float, hi: float) -> float:
	return max(lo, min(hi, v))

func money_str(v: float) -> String:
	var sign = "-" if v < 0 else ""
	var n = int(round(abs(v)))
	var s = str(n)
	var out = ""
	var count = 0
	for i in range(s.length() - 1, -1, -1):
		out = s[i] + out
		count += 1
		if count % 3 == 0 and i != 0:
			out = " " + out
	return sign + out + " zł"

func random_name() -> String:
	var pool = GameData.NAME_POOL
	return pool[randi() % pool.size()] + str(randi() % 100)

func add_log(text: String, type: String = "info") -> void:
	var s = d()
	var log_arr: Array = s.log
	log_arr.push_front({"text": text, "type": type})
	if log_arr.size() > 60:
		log_arr.resize(60)

func owned(id: String) -> bool:
	return d().upgrades.get(id, false)

func max_action_points() -> int:
	return 3 + (1 if owned("management") else 0)

func get_division(id: String) -> Dictionary:
	for div in d().divisions:
		if div.id == id:
			return div
	return {}

func div_index(id: String) -> int:
	var divisions: Array = d().divisions
	for i in range(divisions.size()):
		if divisions[i].id == id:
			return i
	return -1

func selected_division() -> Dictionary:
	var sid = d().selected_division_id
	if sid == "":
		return {}
	return get_division(sid)

func has_trait(p, id: String) -> bool:
	if p == null:
		return false
	return (p.traits as Array).has(id)

func roll_traits() -> Array:
	var r = randf()
	var count = 0
	if r < 0.5:
		count = 1
	elif r < 0.65:
		count = 2
	if count == 0:
		return []
	var ids = []
	for t in GameData.TRAITS:
		ids.append(t.id)
	ids.shuffle()
	return ids.slice(0, count)

func generate_player(tier: int) -> Dictionary:
	var skill = clamp_f(10 + tier * 5 + randf() * 25, 0, 100)
	return {
		"name": random_name(), "skill": skill,
		"morale": round(60 + randf() * 20), "salary": round(60 + skill * 4),
		"traits": roll_traits(),
	}

func generate_market_listing() -> Dictionary:
	var tier_flavor = randi() % GameData.DIVISION_TIERS.size()
	var p = generate_player(tier_flavor)
	return {
		"id": p.name + "-" + str(randi() % 100000),
		"name": p.name, "skill": p.skill, "morale": p.morale, "salary": p.salary, "traits": p.traits,
		"asking_price": round(p.salary * (4 + randf() * 4)),
		"weeks_left": GameData.MARKET_LISTING_LIFESPAN,
	}

func refill_market() -> void:
	var s = d()
	while s.transfer_market.size() < GameData.MARKET_SIZE:
		s.transfer_market.append(generate_market_listing())

func generate_rivals(tier: int) -> Array:
	var base = GameData.DIVISION_TIERS[tier].req_skill + 15
	var names = GameData.RIVAL_ORG_NAMES.duplicate()
	names.shuffle()
	var out = []
	for i in range(min(GameData.RIVALS_PER_SEASON, names.size())):
		out.append({"name": names[i], "power": clamp_f(base + (randf() - 0.5) * 20, 10, 100), "points": 0})
	return out

func new_season(tier: int) -> Dictionary:
	return {"week": 0, "length": GameData.SEASON_LENGTH, "rivals": generate_rivals(tier)}

func total_wins() -> int:
	var t = 0
	for div in d().divisions:
		t += int(div.wins)
	return t

func generate_board_goal() -> Dictionary:
	var s = d()
	var types = GameData.BOARD_GOAL_TYPES
	var type = types[randi() % types.size()]
	var goal = {
		"type": type, "start_week": s.total_weeks, "deadline_week": s.total_weeks + GameData.BOARD_GOAL_DURATION,
		"start_money": s.money, "start_wins": total_wins(), "completed": false,
	}
	if type == "money":
		goal.target = 1500 + (randi() % 3000)
		goal.reward = round(goal.target * 0.4)
		goal.desc = "Zarząd oczekuje wzrostu budżetu o %s w ciągu %d tyg." % [money_str(goal.target), GameData.BOARD_GOAL_DURATION]
	elif type == "wins":
		goal.target = 5 + (randi() % 8)
		goal.reward = goal.target * 150
		goal.desc = "Zarząd oczekuje %d zwycięstw ligowych w ciągu %d tyg." % [goal.target, GameData.BOARD_GOAL_DURATION]
	elif type == "reputation":
		goal.target = clamp_f(round(s.reputation) + 8 + (randi() % 15), 0, 100)
		goal.reward = 1200
		goal.desc = "Zarząd oczekuje reputacji na poziomie %d/100 w ciągu %d tyg." % [goal.target, GameData.BOARD_GOAL_DURATION]
	else:
		goal.target = min(3, s.sponsors.size() + 1)
		goal.reward = 1000
		goal.desc = "Zarząd oczekuje podpisania kolejnego sponsora w ciągu %d tyg." % GameData.BOARD_GOAL_DURATION
	return goal

func board_goal_progress() -> float:
	var s = d()
	var g = s.board_goal
	if g.is_empty():
		return 0
	if g.type == "money":
		return clamp_f(round(s.money - g.start_money), 0, g.target)
	if g.type == "wins":
		return clamp_f(total_wins() - g.start_wins, 0, g.target)
	if g.type == "reputation":
		return clamp_f(round(s.reputation), 0, g.target)
	if g.type == "sponsor":
		return clamp_f(s.sponsors.size(), 0, g.target)
	return 0

func check_board_goal() -> void:
	var s = d()
	if s.board_goal.is_empty():
		return
	var g = s.board_goal
	if board_goal_progress() >= g.target:
		s.money += g.reward
		s.reputation = clamp_f(s.reputation + 3, 0, 100)
		s.board_goals_completed = int(s.get("board_goals_completed", 0)) + 1
		add_log("Cel zarządu zrealizowany! Nagroda: +%s." % money_str(g.reward), "good")
		s.board_goal = generate_board_goal()
		add_log("Nowy cel zarządu: %s" % s.board_goal.desc, "info")

func check_achievements() -> void:
	var s = d()
	for a in GameData.ACHIEVEMENTS:
		if s.achievements_unlocked.get(a.id, false):
			continue
		if _achievement_check(a.id, s):
			s.achievements_unlocked[a.id] = true
			add_log("Nowe osiągnięcie: %s!" % a.name, "good")

func _achievement_check(id: String, s: Dictionary) -> bool:
	match id:
		"first_division": return s.divisions_ever_opened >= 1
		"first_win": return total_wins() >= 1
		"first_promotion":
			for div in s.divisions:
				if div.tier >= 1: return true
			return false
		"legend":
			for div in s.divisions:
				if div.tier >= 5: return true
			return false
		"all_titles": return s.divisions.size() >= 6
		"hundred_wins": return total_wins() >= 100
		"rich": return s.money >= 50000
		"all_sponsors": return s.sponsors.size() >= 3
		"all_upgrades":
			for u in GameData.HQ_UPGRADES:
				if not s.upgrades.get(u.id, false): return false
			return true
		"market_shopper": return int(s.get("market_purchases_ever", 0)) >= 1
		"survivor": return s.get("hit_near_bankruptcy", false) and s.money >= 0
		"board_favorite": return int(s.get("board_goals_completed", 0)) >= 1
	return false

func compute_score() -> int:
	var s = d()
	var wins = total_wins()
	var best_tier = 0
	for div in s.divisions:
		best_tier = max(best_tier, int(div.tier))
	return round(s.money / 50.0 + s.reputation * 5 + wins * 40 + best_tier * 300 + s.divisions_ever_opened * 100)

func check_game_over() -> void:
	var s = d()
	if s.over:
		return
	if s.money <= -5000:
		s.over = true
		game_over.emit("Bankructwo", "Organizacja nie udźwignęła własnych kosztów. Wierzyciele przejmują to, co zostało.")

# --- Round simulation ---
# Match winner is a single dice roll at a capped win chance; round score is a
# cosmetic shape generated afterwards. See index.html comment for why: racing
# independent per-round rolls to 13 compounds any skill edge into near-certainty.
func simulate_round_shape(reduced_variance: bool) -> Dictionary:
	var a = 0
	var b = 0
	var variance_scale = 0.2 if reduced_variance else 0.4
	while a < 13 and b < 13:
		var round_chance = clamp_f(0.5 + (randf() - 0.5) * variance_scale, 0.08, 0.95)
		if randf() < round_chance:
			a += 1
		else:
			b += 1
	return {"winner_score": max(a, b), "loser_score": min(a, b)}

# --- Division actions ---

func open_division(title_id: String) -> void:
	var s = d()
	if s.over or s.action_points < 1:
		return
	var title = GameData.find_title(title_id)
	if title.is_empty() or not get_division(title_id).is_empty():
		return
	if s.money < title.entry_cost:
		add_log("Za mało pieniędzy, by otworzyć oddział %s." % title.name, "bad")
		state_changed.emit()
		return
	s.action_points -= 1
	s.money -= title.entry_cost
	s.divisions.append({
		"id": title_id, "title": title.name, "icon": title.icon, "tier": 0,
		"roster": [null, null, null, null, null], "pending_candidate": [],
		"wins": 0, "losses": 0, "season_points": 0, "season": new_season(0),
	})
	s.divisions_ever_opened += 1
	s.selected_division_id = title_id
	add_log("Otwierasz nowy oddział: %s!" % title.name, "good")
	state_changed.emit()

func close_division(div_id: String) -> void:
	var s = d()
	var idx = div_index(div_id)
	if idx == -1:
		return
	var div = s.divisions[idx]
	var title = GameData.find_title(div_id)
	var refund = round(title.entry_cost * 0.3)
	s.money += refund
	s.divisions.remove_at(idx)
	if s.selected_division_id == div_id:
		s.selected_division_id = s.divisions[0].id if s.divisions.size() > 0 else ""
	add_log("Zamykasz oddział %s, odzyskujesz %s." % [div.title, money_str(refund)], "bad")
	state_changed.emit()

func scout_player(div_id: String) -> void:
	var s = d()
	if s.over or s.action_points < 1:
		return
	var div = get_division(div_id)
	if div.is_empty() or div.pending_candidate.size() > 0:
		return
	var empty_idx = _first_empty_slot(div)
	if empty_idx == -1:
		add_log("Skład %s jest już pełny." % div.title, "bad")
		state_changed.emit()
		return
	s.action_points -= 1
	var candidates = [generate_player(div.tier)]
	if owned("scouting"):
		candidates.append(generate_player(div.tier))
	div.pending_candidate = candidates
	add_log("Skautujesz nowych kandydatów dla %s." % div.title, "info")
	state_changed.emit()

func _first_empty_slot(div: Dictionary) -> int:
	var roster: Array = div.roster
	for i in range(roster.size()):
		if roster[i] == null:
			return i
	return -1

func sign_candidate(div_id: String, idx: int) -> void:
	var s = d()
	var div = get_division(div_id)
	if div.is_empty() or div.pending_candidate.size() <= idx:
		return
	var cand = div.pending_candidate[idx]
	var signing_bonus = cand.salary * 2
	if s.money < signing_bonus:
		add_log("Za mało pieniędzy na bonus podpisania.", "bad")
		state_changed.emit()
		return
	var empty_idx = _first_empty_slot(div)
	if empty_idx == -1:
		add_log("Skład jest już pełny.", "bad")
		div.pending_candidate = []
		state_changed.emit()
		return
	s.money -= signing_bonus
	div.roster[empty_idx] = cand
	div.pending_candidate = []
	add_log("Podpisujesz zawodnika %s (%s)." % [cand.name, div.title], "good")
	state_changed.emit()

func reject_candidates(div_id: String) -> void:
	var div = get_division(div_id)
	if div.is_empty():
		return
	div.pending_candidate = []
	state_changed.emit()

func release_player(div_id: String, slot_idx: int) -> void:
	var s = d()
	var div = get_division(div_id)
	if div.is_empty():
		return
	var p = div.roster[slot_idx]
	if p == null:
		return
	div.roster[slot_idx] = null
	s.reputation = clamp_f(s.reputation - 2, 0, 100)
	add_log("Zwalniasz zawodnika %s z %s." % [p.name, div.title], "bad")
	state_changed.emit()

func sell_player(div_id: String, slot_idx: int) -> void:
	var s = d()
	var div = get_division(div_id)
	if div.is_empty():
		return
	var p = div.roster[slot_idx]
	if p == null:
		return
	var price = round(p.salary * 3)
	div.roster[slot_idx] = null
	s.money += price
	add_log("Sprzedajecie zawodnika %s z %s za %s." % [p.name, div.title, money_str(price)], "good")
	state_changed.emit()

func buy_from_market(listing_id: String) -> void:
	var s = d()
	if s.over or s.action_points < 1:
		return
	var div = selected_division()
	if div.is_empty():
		add_log("Najpierw wybierz oddział, do którego chcesz dopisać zawodnika.", "bad")
		state_changed.emit()
		return
	var empty_idx = _first_empty_slot(div)
	if empty_idx == -1:
		add_log("Skład %s jest już pełny." % div.title, "bad")
		state_changed.emit()
		return
	var idx = -1
	for i in range(s.transfer_market.size()):
		if s.transfer_market[i].id == listing_id:
			idx = i
			break
	if idx == -1:
		return
	var listing = s.transfer_market[idx]
	if s.money < listing.asking_price:
		add_log("Za mało pieniędzy na transfer %s." % listing.name, "bad")
		state_changed.emit()
		return
	s.action_points -= 1
	s.money -= listing.asking_price
	div.roster[empty_idx] = {"name": listing.name, "skill": listing.skill, "morale": listing.morale, "salary": listing.salary, "traits": listing.traits}
	s.transfer_market.remove_at(idx)
	s.market_purchases_ever = int(s.get("market_purchases_ever", 0)) + 1
	refill_market()
	add_log("Kupujecie %s z rynku transferowego dla %s za %s!" % [listing.name, div.title, money_str(listing.asking_price)], "good")
	state_changed.emit()

func train_division(div_id: String) -> void:
	var s = d()
	if s.over or s.action_points < 1:
		return
	var div = get_division(div_id)
	if div.is_empty():
		return
	var filled = _filled_roster(div)
	if filled.is_empty():
		add_log("Brak zawodników do trenowania w %s." % div.title, "bad")
		state_changed.emit()
		return
	var cost = 250
	if s.money < cost:
		add_log("Za mało pieniędzy na sesję treningową.", "bad")
		state_changed.emit()
		return
	s.action_points -= 1
	s.money -= cost
	var bonus = 1.3 if owned("facility") else 1.0
	var has_mentor = false
	for p in filled:
		if has_trait(p, "mentor"):
			has_mentor = true
			break
	for p in filled:
		var mult = bonus
		if has_trait(p, "toxic"):
			mult *= 1.3
		if has_trait(p, "prodigy"):
			mult *= 1.4
		if has_mentor and not has_trait(p, "mentor"):
			mult *= 1.2
		p.skill = clamp_f(p.skill + (2 + randf() * 3) * mult, 0, 100)
	add_log("Trenujecie oddział %s." % div.title, "good")
	state_changed.emit()

func _filled_roster(div: Dictionary) -> Array:
	var out = []
	for p in div.roster:
		if p != null:
			out.append(p)
	return out

func next_opponent(div: Dictionary):
	if div.season.rivals.is_empty():
		return null
	return div.season.rivals[div.season.week % div.season.rivals.size()]

func compete_division(div_id: String) -> void:
	var s = d()
	if s.over or s.action_points < 1:
		return
	var div = get_division(div_id)
	if div.is_empty():
		return
	var active = _filled_roster(div)
	if active.size() < 3:
		add_log("Potrzebujesz co najmniej 3 zawodników, by rywalizować w %s." % div.title, "bad")
		state_changed.emit()
		return
	var opponent = next_opponent(div)
	if opponent == null:
		return
	s.action_points -= 1
	var avg_skill = 0.0
	for p in active:
		avg_skill += p.skill
	avg_skill /= active.size()
	var difficulty = opponent.power
	var win_chance = clamp_f(0.5 + (avg_skill - difficulty) / 140.0, 0.2, 0.8)
	var has_consistent = false
	for p in active:
		if has_trait(p, "consistent"):
			has_consistent = true
			break
	var win = randf() < win_chance
	var shape = simulate_round_shape(has_consistent)
	var my_score = shape.winner_score if win else shape.loser_score
	var opp_score = shape.loser_score if win else shape.winner_score
	var margin = clamp_f(abs(my_score - opp_score) / 13.0, 0.15, 1.0)
	var tier_info = GameData.DIVISION_TIERS[div.tier]
	var score = "%d:%d" % [my_score, opp_score]
	var pr_bonus = 1.3 if owned("pr") else 1.0
	var analyst_bonus = 1.5 if owned("analysts") else 1.0

	if win:
		var prize = round(tier_info.prize_base * (0.3 + margin * 0.6))
		s.money += prize
		s.reputation = clamp_f(s.reputation + (1 + margin * 4) * pr_bonus, 0, 100)
		div.wins += 1
		div.season_points += 3
		for p in active:
			var growth_mult = analyst_bonus
			if has_trait(p, "toxic"):
				growth_mult *= 1.3
			if has_trait(p, "prodigy"):
				growth_mult *= 1.4
			p.skill = clamp_f(p.skill + (0.5 + randf()) * growth_mult, 0, 100)
			p.morale = clamp_f(p.morale + (6 if has_trait(p, "volatile") else 3), 0, 100)
		add_log("%s vs %s: wygrywacie %s! +%s." % [div.title, opponent.name, score, money_str(prize)], "good")
	else:
		s.reputation = clamp_f(s.reputation - (1 + margin * 2), 0, 100)
		div.losses += 1
		for p in active:
			p.morale = clamp_f(p.morale - (8 if has_trait(p, "volatile") else 4), 0, 100)
		add_log("%s vs %s: przegrywacie %s." % [div.title, opponent.name, score], "bad")
	check_achievements()
	state_changed.emit()

func resolve_season_end(div: Dictionary) -> Dictionary:
	var table = [{"name": div.title + " (Ty)", "points": div.season_points, "is_player": true}]
	for r in div.season.rivals:
		table.append({"name": r.name, "points": r.points, "is_player": false})
	table.sort_custom(func(a, b): return a.points > b.points)
	var rank = 0
	for i in range(table.size()):
		if table[i].is_player:
			rank = i + 1
			break
	var total = table.size()

	var outcome = "Utrzymanie"
	if rank <= 2 and div.tier < GameData.DIVISION_TIERS.size() - 1:
		div.tier += 1
		outcome = "Awans"
		add_log("Koniec sezonu: %s kończy na %d. miejscu i AWANSUJE do ligi %s!" % [div.title, rank, GameData.DIVISION_TIERS[div.tier].name], "good")
	elif rank >= total - 1 and div.tier > 0:
		div.tier -= 1
		outcome = "Spadek"
		add_log("Koniec sezonu: %s kończy na %d. miejscu i SPADA do ligi %s." % [div.title, rank, GameData.DIVISION_TIERS[div.tier].name], "bad")
	else:
		add_log("Koniec sezonu: %s kończy na %d. miejscu z %d pkt. Utrzymanie w lidze %s." % [div.title, rank, div.season_points, GameData.DIVISION_TIERS[div.tier].name], "info")

	var prize = round(GameData.DIVISION_TIERS[div.tier].prize_base * float(total - rank + 1) / total * 0.6)
	d().money += prize
	add_log("Nagroda za sezon (%s): +%s." % [div.title, money_str(prize)], "good")

	var recap = {"div_title": div.title, "div_icon": div.icon, "rank": rank, "total": total, "outcome": outcome, "prize": prize, "table": table}

	div.season_points = 0
	div.season = new_season(div.tier)

	return recap

# --- Org-level actions ---

func sign_sponsor() -> void:
	var s = d()
	if s.over or s.action_points < 1:
		return
	var max_sponsors = 3
	if s.sponsors.size() >= max_sponsors:
		add_log("Masz już maksymalną liczbę sponsorów.", "bad")
		state_changed.emit()
		return
	var req_rep = 10 + s.sponsors.size() * 20
	if s.reputation < req_rep:
		add_log("Potrzebujesz reputacji %d, by przyciągnąć kolejnego sponsora." % req_rep, "bad")
		state_changed.emit()
		return
	s.action_points -= 1
	var used = []
	for sp in s.sponsors:
		used.append(sp.name)
	var pool = []
	for n in GameData.SPONSOR_NAMES:
		if not used.has(n):
			pool.append(n)
	if pool.is_empty():
		pool = GameData.SPONSOR_NAMES
	var name = pool[randi() % pool.size()]
	var income = round(100 + s.reputation * 8 + randf() * 150)
	s.sponsors.append({"name": name, "income": income})
	add_log("Podpisujecie umowę sponsorską z %s! +%s/tydzień." % [name, money_str(income)], "good")
	check_achievements()
	state_changed.emit()

func buy_upgrade(id: String) -> void:
	var s = d()
	var u = GameData.find_upgrade(id)
	if u.is_empty() or owned(id) or s.money < u.cost or s.over:
		return
	s.money -= u.cost
	s.upgrades[id] = true
	add_log("Budujecie: %s." % u.name, "good")
	check_achievements()
	state_changed.emit()

func end_week() -> void:
	var s = d()
	if s.over:
		return
	s.week += 1
	if s.week > 52:
		s.week = 1
		s.year += 1

	var total_salaries = 0.0
	for div in s.divisions:
		for p in div.roster:
			if p != null:
				total_salaries += p.salary
	var hq_upkeep = 0.0
	for u in GameData.HQ_UPGRADES:
		if owned(u.id):
			hq_upkeep += u.upkeep
	var sponsor_income = 0.0
	for sp in s.sponsors:
		sponsor_income += sp.income

	s.money -= total_salaries
	s.money -= hq_upkeep
	s.money += sponsor_income

	var morale_regen = 3 if owned("medical") else 1
	for div in s.divisions:
		var filled = _filled_roster(div)
		var has_leader = false
		var has_toxic = false
		for p in filled:
			if has_trait(p, "leader"):
				has_leader = true
			if has_trait(p, "toxic"):
				has_toxic = true
		for p in filled:
			var delta = morale_regen
			if has_leader:
				delta += 2
			if has_toxic and not has_trait(p, "toxic"):
				delta -= 1
			if has_trait(p, "prodigy"):
				delta -= 1
			p.morale = clamp_f(p.morale + delta, 0, 100)

			if has_trait(p, "media"):
				s.reputation = clamp_f(s.reputation + 1, 0, 100)
			if has_trait(p, "injury") and randf() < 0.06:
				p.skill = clamp_f(p.skill - (2 + randf() * 3), 0, 100)
				p.morale = clamp_f(p.morale - 8, 0, 100)
				add_log("%s (%s) łapie drobną kontuzję." % [p.name, div.title], "bad")

	for l in s.transfer_market:
		l.weeks_left -= 1
	var survivors = []
	for l in s.transfer_market:
		if l.weeks_left <= 0:
			add_log("%s podpisuje kontrakt gdzie indziej i znika z rynku transferowego." % l.name, "info")
		elif randf() < 0.12:
			var rival = GameData.RIVAL_ORG_NAMES[randi() % GameData.RIVAL_ORG_NAMES.size()]
			add_log("%s zostaje podkradziony z rynku przez %s!" % [l.name, rival], "bad")
		else:
			survivors.append(l)
	s.transfer_market = survivors
	refill_market()

	_roll_random_event(s)

	s.action_points = max_action_points()
	add_log("Koniec tygodnia %d. Wypłaty: %s, przychód sponsorów: %s." % [s.week, money_str(total_salaries + hq_upkeep), money_str(sponsor_income)], "info")

	var season_recaps = []
	for div in s.divisions:
		if div.season.is_empty():
			div.season = new_season(div.tier)
		for r in div.season.rivals:
			r.points += round(r.power / 12.0 + (randf() - 0.5) * 2)
		div.season.week += 1
		if div.season.week >= div.season.length:
			season_recaps.append(resolve_season_end(div))

	s.total_weeks += 1
	check_board_goal()
	if not s.board_goal.is_empty() and s.total_weeks >= s.board_goal.deadline_week:
		s.reputation = clamp_f(s.reputation - 4, 0, 100)
		add_log("Nie udało się zrealizować celu zarządu na czas. Zarząd jest niezadowolony (-4 reputacji).", "bad")
		s.board_goal = generate_board_goal()
		add_log("Nowy cel zarządu: %s" % s.board_goal.desc, "info")

	if s.money < -2000:
		s.hit_near_bankruptcy = true

	check_achievements()
	check_game_over()
	state_changed.emit()
	if season_recaps.size() > 0:
		season_recap_ready.emit(season_recaps)

func _roll_random_event(s: Dictionary) -> void:
	var roll = randf()
	var acc = 0.0
	for ev in GameData.RANDOM_EVENTS:
		acc += ev.chance
		if roll < acc:
			_apply_random_event(ev.id, s)
			add_log(ev.text, ev.type)
			break

func _apply_random_event(id: String, s: Dictionary) -> void:
	match id:
		"viral_clip":
			s.reputation = clamp_f(s.reputation + 4, 0, 100)
		"invoice":
			s.money -= round(500 + randf() * 500)
		"promising_brand":
			s.reputation = clamp_f(s.reputation + 6, 0, 100)
		"locker_conflict":
			var div = _random_division(s)
			if div != null:
				for p in div.roster:
					if p != null:
						p.morale = clamp_f(p.morale - 6, 0, 100)
		"sponsor_bonus":
			s.money += round(300 + randf() * 400)
		"sick_player":
			var div2 = _random_division(s)
			var p2 = _random_player(div2)
			if p2 != null:
				p2.skill = clamp_f(p2.skill - 3, 0, 100)
		"interview":
			s.reputation = clamp_f(s.reputation + 2, 0, 100)
		"investor":
			s.money += 800

func _random_division(s: Dictionary):
	if s.divisions.is_empty():
		return null
	return s.divisions[randi() % s.divisions.size()]

func _random_player(div):
	if div == null:
		return null
	var filled = _filled_roster(div)
	if filled.is_empty():
		return null
	return filled[randi() % filled.size()]

func action_retire() -> void:
	var s = d()
	s.over = true
	game_over.emit("Koniec działalności", "Postanawiasz zamknąć organizację na własnych warunkach.")
