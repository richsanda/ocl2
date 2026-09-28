import sqlite3
from pathlib import Path

from flask import Flask, g, jsonify, request

from etl import TEAM_OWNERS, TEAM_GROUPS, TEAM_OWNER_HISTORY

HERE = Path(__file__).resolve().parent
DB_PATH = HERE / "data" / "ocl.db"
ALL_POSITIONS = ["QB", "RB", "WR", "TE", "D/ST", "K"]
RESULT_SIZE = 100

app = Flask(__name__, static_folder="static", static_url_path="")


@app.route("/")
def index():
    return app.send_static_file("index.html")


def get_db():
    if "db" not in g:
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
    return g.db


@app.teardown_appcontext
def close_db(exception=None):
    db = g.pop("db", None)
    if db is not None:
        db.close()


def game_number(season, scoring_period):
    return season * 100 + scoring_period


def csv_ints(value):
    return [int(v) for v in value.split(",") if v.strip() != ""]


def csv_strs(value):
    return [v for v in value.split(",") if v.strip() != ""]


def ruxbee_bugton_bounds(db):
    """ruxbeeMax: the highest a team's single-game floor (its lowest starter's points) has
    ever been -- the practical ceiling for a 'min player points >= N' filter.
    bugtonMin: the lowest a team's single-game ceiling (its highest starter's points) has
    ever been -- the practical floor for a 'max player points <= N' filter.
    Restricted to single-week games (weeks_covered=1, the default view) -- a 2-week combined
    playoff round sums two games' worth of points per player, which inflates both bounds
    unrealistically relative to what's normally being filtered."""
    rows = db.execute(
        "SELECT MIN(pw.points) mn, MAX(pw.points) mx FROM player_weeks pw "
        "JOIN team_weeks tw ON tw.id = pw.team_week_id "
        "JOIN games g ON g.id = tw.game_id "
        "WHERE g.weeks_covered = 1 "
        "GROUP BY pw.team_week_id"
    ).fetchall()
    return max(r["mn"] for r in rows), min(r["mx"] for r in rows)


@app.route("/api/meta")
def meta():
    db = get_db()
    min_season, max_season = db.execute("SELECT MIN(season), MAX(season) FROM games").fetchone()
    current_game_number = db.execute("SELECT MAX(game_number) FROM games").fetchone()[0]
    current_season, current_scoring_period = divmod(current_game_number, 100)
    ruxbee_max, bugton_min = ruxbee_bugton_bounds(db)
    return jsonify({
        "teams": [{"teamNumber": n, "owner": o} for n, o in sorted(TEAM_OWNERS.items())],
        "ownerHistory": {
            n: [{"fromSeason": s, "owner": o} for s, o in history]
            for n, history in TEAM_OWNER_HISTORY.items()
        },
        "groups": [{"name": name, "teamNumbers": nums} for name, nums in TEAM_GROUPS],
        "positions": ALL_POSITIONS,
        "minSeason": min_season,
        "maxSeason": max_season,
        "currentSeason": current_season,
        "currentScoringPeriod": current_scoring_period,
        "ruxbeeMax": ruxbee_max,
        "bugtonMin": bugton_min,
    })


def points_per_team(rows):
    """rows: sqlite Row objects with team_number, points -- already one row per player_week."""
    totals = {}
    for r in rows:
        totals[r["team_number"]] = totals.get(r["team_number"], 0) + r["points"]
    return sorted(
        ({"teamNumber": k, "points": v} for k, v in totals.items()),
        key=lambda x: -x["points"],
    )


@app.route("/api/players/points")
def players_points():
    db = get_db()

    team_numbers = csv_ints(request.args.get("teamNumbers", "")) or list(TEAM_OWNERS.keys())
    positions = csv_strs(request.args.get("positions", "")) or ALL_POSITIONS
    start_season = int(request.args.get("startSeason", 2005))
    end_season = int(request.args.get("endSeason", 2100))
    start_period = int(request.args.get("startScoringPeriod", 1))
    end_period = int(request.args.get("endScoringPeriod", 17))

    start_game = game_number(start_season, start_period)
    end_game = game_number(end_season, end_period)

    current_game = db.execute("SELECT MAX(game_number) FROM games").fetchone()[0]

    rows = db.execute(
        """
        SELECT pw.player_id, p.name, pw.position, pw.points,
               tw.team_number, tw.win, tw.loss, tw.tie, g.game_number
        FROM player_weeks pw
        JOIN players p ON p.id = pw.player_id
        JOIN team_weeks tw ON tw.id = pw.team_week_id
        JOIN games g ON g.id = tw.game_id
        WHERE tw.team_number IN ({})
          AND pw.position IN ({})
          AND g.game_number BETWEEN ? AND ?
        """.format(
            ",".join("?" * len(team_numbers)),
            ",".join("?" * len(positions)),
        ),
        (*team_numbers, *positions, start_game, end_game),
    ).fetchall()

    by_player = {}
    for r in rows:
        by_player.setdefault(r["player_id"], []).append(r)

    results = []
    for player_id, weeks in by_player.items():
        total_points = sum(w["points"] for w in weeks)
        current_weeks = [w for w in weeks if w["game_number"] == current_game]
        results.append({
            "playerId": player_id,
            "name": weeks[0]["name"],
            "position": weeks[0]["position"],
            "points": total_points,
            "games": len(weeks),
            "wins": sum(1 for w in weeks if w["win"]),
            "losses": sum(1 for w in weeks if w["loss"]),
            "ties": sum(1 for w in weeks if w["tie"]),
            "pointsPerTeam": points_per_team(weeks),
            "currentPointsPerTeam": points_per_team(current_weeks)[0] if current_weeks else None,
        })

    results.sort(key=lambda r: -r["points"])
    return jsonify(results[:RESULT_SIZE])


@app.route("/api/player/<int:player_id>")
def player_detail(player_id):
    db = get_db()

    name_row = db.execute("SELECT name FROM players WHERE id = ?", (player_id,)).fetchone()
    if name_row is None:
        return jsonify(None), 404

    rows = db.execute(
        """
        SELECT pw.points, pw.position, g.season, g.scoring_period,
               tw.team_number AS team_number, tw.points AS team_points, tw.win, tw.loss, tw.tie,
               opp.team_number AS opponent_team_number, opp.points AS opponent_points
        FROM player_weeks pw
        JOIN team_weeks tw ON tw.id = pw.team_week_id
        JOIN games g ON g.id = tw.game_id
        JOIN team_weeks opp ON opp.game_id = g.id AND opp.id != tw.id
        WHERE pw.player_id = ?
        ORDER BY g.season, g.scoring_period
        """,
        (player_id,),
    ).fetchall()

    return jsonify({
        "playerId": player_id,
        "name": name_row["name"],
        "position": rows[0]["position"] if rows else None,
        "points": sum(r["points"] for r in rows),
        "games": len(rows),
        "wins": sum(1 for r in rows if r["win"]),
        "losses": sum(1 for r in rows if r["loss"]),
        "ties": sum(1 for r in rows if r["tie"]),
        "gameStats": [
            {
                "season": r["season"],
                "scoringPeriod": r["scoring_period"],
                "teamNumber": r["team_number"],
                "teamPoints": r["team_points"],
                "opponentTeamNumber": r["opponent_team_number"],
                "opponentPoints": r["opponent_points"],
                "points": r["points"],
            }
            for r in rows
        ],
    })


@app.route("/api/games")
def games_list():
    db = get_db()

    team_numbers = csv_ints(request.args.get("teamNumbers", "")) or list(TEAM_OWNERS.keys())
    start_season = int(request.args.get("startSeason", 2005))
    end_season = int(request.args.get("endSeason", 2100))
    start_period = int(request.args.get("startScoringPeriod", 1))
    end_period = int(request.args.get("endScoringPeriod", 17))
    start_game = game_number(start_season, start_period)
    end_game = game_number(end_season, end_period)

    outcome = request.args.get("outcome")  # "win" | "loss" | "tie" | None
    ruxbee = request.args.get("ruxbee", type=int)  # min player points >= this
    bugton = request.args.get("bugton", type=int)  # max player points <= this
    sort = request.args.get("sort", "points_desc")
    include_multi_week = request.args.get("includeMultiWeek") == "true"

    outcome_clause = ""
    if outcome in ("win", "loss", "tie"):
        outcome_clause = f" AND tw.{outcome} = 1"

    multi_week_clause = "" if include_multi_week else " AND g.weeks_covered = 1"

    rows = db.execute(
        f"""
        SELECT tw.id AS tw_id, tw.team_number, tw.points AS team_points,
               tw.win, tw.loss, tw.tie,
               g.season, g.scoring_period, g.weeks_covered,
               opp.id AS opp_tw_id, opp.team_number AS opp_team_number, opp.points AS opp_points
        FROM team_weeks tw
        JOIN games g ON g.id = tw.game_id
        JOIN team_weeks opp ON opp.game_id = g.id AND opp.id != tw.id
        WHERE tw.team_number IN ({",".join("?" * len(team_numbers))})
          AND g.game_number BETWEEN ? AND ?
          {outcome_clause}
          {multi_week_clause}
        """,
        (*team_numbers, start_game, end_game),
    ).fetchall()

    tw_ids = [r["tw_id"] for r in rows] + [r["opp_tw_id"] for r in rows]
    minmax = {}
    if tw_ids:
        placeholders = ",".join("?" * len(tw_ids))
        for r in db.execute(
            f"SELECT team_week_id, MIN(points) mn, MAX(points) mx FROM player_weeks "
            f"WHERE team_week_id IN ({placeholders}) GROUP BY team_week_id",
            tw_ids,
        ):
            minmax[r["team_week_id"]] = (r["mn"], r["mx"])

    def players_for(tw_id):
        return [
            {"playerNumber": p["player_id"], "playerName": p["player_name"],
             "position": p["position"], "points": p["points"]}
            for p in db.execute(
                "SELECT pw.player_id, pw.player_name, pw.position, pw.points "
                "FROM player_weeks pw WHERE pw.team_week_id = ?",
                (tw_id,),
            )
        ]

    player_cache = {}

    def cached_players(tw_id):
        if tw_id not in player_cache:
            player_cache[tw_id] = players_for(tw_id)
        return player_cache[tw_id]

    results = []
    for r in rows:
        mn, mx = minmax.get(r["tw_id"], (None, None))
        if ruxbee is not None and (mn is None or mn < ruxbee):
            continue
        if bugton is not None and (mx is None or mx > bugton):
            continue

        results.append({
            "season": r["season"],
            "scoringPeriod": r["scoring_period"],
            "weeksCovered": r["weeks_covered"],
            "teamNumber": r["team_number"],
            "teamPoints": r["team_points"],
            "win": bool(r["win"]),
            "loss": bool(r["loss"]),
            "tie": bool(r["tie"]),
            "opponentTeamNumber": r["opp_team_number"],
            "opponentPoints": r["opp_points"],
            "minPlayerPoints": mn,
            "maxPlayerPoints": mx,
            "players": cached_players(r["tw_id"]),
            "opponentPlayers": cached_players(r["opp_tw_id"]),
        })

    # both margin sorts use the ABSOLUTE margin (most = biggest blowouts, least = closest
    # games/ties, either direction) so a game's two team-perspective rows land right next to
    # each other -- then a tertiary win-before-loss key puts the winner's row on top of the
    # pair (ties keep either order, there's no winner). Secondary: total points descending --
    # how high were the highest ties, then the highest 1-pt wins, etc.
    def win_first(x):
        return 0 if x["win"] else 1

    sort_keys = {
        "points_desc": lambda x: (-x["teamPoints"],),
        "points_asc": lambda x: (x["teamPoints"],),
        "margin_desc": lambda x: (-abs(x["teamPoints"] - x["opponentPoints"]), -(x["teamPoints"] + x["opponentPoints"]), win_first(x)),
        "margin_asc": lambda x: (abs(x["teamPoints"] - x["opponentPoints"]), -(x["teamPoints"] + x["opponentPoints"]), win_first(x)),
        "total_desc": lambda x: (-(x["teamPoints"] + x["opponentPoints"]), win_first(x)),
        "total_asc": lambda x: (x["teamPoints"] + x["opponentPoints"], win_first(x)),
    }
    results.sort(key=sort_keys.get(sort, sort_keys["points_desc"]))

    return jsonify(results[:RESULT_SIZE])


CONTRIBUTOR_THRESHOLD_PCT = 10
MAX_CONTRIBUTORS = 4


def split_contributors(sorted_players, total):
    """sorted_players: [(player_id, name, points), ...] descending by points.
    Always includes the top player; additional players only if their share of the
    season's total at that position exceeds CONTRIBUTOR_THRESHOLD_PCT, capped at
    MAX_CONTRIBUTORS total. Returns (capped_list, full_list), both [{name, pct}, ...]."""
    all_contributors = []
    capped = []
    for i, (player_id, name, points) in enumerate(sorted_players):
        pct = round(points / total * 100, 1) if total else 0.0
        all_contributors.append({"name": name, "pct": pct})
        if len(capped) < MAX_CONTRIBUTORS and (i == 0 or pct > CONTRIBUTOR_THRESHOLD_PCT):
            capped.append({"name": name, "pct": pct})
    return capped, all_contributors


@app.route("/api/positions")
def positions_view():
    db = get_db()

    team_numbers = csv_ints(request.args.get("teamNumbers", "")) or list(TEAM_OWNERS.keys())
    positions = csv_strs(request.args.get("positions", "")) or ALL_POSITIONS
    start_season = int(request.args.get("startSeason", 2005))
    end_season = int(request.args.get("endSeason", 2100))
    sort = request.args.get("sort", "points_desc")

    team_placeholders = ",".join("?" * len(team_numbers))

    records = {}
    for r in db.execute(
        f"SELECT tw.team_number, g.season, tw.win, tw.loss, tw.tie FROM team_weeks tw "
        f"JOIN games g ON g.id = tw.game_id "
        f"WHERE tw.team_number IN ({team_placeholders}) AND g.season BETWEEN ? AND ?",
        (*team_numbers, start_season, end_season),
    ):
        key = (r["team_number"], r["season"])
        rec = records.setdefault(key, {"wins": 0, "losses": 0, "ties": 0})
        rec["wins"] += r["win"]
        rec["losses"] += r["loss"]
        rec["ties"] += r["tie"]

    # (team_number, season, position) -> {player_id: (name, points)}
    buckets = {}
    for r in db.execute(
        f"""
        SELECT pw.points, pw.position, p.id AS player_id, p.name, tw.team_number, g.season
        FROM player_weeks pw
        JOIN players p ON p.id = pw.player_id
        JOIN team_weeks tw ON tw.id = pw.team_week_id
        JOIN games g ON g.id = tw.game_id
        WHERE tw.team_number IN ({team_placeholders}) AND g.season BETWEEN ? AND ?
        """,
        (*team_numbers, start_season, end_season),
    ):
        key = (r["team_number"], r["season"], r["position"])
        bucket = buckets.setdefault(key, {})
        pid = r["player_id"]
        name, points = bucket.get(pid, (r["name"], 0.0))
        bucket[pid] = (name, points + r["points"])

    by_position = {pos: [] for pos in positions}
    for (team_number, season, position), players in buckets.items():
        if position not in by_position:
            continue
        sorted_players = sorted(
            ((pid, name, points) for pid, (name, points) in players.items()),
            key=lambda x: -x[2],
        )
        total = sum(p[2] for p in sorted_players)
        contributors, all_contributors = split_contributors(sorted_players, total)
        rec = records.get((team_number, season), {"wins": 0, "losses": 0, "ties": 0})
        by_position[position].append({
            "teamNumber": team_number,
            "season": season,
            "position": position,
            "points": total,
            "wins": rec["wins"],
            "losses": rec["losses"],
            "ties": rec["ties"],
            "contributors": contributors,
            "allContributors": all_contributors,
        })

    # rank is always "Nth best season at THIS position for this team-number pool" --
    # computed within each position's own group, since points aren't comparable across
    # positions (a 140-pt kicker season isn't "worse" than a 300-pt QB season).
    for rows in by_position.values():
        rows.sort(key=lambda x: (-x["points"], x["teamNumber"], x["season"]))
        for i, row in enumerate(rows):
            row["rank"] = i + 1

    # but DISPLAY is one flat, interleaved list across every selected position (no
    # per-position grouping/headers) -- secondary (team, season, position) keys just
    # make ties deterministic across repeated calls.
    def lead_pct(x):
        return x["contributors"][0]["pct"] if x["contributors"] else 0

    all_rows = [row for rows in by_position.values() for row in rows]
    sort_keys = {
        "points_desc": lambda x: (-x["points"], x["teamNumber"], x["season"], x["position"]),
        "points_asc": lambda x: (x["points"], x["teamNumber"], x["season"], x["position"]),
        "year_desc": lambda x: (-x["season"], x["teamNumber"], x["position"]),
        "year_asc": lambda x: (x["season"], x["teamNumber"], x["position"]),
        "lead_pct_desc": lambda x: (-lead_pct(x), x["teamNumber"], x["season"], x["position"]),
        "lead_pct_asc": lambda x: (lead_pct(x), x["teamNumber"], x["season"], x["position"]),
    }
    all_rows.sort(key=sort_keys.get(sort, sort_keys["points_desc"]))

    return jsonify(all_rows)


@app.route("/api/team-seasons")
def team_seasons_view():
    """Same shape and algorithm as /api/positions, but totals span a team's WHOLE
    roster for the season instead of one position -- no position grouping/column."""
    db = get_db()

    team_numbers = csv_ints(request.args.get("teamNumbers", "")) or list(TEAM_OWNERS.keys())
    start_season = int(request.args.get("startSeason", 2005))
    end_season = int(request.args.get("endSeason", 2100))
    sort = request.args.get("sort", "points_desc")

    team_placeholders = ",".join("?" * len(team_numbers))

    records = {}
    for r in db.execute(
        f"SELECT tw.team_number, g.season, tw.win, tw.loss, tw.tie FROM team_weeks tw "
        f"JOIN games g ON g.id = tw.game_id "
        f"WHERE tw.team_number IN ({team_placeholders}) AND g.season BETWEEN ? AND ?",
        (*team_numbers, start_season, end_season),
    ):
        key = (r["team_number"], r["season"])
        rec = records.setdefault(key, {"wins": 0, "losses": 0, "ties": 0})
        rec["wins"] += r["win"]
        rec["losses"] += r["loss"]
        rec["ties"] += r["tie"]

    # (team_number, season) -> {player_id: (name, points)}
    buckets = {}
    for r in db.execute(
        f"""
        SELECT pw.points, p.id AS player_id, p.name, tw.team_number, g.season
        FROM player_weeks pw
        JOIN players p ON p.id = pw.player_id
        JOIN team_weeks tw ON tw.id = pw.team_week_id
        JOIN games g ON g.id = tw.game_id
        WHERE tw.team_number IN ({team_placeholders}) AND g.season BETWEEN ? AND ?
        """,
        (*team_numbers, start_season, end_season),
    ):
        key = (r["team_number"], r["season"])
        bucket = buckets.setdefault(key, {})
        pid = r["player_id"]
        name, points = bucket.get(pid, (r["name"], 0.0))
        bucket[pid] = (name, points + r["points"])

    rows = []
    for (team_number, season), players in buckets.items():
        sorted_players = sorted(
            ((pid, name, points) for pid, (name, points) in players.items()),
            key=lambda x: -x[2],
        )
        total = sum(p[2] for p in sorted_players)
        contributors, all_contributors = split_contributors(sorted_players, total)
        rec = records.get((team_number, season), {"wins": 0, "losses": 0, "ties": 0})
        rows.append({
            "teamNumber": team_number,
            "season": season,
            "points": total,
            "wins": rec["wins"],
            "losses": rec["losses"],
            "ties": rec["ties"],
            "contributors": contributors,
            "allContributors": all_contributors,
        })

    rows.sort(key=lambda x: (-x["points"], x["teamNumber"], x["season"]))
    for i, row in enumerate(rows):
        row["rank"] = i + 1

    def lead_pct(x):
        return x["contributors"][0]["pct"] if x["contributors"] else 0

    sort_keys = {
        "points_desc": lambda x: (-x["points"], x["teamNumber"], x["season"]),
        "points_asc": lambda x: (x["points"], x["teamNumber"], x["season"]),
        "year_desc": lambda x: (-x["season"], x["teamNumber"]),
        "year_asc": lambda x: (x["season"], x["teamNumber"]),
        "lead_pct_desc": lambda x: (-lead_pct(x), x["teamNumber"], x["season"]),
        "lead_pct_asc": lambda x: (lead_pct(x), x["teamNumber"], x["season"]),
    }
    rows.sort(key=sort_keys.get(sort, sort_keys["points_desc"]))

    return jsonify(rows)


@app.route("/api/position-detail")
def position_detail():
    """position is optional: given, returns just that position's players (single
    group); omitted, returns EVERY position's players together, grouped in canonical
    position order then first appearance within each group -- used by the teams view
    to show a full roster sectioned QB/RB/WR/TE/D-ST/K like a baseball box score."""
    db = get_db()
    team_number = request.args.get("teamNumber", type=int)
    season = request.args.get("season", type=int)
    position = request.args.get("position")

    query = """
        SELECT pw.points, pw.position, g.scoring_period, p.id AS player_id, p.name, tw.win, tw.loss, tw.tie
        FROM player_weeks pw
        JOIN players p ON p.id = pw.player_id
        JOIN team_weeks tw ON tw.id = pw.team_week_id
        JOIN games g ON g.id = tw.game_id
        WHERE tw.team_number = ? AND g.season = ?
    """
    params = [team_number, season]
    if position:
        query += " AND pw.position = ?"
        params.append(position)
    query += " ORDER BY g.scoring_period"

    rows = db.execute(query, params).fetchall()

    players = {}
    first_week = {}
    for r in rows:
        pid = r["player_id"]
        if pid not in players:
            players[pid] = {"playerId": pid, "name": r["name"], "position": r["position"], "weeks": {}}
            first_week[pid] = r["scoring_period"]
        players[pid]["weeks"][r["scoring_period"]] = {
            "points": r["points"], "win": bool(r["win"]), "loss": bool(r["loss"]), "tie": bool(r["tie"]),
        }

    position_order = {pos: i for i, pos in enumerate(ALL_POSITIONS)}
    ordered = sorted(
        players.values(),
        key=lambda p: (position_order.get(p["position"], 99), first_week[p["playerId"]], p["playerId"]),
    )
    return jsonify({"players": ordered})


@app.route("/api/game/<int:season>/<int:scoring_period>/<int:team_number>")
def game_detail(season, scoring_period, team_number):
    db = get_db()

    game = db.execute(
        "SELECT id FROM games WHERE season = ? AND scoring_period = ? "
        "AND (home_team_number = ? OR away_team_number = ?)",
        (season, scoring_period, team_number, team_number),
    ).fetchone()

    if game is None:
        return jsonify(None), 404

    team_weeks = db.execute(
        "SELECT id, team_number, is_home, points FROM team_weeks WHERE game_id = ?",
        (game["id"],),
    ).fetchall()

    def side(tw):
        players = db.execute(
            "SELECT pw.player_id, pw.player_name, pw.position, pw.points "
            "FROM player_weeks pw WHERE pw.team_week_id = ?",
            (tw["id"],),
        ).fetchall()
        return {
            "teamNumber": tw["team_number"],
            "points": tw["points"],
            "players": [
                {"playerNumber": p["player_id"], "playerName": p["player_name"],
                 "position": p["position"], "points": p["points"]}
                for p in players
            ],
        }

    home = next(tw for tw in team_weeks if tw["is_home"])
    away = next(tw for tw in team_weeks if not tw["is_home"])

    return jsonify({
        "season": season,
        "scoringPeriod": scoring_period,
        "home": side(home),
        "away": side(away),
    })


if __name__ == "__main__":
    app.run(debug=True, port=8081)
