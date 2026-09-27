"""
Pull matchup/box-score data from the ESPN fantasy football v3 API and save
the raw JSON, one file per (season, matchupPeriodId), in the same shape as
the files already checked in under ocl2-job-espn/src/main/resources/matchups/.

IMPORTANT: matchupPeriodId is not always the same as the real NFL week.
Since 2021 this league's playoff rounds span 2 real weeks each (semifinal =
weeks 15+16 combined as matchupPeriodId 15, championship = weeks 17+18
combined as matchupPeriodId 16) -- ESPN calls that whole combined round a
single "matchup period". We fetch and save by matchupPeriodId. The team-level
totalPoints ESPN returns for a combined period IS the true 2-week total
(verified against pointsByScoringPeriod), but querying at any single
scoringPeriodId only returns that ONE week's player-level stats -- so for a
multi-week period we query EVERY constituent week separately and merge each
player's points across them (summing a player who was started both weeks,
keeping a player who was only started one week as-is -- e.g. a mid-round
kicker swap). That merged per-player total is what actually reconciles with
the team's true totalPoints; a single-week snapshot alone does not. Each saved
file's settings.scheduleSettings.matchupPeriods tells etl.py how many real
weeks that period actually covers.

Usage:
    python fetch_espn.py --season 2022
    python fetch_espn.py --season 2022 --periods 1-16
    python fetch_espn.py --season 2022 2023 2024 2025

Credentials come from a .env file next to this script (see .env.example).
Re-running is safe: each period is re-downloaded and overwritten, so this is
also how you do a weekly "pull down" during the season -- just re-run for
the current season (only whichever periods have new data will change).
"""
import argparse
import copy
import json
import os
import sys
import time
from pathlib import Path

import requests
from dotenv import load_dotenv

HERE = Path(__file__).resolve().parent
load_dotenv(HERE / ".env")

API_HOST = "https://lm-api-reads.fantasy.espn.com"
OUT_DIR = HERE / "data" / "raw" / "matchups"
DEFAULT_MATCHUP_PERIOD_COUNT = 16


def fetch(season, league_id, s2, swid, session, scoring_period_id=None):
    url = f"{API_HOST}/apis/v3/games/ffl/seasons/{season}/segments/0/leagues/{league_id}"
    params = [("view", "mBoxscore"), ("view", "mMatchupScore"), ("view", "mSettings")]
    if scoring_period_id is not None:
        params.append(("scoringPeriodId", scoring_period_id))

    cookies = {}
    if s2:
        cookies["espn_s2"] = s2
    if swid:
        cookies["SWID"] = swid

    resp = session.get(url, params=params, cookies=cookies, timeout=20)
    resp.raise_for_status()
    return resp.json()


def matchup_periods_map(data):
    """{matchupPeriodId (int) -> [real weeks it covers]}, defaulting to 1:1 if
    settings weren't returned for this season."""
    raw = data.get("settings", {}).get("scheduleSettings", {}).get("matchupPeriods")
    if not raw:
        return None
    return {int(k): v for k, v in raw.items()}


def roster_key(side):
    return "rosterForCurrentScoringPeriod" if "rosterForCurrentScoringPeriod" in side else "rosterForMatchupPeriod"


def merge_rosters(*rosters):
    """Combine per-week roster snapshots into one, summing a player's points across
    every week they appear in (started both weeks) and keeping a player who only
    appears in one snapshot as-is (started only one of the two weeks)."""
    by_player = {}
    for roster in rosters:
        for entry in (roster or {}).get("entries", []):
            if entry.get("lineupSlotId") in (20, 21):  # bench, IR
                continue
            pid = entry.get("playerId")
            pts = (entry.get("playerPoolEntry") or {}).get("appliedStatTotal") or 0.0
            if pid in by_player:
                pool = by_player[pid].setdefault("playerPoolEntry", {})
                pool["appliedStatTotal"] = (pool.get("appliedStatTotal") or 0.0) + pts
            else:
                by_player[pid] = copy.deepcopy(entry)
    return {"entries": list(by_player.values())}


def find_matchups(data, period):
    """A single matchupPeriodId covers every game in that period (e.g. 6 games/week in
    this league), not just one -- must merge every one of them, matched across snapshots
    by the matchup's own stable "id" field (matchupPeriodId alone is not unique per game)."""
    return {m.get("id"): m for m in data.get("schedule", []) if m.get("matchupPeriodId") == period}


def merge_multi_week_period(data, period, extra_snapshots):
    """Mutates every one of `data`'s matchup entries for `period` in place, replacing each
    side's roster with the merge of `data`'s own snapshot plus every snapshot in
    extra_snapshots (matched to the same actual game via its id)."""
    matchups = find_matchups(data, period)
    extra_matchups = [find_matchups(snap, period) for snap in extra_snapshots]

    for match_id, m in matchups.items():
        for side in ("home", "away"):
            side_obj = m.get(side) or {}
            key = roster_key(side_obj)
            rosters = [side_obj.get(key)]
            for extra in extra_matchups:
                snap_m = extra.get(match_id)
                if snap_m:
                    snap_side = snap_m.get(side) or {}
                    rosters.append(snap_side.get(roster_key(snap_side)))
            side_obj[key] = merge_rosters(*rosters)


def parse_periods(spec):
    if "-" in spec:
        lo, hi = spec.split("-", 1)
        return list(range(int(lo), int(hi) + 1))
    return [int(spec)]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--season", type=int, nargs="+", required=True, help="one or more season years")
    parser.add_argument("--periods", default=None, help="matchupPeriodId or range, e.g. '5' or '1-16' (default: full season, auto-detected)")
    parser.add_argument("--league-id", default=os.environ.get("ESPN_LEAGUE_ID"))
    args = parser.parse_args()

    s2 = os.environ.get("ESPN_S2")
    swid = os.environ.get("ESPN_SWID")
    league_id = args.league_id

    if not league_id:
        sys.exit("No league id: pass --league-id or set ESPN_LEAGUE_ID in .env")

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    session = requests.Session()

    for season in args.season:
        # one cheap request (no scoringPeriodId) just to learn this season's matchup-period layout
        try:
            probe = fetch(season, league_id, s2, swid, session)
        except requests.HTTPError as e:
            print(f"  {season}: could not fetch season settings ({e}) -- skipping")
            continue

        periods_map = matchup_periods_map(probe)
        if periods_map:
            multi_week = {k: v for k, v in periods_map.items() if len(v) > 1}
            if multi_week:
                print(f"  {season}: multi-week matchup periods detected: {multi_week}")

        if args.periods:
            periods = parse_periods(args.periods)
        elif periods_map:
            periods = sorted(periods_map.keys())
        else:
            periods = list(range(1, DEFAULT_MATCHUP_PERIOD_COUNT + 1))

        for period in periods:
            real_weeks = periods_map.get(period, [period]) if periods_map else [period]
            query_week = max(real_weeks)

            try:
                data = fetch(season, league_id, s2, swid, session, scoring_period_id=query_week)
            except requests.HTTPError as e:
                print(f"  {season} period {period}: HTTP error {e} -- skipping")
                continue

            schedule = data.get("schedule", [])
            this_period = [
                m for m in schedule
                if m.get("matchupPeriodId") == period
                and ("rosterForCurrentScoringPeriod" in m.get("home", {}) or "rosterForMatchupPeriod" in m.get("home", {}))
            ]

            if not this_period:
                print(f"  {season} period {period}: no roster data returned (period may not exist yet / bye) -- skipping")
                continue

            if len(real_weeks) > 1:
                extra_snapshots = []
                for week in real_weeks:
                    if week == query_week:
                        continue
                    try:
                        extra_snapshots.append(fetch(season, league_id, s2, swid, session, scoring_period_id=week))
                        time.sleep(0.5)
                    except requests.HTTPError as e:
                        print(f"  {season} period {period}: could not fetch week {week} to merge ({e})")
                merge_multi_week_period(data, period, extra_snapshots)

            out_path = OUT_DIR / f"{season}.{period:02d}.json"
            out_path.write_text(json.dumps(data))
            weeks_note = f" (merged real weeks {real_weeks})" if len(real_weeks) > 1 else ""
            print(f"  {season} period {period}{weeks_note}: saved {len(this_period)} matchups -> {out_path.relative_to(HERE)}")

            time.sleep(0.5)  # be polite to ESPN's API


if __name__ == "__main__":
    main()
