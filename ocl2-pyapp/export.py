"""
Export data/ocl.db into static JSON files under dist/data/, for a fully
static deployment (no Flask/Python needed at serve time -- dist/ is just
index.html + app.js + style.css + data/*.json, upload the whole folder
wherever wishdrops.net is hosted).

Run after etl.py whenever the db has been rebuilt:
    python etl.py && python export.py
"""
import json
import sqlite3
from pathlib import Path

from etl import TEAM_OWNERS, TEAM_OWNER_HISTORY, TEAM_GROUPS

HERE = Path(__file__).resolve().parent
DB_PATH = HERE / "data" / "ocl.db"
DIST_DATA = HERE / "dist" / "data"
ALL_POSITIONS = ["QB", "RB", "WR", "TE", "D/ST", "K"]


def main():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    DIST_DATA.mkdir(parents=True, exist_ok=True)

    min_season, max_season = conn.execute("SELECT MIN(season), MAX(season) FROM games").fetchone()
    current_game_number = conn.execute("SELECT MAX(game_number) FROM games").fetchone()[0]
    current_season, current_scoring_period = divmod(current_game_number, 100)

    # restricted to single-week games -- see app.py's ruxbee_bugton_bounds() for why
    minmax_rows = conn.execute(
        "SELECT MIN(pw.points) mn, MAX(pw.points) mx FROM player_weeks pw "
        "JOIN team_weeks tw ON tw.id = pw.team_week_id "
        "JOIN games g ON g.id = tw.game_id "
        "WHERE g.weeks_covered = 1 "
        "GROUP BY pw.team_week_id"
    ).fetchall()
    ruxbee_max = max(r["mn"] for r in minmax_rows)
    bugton_min = min(r["mx"] for r in minmax_rows)

    meta = {
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
    }
    (DIST_DATA / "meta.json").write_text(json.dumps(meta))

    games = [
        {
            "season": r["season"],
            "week": r["scoring_period"],
            "gameNumber": r["game_number"],
            "home": r["home_team_number"],
            "away": r["away_team_number"],
            "homePoints": r["home_points"],
            "awayPoints": r["away_points"],
            "weeksCovered": r["weeks_covered"],
        }
        for r in conn.execute(
            "SELECT season, scoring_period, game_number, home_team_number, away_team_number, "
            "home_points, away_points, weeks_covered FROM games"
        )
    ]
    (DIST_DATA / "games.json").write_text(json.dumps(games))

    player_weeks = [
        {
            "playerId": r["player_id"],
            "name": r["name"],
            "position": r["position"],
            "points": r["points"],
            "teamNumber": r["team_number"],
            "season": r["season"],
            "week": r["scoring_period"],
            "gameNumber": r["game_number"],
        }
        for r in conn.execute(
            """
            SELECT pw.player_id, p.name, pw.position, pw.points,
                   tw.team_number, g.season, g.scoring_period, g.game_number
            FROM player_weeks pw
            JOIN players p ON p.id = pw.player_id
            JOIN team_weeks tw ON tw.id = pw.team_week_id
            JOIN games g ON g.id = tw.game_id
            """
        )
    ]
    (DIST_DATA / "player_weeks.json").write_text(json.dumps(player_weeks))

    conn.close()

    sizes = {f.name: f.stat().st_size for f in DIST_DATA.glob("*.json")}
    total = sum(sizes.values())
    for name, size in sizes.items():
        print(f"  {name}: {size / 1024:.0f} KB")
    print(f"Total: {total / 1024:.0f} KB -> {DIST_DATA}")


if __name__ == "__main__":
    main()
