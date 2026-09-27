"""
Build data/ocl.db from the raw source files:

  - ocl2-job-espn/.../resources/games/*.xml       (2005-2018, one file per team per week)
  - ocl2-job-espn/.../resources/matchups/*.json   (2019-2021, ESPN v3 API dumps, checked in)
  - ocl2-pyapp/data/raw/matchups/*.json           (2022+, pulled by fetch_espn.py)

This is a full rebuild every run (drop + recreate tables) -- the source
files are the source of truth, the db is just a derived query cache.
"""
import json
import re
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent
OLD_JOB_RESOURCES = REPO_ROOT / "ocl2-job" / "ocl2-job-espn" / "src" / "main" / "resources"
GAMES_XML_DIR = OLD_JOB_RESOURCES / "games"
MATCHUPS_JSON_DIRS = [
    # freshly (re)fetched files win over the old checked-in dumps on a filename collision --
    # fetch_espn.py was fixed to correctly capture 2-week playoff rounds (see weeks_covered
    # below), so a re-pulled e.g. 2021.15.json is strictly better than the original checked-in
    # one it shares a name with.
    HERE / "data" / "raw" / "matchups",
    OLD_JOB_RESOURCES / "matchups",
]
DB_PATH = HERE / "data" / "ocl.db"

# team_number -> [(fromSeason, ownerNickname), ...], oldest first. fromSeason is always the
# NEW owner's first year. Confirmed by hand, not derivable from any source file: team 5
# rux -> baird in 2017; team 9 mbug -> paul in 2017; team 12 ruggs -> dodge in 2016; team 11
# bill -> toland in 2022 -> mbug in 2024 (mbug is the same person as team 9's original
# owner, picking up a different team-number slot years later -- TEAM_GROUPS is per-slot,
# not per-person).
TEAM_OWNER_HISTORY = {
    1: [(0, "trav")],
    2: [(0, "nick")],
    3: [(0, "jeff")],
    4: [(0, "justin")],
    5: [(0, "rux"), (2017, "baird")],
    6: [(0, "rich")],
    7: [(0, "greg")],
    8: [(0, "spoth")],
    9: [(0, "mbug"), (2017, "paul")],
    10: [(0, "argo")],
    11: [(0, "bill"), (2022, "toland"), (2024, "mbug")],
    12: [(0, "ruggs"), (2016, "dodge")],
}


def owner_for(team_number, season):
    owner = TEAM_OWNER_HISTORY[team_number][0][1]
    for from_season, name in TEAM_OWNER_HISTORY[team_number]:
        if season >= from_season:
            owner = name
    return owner


# current/latest owner per team -- used for team-select buttons and anywhere a single,
# season-independent label is needed (matches the original UI, which never showed
# historical names outside the player game-log grid and box score screens)
TEAM_OWNERS = {n: owner_for(n, 9999) for n in TEAM_OWNER_HISTORY}

# groupings from the old UI (clicking a group name filters to the union of these teams)
TEAM_GROUPS = [
    ("mason", [1, 2, 4, 9]),
    ("murph", [3, 6, 7, 11]),
    ("montosi", [5, 8, 10, 12]),
]

# hardcoded, confirmed by hand (not detectable from the XML itself, which has no
# per-sub-week breakdown): only the 2005 season used 2-week cumulative playoff
# rounds -- "period 15" is really weeks 14+15 combined, "period 17" is weeks
# 16+17 combined, which is exactly why those two weeks never appear as their own
# files. Every other pre-2018 season (2006-2018) is a normal 1:1 week mapping.
XML_MULTI_WEEK_PERIODS = {
    2005: {15, 17},
}


def xml_weeks_covered(season, scoring_period):
    return 2 if scoring_period in XML_MULTI_WEEK_PERIODS.get(season, set()) else 1

POSITION_BY_ID = {1: "QB", 2: "RB", 3: "WR", 4: "TE", 16: "D/ST", 5: "K"}
PRO_TEAM_BY_ID = {
    1: "Atl", 2: "Buf", 3: "Chi", 4: "Cin", 5: "Cle", 6: "Dal", 7: "Den", 8: "Det",
    9: "GB", 10: "Ten", 11: "Ind", 12: "KC", 13: "Oak", 14: "LAR", 15: "Mia", 16: "Min",
    17: "NE", 18: "NO", 19: "NYG", 20: "NYJ", 21: "Phi", 22: "Ari", 23: "Pit", 24: "LAC",
    25: "SF", 26: "Sea", 27: "TB", 28: "Was", 29: "Car", 30: "Jac", 33: "Bal", 34: "Hou",
}
BENCH_SLOTS = {20, 21}  # bench, IR -- excluded, matches original GameMapper.active()
KNOWN_POSITIONS = {"QB", "RB", "WR", "TE", "K", "D/ST"}


def derive_position_from_name(name):
    """~78% of pre-2018 XML files leave <player-slot/> blank. The true position is
    still recoverable as the last recognizable token in the scraped name string
    (e.g. "Drew Bennett, Ten WR" -> WR; trailing injury flags like " Q" are skipped)."""
    for token in reversed(name.split()):
        if token in KNOWN_POSITIONS:
            return token
    return ""


_POSITION_TOKEN = r"(?:QB|RB|WR|TE|K|D/ST)"
_CLEAN_NAME_RE = re.compile(r"^(.*,\s*\S+\s+" + _POSITION_TOKEN + r")\b")


def strip_injury_flag(name):
    """2017-2018 XML files append an injury-status flag after the position
    (e.g. "Marshawn Lynch, Oak RB   Q" for Questionable; also D/O/IR/SSPD seen).
    Trim anything trailing the recognized "<name>, <team> <POS>" shape. Call this
    BEFORE derive_position_from_name(), which still needs the position token."""
    m = _CLEAN_NAME_RE.match(name)
    return m.group(1) if m else name


def display_name(name):
    """Old-format names carry a trailing ", Team POS" (the position is already recorded
    separately, see derive_position_from_name()) that only ever got cleaned up if the
    player also appeared in post-2019 data, which overwrites the stored display name.
    Strip it uniformly so a player who retired before 2019 shows just as cleanly as one
    who didn't (e.g. "Tony Romo" instead of "Tony Romo, Dal QB"). D/ST names have no
    comma and pass through unchanged."""
    return name.split(",", 1)[0].strip()


def index_name(name):
    # old-format names carry a trailing ", Team POS[ injuryFlag]" (e.g. "Tom Brady, NE QB")
    # that new-format names don't ("Tom Brady") -- strip past the first comma so the same
    # player matches across the 2018/2019 source-format boundary.
    name = name.split(",", 1)[0]
    return re.sub(r"\s+", " ", re.sub(r"[^A-Za-z\s]+", "", name.lower())).strip()


class PlayerRegistry:
    """Mirrors StatsServiceImpl.attachPlayer: player identity keyed per source-era id
    scheme, falling back to a name match only when unambiguous."""

    def __init__(self, conn):
        self.conn = conn
        self.by_key = {}       # ("pre2018"|"v3", source_id) -> player_id
        self.by_name = {}      # indexed name -> set(player_id)

    def resolve(self, name, source_id, is_v3):
        scheme = "v3" if is_v3 else "pre2018"
        key = (scheme, source_id)

        player_id = self.by_key.get(key)

        if player_id is None:
            idx = index_name(name)
            candidates = self.by_name.get(idx, set())
            if len(candidates) == 1:
                player_id = next(iter(candidates))

        if player_id is None:
            cur = self.conn.execute(
                "INSERT INTO players (name, name_indexed, pre2018_player_number, player_number) VALUES (?, ?, ?, ?)",
                (name, index_name(name), source_id if not is_v3 else None, source_id if is_v3 else None),
            )
            player_id = cur.lastrowid
        else:
            self.conn.execute(
                "UPDATE players SET name = ?, name_indexed = ?, "
                + ("player_number = ?" if is_v3 else "pre2018_player_number = ?")
                + " WHERE id = ?",
                (name, index_name(name), source_id, player_id),
            )

        self.by_key[key] = player_id
        self.by_name.setdefault(index_name(name), set()).add(player_id)
        return player_id


def create_schema(conn):
    conn.executescript(
        """
        DROP TABLE IF EXISTS player_weeks;
        DROP TABLE IF EXISTS team_weeks;
        DROP TABLE IF EXISTS games;
        DROP TABLE IF EXISTS players;
        DROP TABLE IF EXISTS teams;

        CREATE TABLE teams (
            team_number INTEGER PRIMARY KEY,
            owner TEXT NOT NULL
        );

        CREATE TABLE players (
            id INTEGER PRIMARY KEY,
            name TEXT NOT NULL,
            name_indexed TEXT NOT NULL,
            pre2018_player_number INTEGER,
            player_number INTEGER
        );
        CREATE INDEX idx_players_name_indexed ON players(name_indexed);
        CREATE INDEX idx_players_pre2018_number ON players(pre2018_player_number);
        CREATE INDEX idx_players_player_number ON players(player_number);

        CREATE TABLE games (
            id INTEGER PRIMARY KEY,
            season INTEGER NOT NULL,
            scoring_period INTEGER NOT NULL,
            game_number INTEGER NOT NULL,
            home_team_number INTEGER,
            away_team_number INTEGER,
            home_points REAL,
            away_points REAL,
            weeks_covered INTEGER NOT NULL DEFAULT 1
        );
        CREATE INDEX idx_games_number ON games(game_number);
        CREATE INDEX idx_games_season_period ON games(season, scoring_period);

        CREATE TABLE team_weeks (
            id INTEGER PRIMARY KEY,
            game_id INTEGER NOT NULL REFERENCES games(id),
            team_number INTEGER,
            header TEXT,
            is_home INTEGER NOT NULL,
            points REAL NOT NULL,
            win INTEGER NOT NULL,
            loss INTEGER NOT NULL,
            tie INTEGER NOT NULL
        );
        CREATE INDEX idx_team_weeks_game ON team_weeks(game_id);
        CREATE INDEX idx_team_weeks_team ON team_weeks(team_number);

        CREATE TABLE player_weeks (
            id INTEGER PRIMARY KEY,
            team_week_id INTEGER NOT NULL REFERENCES team_weeks(id),
            player_id INTEGER NOT NULL REFERENCES players(id),
            player_name TEXT,
            position TEXT,
            player_pro_team TEXT,
            opponent TEXT,
            game_status TEXT,
            points REAL NOT NULL
        );
        CREATE INDEX idx_player_weeks_team_week ON player_weeks(team_week_id);
        CREATE INDEX idx_player_weeks_player ON player_weeks(player_id);
        """
    )
    conn.executemany(
        "INSERT INTO teams (team_number, owner) VALUES (?, ?)",
        list(TEAM_OWNERS.items()),
    )


# ---------------------------------------------------------------------------
# old XML format (2005-2018), one file per team per week
# ---------------------------------------------------------------------------

def parse_xml_games():
    """Yields dicts: {season, scoring_period, home_number, home_header, home_players,
    away_header, away_players} -- one per file. away_number is resolved in a second pass."""
    for path in sorted(GAMES_XML_DIR.glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            print(f"  skipping unparseable file {path.name}")
            continue

        season = int(root.get("season"))
        scoring_period = int(root.get("scoringPeriod"))
        home_number = int(root.get("team"))

        teams = root.findall("team")
        if len(teams) != 2:
            continue

        def team_players(team_el):
            players = []
            for p in team_el.findall("players/player"):
                pid_raw = p.get("id") or ""
                m = re.search(r"\d+", pid_raw)
                if not m:
                    continue
                points_text = (p.findtext("points") or "0").strip()
                try:
                    points = float(points_text)
                except ValueError:
                    points = 0.0
                raw_name = strip_injury_flag((p.findtext("player-name") or "").strip())
                slot = (p.findtext("player-slot") or "").strip()
                players.append({
                    "source_id": int(m.group()),
                    "name": display_name(raw_name),
                    "position": slot or derive_position_from_name(raw_name),
                    "opponent": (p.findtext("opponent") or "").strip(),
                    "game_status": (p.findtext("game-status") or "").strip(),
                    "points": points,
                })
            return players

        yield {
            "season": season,
            "scoring_period": scoring_period,
            "home_number": home_number,
            "home_header": (teams[0].findtext("header") or "").strip(),
            "home_players": team_players(teams[0]),
            "away_header": (teams[1].findtext("header") or "").strip(),
            "away_players": team_players(teams[1]),
        }


def load_xml_games(conn, registry):
    files = list(parse_xml_games())

    # first pass: map header text -> team number, and group files by (season, scoring_period)
    header_to_number = {}
    by_week = {}
    for f in files:
        header_to_number[f["home_header"]] = f["home_number"]
        by_week.setdefault((f["season"], f["scoring_period"]), []).append(f)

    seen_pairs = set()
    inserted = 0

    for (season, scoring_period), week_files in by_week.items():
        for f in week_files:
            away_number = header_to_number.get(f["away_header"])
            pair_key = (season, scoring_period, frozenset([f["home_header"], f["away_header"]]))
            if pair_key in seen_pairs:
                continue
            seen_pairs.add(pair_key)

            insert_game(
                conn, registry,
                season=season, scoring_period=scoring_period, weeks_covered=xml_weeks_covered(season, scoring_period),
                home_number=f["home_number"], home_header=f["home_header"], home_players=f["home_players"],
                away_number=away_number, away_header=f["away_header"], away_players=f["away_players"],
                is_v3=False,
            )
            inserted += 1

    print(f"  loaded {len(files)} xml files -> {inserted} games")


# ---------------------------------------------------------------------------
# ESPN v3 JSON format (2019+)
# ---------------------------------------------------------------------------

def parse_json_matchups():
    seen_files = set()
    for d in MATCHUPS_JSON_DIRS:
        if not d.exists():
            continue
        for path in sorted(d.glob("*.json")):
            if path.name in seen_files:
                continue
            seen_files.add(path.name)
            try:
                data = json.loads(path.read_text())
            except json.JSONDecodeError:
                print(f"  skipping unparseable file {path}")
                continue
            season = data.get("seasonId")
            matchup_periods = data.get("settings", {}).get("scheduleSettings", {}).get("matchupPeriods") or {}
            for m in data.get("schedule", []):
                week = m.get("matchupPeriodId")
                weeks_covered = len(matchup_periods.get(str(week), [week]))
                home = m.get("home") or {}
                away = m.get("away") or {}
                home_roster = home.get("rosterForCurrentScoringPeriod") or home.get("rosterForMatchupPeriod")
                away_roster = away.get("rosterForCurrentScoringPeriod") or away.get("rosterForMatchupPeriod")
                if not home_roster or not away_roster:
                    continue

                # ESPN's totalPoints is authoritative for combined 2-week playoff rounds (verified
                # against pointsByScoringPeriod), where individual per-sub-week player attribution is
                # NOT reliable. But for normal single-week games, some older files (2019 wk 7-8, 2020
                # wk 9/10/12) have totalPoints stuck at 0 even though the roster's individual player
                # points are correct -- for those, summing the (already-correct) player points is
                # strictly more trustworthy than trusting totalPoints.
                home_points = home.get("totalPoints") if weeks_covered > 1 else None
                away_points = away.get("totalPoints") if weeks_covered > 1 else None

                yield {
                    "season": season,
                    "scoring_period": week,
                    "weeks_covered": weeks_covered,
                    "home_number": home.get("teamId"),
                    "home_points": home_points,
                    "home_players": v3_players(home_roster),
                    "away_number": away.get("teamId"),
                    "away_points": away_points,
                    "away_players": v3_players(away_roster),
                }


def v3_players(roster):
    players = []
    for entry in roster.get("entries", []):
        if entry.get("lineupSlotId") in BENCH_SLOTS:
            continue
        pool_entry = entry.get("playerPoolEntry") or {}
        player = pool_entry.get("player") or {}
        players.append({
            "source_id": entry.get("playerId") or pool_entry.get("id") or player.get("id"),
            "name": player.get("fullName", ""),
            "position": POSITION_BY_ID.get(player.get("defaultPositionId"), "?"),
            "opponent": "",
            "game_status": "",
            "points": pool_entry.get("appliedStatTotal") or 0.0,
        })
    return players


def load_json_matchups(conn, registry):
    count = 0
    seen_pairs = set()
    for m in parse_json_matchups():
        pair_key = (m["season"], m["scoring_period"], frozenset([m["home_number"], m["away_number"]]))
        if pair_key in seen_pairs:
            continue
        seen_pairs.add(pair_key)

        insert_game(
            conn, registry,
            season=m["season"], scoring_period=m["scoring_period"], weeks_covered=m["weeks_covered"],
            home_number=m["home_number"], home_header=None, home_players=m["home_players"],
            away_number=m["away_number"], away_header=None, away_players=m["away_players"],
            is_v3=True,
            home_points_override=m["home_points"], away_points_override=m["away_points"],
        )
        count += 1
    print(f"  loaded json matchups -> {count} games")


# ---------------------------------------------------------------------------
# shared insert logic
# ---------------------------------------------------------------------------

def insert_game(conn, registry, *, season, scoring_period, home_number, home_header, home_players,
                away_number, away_header, away_players, is_v3, weeks_covered=1,
                home_points_override=None, away_points_override=None):

    home_points = home_points_override if home_points_override is not None else sum(p["points"] for p in home_players)
    away_points = away_points_override if away_points_override is not None else sum(p["points"] for p in away_players)

    game_number = season * 100 + scoring_period

    cur = conn.execute(
        "INSERT INTO games (season, scoring_period, game_number, home_team_number, away_team_number, "
        "home_points, away_points, weeks_covered) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (season, scoring_period, game_number, home_number, away_number, home_points, away_points, weeks_covered),
    )
    game_id = cur.lastrowid

    if home_points > away_points:
        home_wlt = (1, 0, 0)
        away_wlt = (0, 1, 0)
    elif away_points > home_points:
        home_wlt = (0, 1, 0)
        away_wlt = (1, 0, 0)
    else:
        home_wlt = away_wlt = (0, 0, 1)

    home_tw_id = insert_team_week(conn, game_id, home_number, home_header, True, home_points, home_wlt)
    away_tw_id = insert_team_week(conn, game_id, away_number, away_header, False, away_points, away_wlt)

    for tw_id, players in ((home_tw_id, home_players), (away_tw_id, away_players)):
        for p in players:
            if not p["name"] or p["source_id"] is None:
                continue
            player_id = registry.resolve(p["name"], p["source_id"], is_v3)
            conn.execute(
                "INSERT INTO player_weeks (team_week_id, player_id, player_name, position, player_pro_team, "
                "opponent, game_status, points) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (tw_id, player_id, p["name"], p["position"], p.get("player_pro_team"), p["opponent"],
                 p["game_status"], p["points"]),
            )


def insert_team_week(conn, game_id, team_number, header, is_home, points, wlt):
    win, loss, tie = wlt
    cur = conn.execute(
        "INSERT INTO team_weeks (game_id, team_number, header, is_home, points, win, loss, tie) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        (game_id, team_number, header, int(is_home), points, win, loss, tie),
    )
    return cur.lastrowid


def main():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    if DB_PATH.exists():
        DB_PATH.unlink()

    conn = sqlite3.connect(DB_PATH)
    conn.execute("PRAGMA foreign_keys = OFF")  # bulk load, order isn't guaranteed row-by-row

    create_schema(conn)
    registry = PlayerRegistry(conn)

    print("Loading old XML box scores (2005-2018)...")
    load_xml_games(conn, registry)

    print("Loading ESPN v3 JSON matchups (2019+)...")
    load_json_matchups(conn, registry)

    conn.commit()

    n_games, n_pw, n_players = conn.execute(
        "SELECT (SELECT COUNT(*) FROM games), (SELECT COUNT(*) FROM player_weeks), (SELECT COUNT(*) FROM players)"
    ).fetchone()
    seasons = conn.execute("SELECT MIN(season), MAX(season) FROM games").fetchone()
    print(f"Done: {n_games} games, {n_pw} player-weeks, {n_players} distinct players, seasons {seasons[0]}-{seasons[1]}")

    conn.close()


if __name__ == "__main__":
    main()
