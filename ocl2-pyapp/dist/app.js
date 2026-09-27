// Fully static/client-side build: no server, all data loaded once from data/*.json
// into in-memory arrays/maps and queried in JS. See ../export.py for how those
// JSON files get (re)generated from data/ocl.db.

const TEAM_COLORS = {
    1: "#797979", 2: "#9a7a7a", 3: "#3f7f7f", 4: "#d6c8a3",
    5: "#495e7e", 6: "#757a2a", 7: "#a6e0ce", 8: "#c57a9a",
    9: "#cfa68e", 10: "#d498ed", 11: "#56b365", 12: "#8a9ac5",
};
const RESULT_SIZE = 100;

let META = null;
let GAMES = null;          // array of {season, week, gameNumber, home, away, homePoints, awayPoints}
let PLAYER_WEEKS = null;   // array of {playerId, name, position, points, teamNumber, season, week, gameNumber}
let GAME_TEAM_INFO = null; // Map "gameNumber:teamNumber" -> {points, win, loss, tie, opponentTeamNumber, opponentPoints, weeksCovered}
let WEEKS_BY_PLAYER = null;// Map playerId -> [player_week rows]
let PLAYERS_BY_GAME_TEAM = null; // Map "gameNumber:teamNumber" -> [player_week rows]

let mode = "players"; // "players" | "games"
let selectedTeams = null; // null = all
let selectedPositions = new Set();

async function getJSON(url) {
    const resp = await fetch(url);
    if (!resp.ok) return null;
    return resp.json();
}

function ownerName(teamNumber, season) {
    const history = META.ownerHistory[teamNumber];
    if (!history) return "?";
    const s = season == null ? META.currentSeason : season;
    let owner = history[0].owner;
    for (const entry of history) {
        if (s >= entry.fromSeason) owner = entry.owner;
    }
    return owner;
}

function buildIndices() {
    GAME_TEAM_INFO = new Map();
    for (const g of GAMES) {
        const homeWlt = g.homePoints > g.awayPoints ? [1, 0, 0] : g.awayPoints > g.homePoints ? [0, 1, 0] : [0, 0, 1];
        const awayWlt = g.homePoints > g.awayPoints ? [0, 1, 0] : g.awayPoints > g.homePoints ? [1, 0, 0] : [0, 0, 1];
        GAME_TEAM_INFO.set(g.gameNumber + ":" + g.home, {
            points: g.homePoints, win: homeWlt[0], loss: homeWlt[1], tie: homeWlt[2],
            opponentTeamNumber: g.away, opponentPoints: g.awayPoints, weeksCovered: g.weeksCovered,
        });
        GAME_TEAM_INFO.set(g.gameNumber + ":" + g.away, {
            points: g.awayPoints, win: awayWlt[0], loss: awayWlt[1], tie: awayWlt[2],
            opponentTeamNumber: g.home, opponentPoints: g.homePoints, weeksCovered: g.weeksCovered,
        });
    }

    WEEKS_BY_PLAYER = new Map();
    PLAYERS_BY_GAME_TEAM = new Map();
    for (const pw of PLAYER_WEEKS) {
        if (!WEEKS_BY_PLAYER.has(pw.playerId)) WEEKS_BY_PLAYER.set(pw.playerId, []);
        WEEKS_BY_PLAYER.get(pw.playerId).push(pw);

        const key = pw.gameNumber + ":" + pw.teamNumber;
        if (!PLAYERS_BY_GAME_TEAM.has(key)) PLAYERS_BY_GAME_TEAM.set(key, []);
        PLAYERS_BY_GAME_TEAM.get(key).push(pw);
    }
}

function gameNumber(season, week) {
    return season * 100 + week;
}

function pointsPerTeam(rows) {
    const totals = new Map();
    for (const r of rows) totals.set(r.teamNumber, (totals.get(r.teamNumber) || 0) + r.points);
    return Array.from(totals, ([teamNumber, points]) => ({ teamNumber, points }))
        .sort((a, b) => b.points - a.points);
}

// -- ports of the three Flask endpoints, now querying in-memory arrays --

function queryPlayersPoints({ teamNumbers, positions, startSeason, endSeason, startWeek = 1, endWeek = 17 }) {
    const teams = teamNumbers && teamNumbers.length ? new Set(teamNumbers) : new Set(META.teams.map(t => t.teamNumber));
    const posSet = positions && positions.length ? new Set(positions) : new Set(META.positions);
    const startGame = gameNumber(startSeason, startWeek);
    const endGame = gameNumber(endSeason, endWeek);
    const currentGame = gameNumber(META.currentSeason, META.currentScoringPeriod);

    const byPlayer = new Map();
    for (const pw of PLAYER_WEEKS) {
        if (!teams.has(pw.teamNumber)) continue;
        if (!posSet.has(pw.position)) continue;
        if (pw.gameNumber < startGame || pw.gameNumber > endGame) continue;
        if (!byPlayer.has(pw.playerId)) byPlayer.set(pw.playerId, []);
        byPlayer.get(pw.playerId).push(pw);
    }

    const results = [];
    for (const [playerId, weeks] of byPlayer) {
        const info = weeks.map(w => GAME_TEAM_INFO.get(w.gameNumber + ":" + w.teamNumber));
        const currentWeeks = weeks.filter(w => w.gameNumber === currentGame);
        const currentPpt = currentWeeks.length ? pointsPerTeam(currentWeeks)[0] : null;
        results.push({
            playerId,
            name: weeks[0].name,
            position: weeks[0].position,
            points: weeks.reduce((s, w) => s + w.points, 0),
            games: weeks.length,
            wins: info.reduce((s, i) => s + i.win, 0),
            losses: info.reduce((s, i) => s + i.loss, 0),
            ties: info.reduce((s, i) => s + i.tie, 0),
            pointsPerTeam: pointsPerTeam(weeks),
            currentPointsPerTeam: currentPpt,
        });
    }

    results.sort((a, b) => b.points - a.points);
    return results.slice(0, RESULT_SIZE);
}

function queryPlayerDetail(playerId) {
    const weeks = (WEEKS_BY_PLAYER.get(playerId) || []).slice().sort((a, b) => a.gameNumber - b.gameNumber);
    if (!weeks.length) return null;

    const gameStats = weeks.map(w => {
        const info = GAME_TEAM_INFO.get(w.gameNumber + ":" + w.teamNumber);
        return {
            season: w.season,
            scoringPeriod: w.week,
            teamNumber: w.teamNumber,
            teamPoints: info.points,
            opponentTeamNumber: info.opponentTeamNumber,
            opponentPoints: info.opponentPoints,
            points: w.points,
        };
    });

    return {
        playerId,
        name: weeks[weeks.length - 1].name,
        position: weeks[0].position,
        points: weeks.reduce((s, w) => s + w.points, 0),
        games: weeks.length,
        wins: gameStats.filter(g => g.teamPoints > g.opponentPoints).length,
        losses: gameStats.filter(g => g.opponentPoints > g.teamPoints).length,
        ties: gameStats.filter(g => g.teamPoints === g.opponentPoints).length,
        gameStats,
    };
}

function queryGameDetail(season, scoringPeriod, teamNumber) {
    const game = GAMES.find(g => g.season === season && g.week === scoringPeriod &&
        (g.home === teamNumber || g.away === teamNumber));
    if (!game) return null;

    function side(sideTeamNumber, points) {
        const players = PLAYER_WEEKS.filter(pw =>
            pw.season === season && pw.week === scoringPeriod && pw.teamNumber === sideTeamNumber);
        return {
            teamNumber: sideTeamNumber,
            points,
            players: players.map(p => ({
                playerNumber: p.playerId, playerName: p.name, position: p.position, points: p.points,
            })),
        };
    }

    return {
        season, scoringPeriod,
        home: side(game.home, game.homePoints),
        away: side(game.away, game.awayPoints),
    };
}

function playersFor(gameNumber, teamNumber) {
    return (PLAYERS_BY_GAME_TEAM.get(gameNumber + ":" + teamNumber) || []).map(p => ({
        playerNumber: p.playerId, playerName: p.name, position: p.position, points: p.points,
    }));
}

function queryGamesList({ teamNumbers, startSeason, endSeason, startWeek = 1, endWeek = 17, outcome, ruxbee, bugton, sort, includeMultiWeek }) {
    const teams = teamNumbers && teamNumbers.length ? new Set(teamNumbers) : new Set(META.teams.map(t => t.teamNumber));
    const startGame = gameNumber(startSeason, startWeek);
    const endGame = gameNumber(endSeason, endWeek);

    const seen = new Set(); // gameNumber:teamNumber, dedupe since PLAYER_WEEKS has one row per player
    const results = [];

    for (const pw of PLAYER_WEEKS) {
        if (!teams.has(pw.teamNumber)) continue;
        if (pw.gameNumber < startGame || pw.gameNumber > endGame) continue;
        const key = pw.gameNumber + ":" + pw.teamNumber;
        if (seen.has(key)) continue;
        seen.add(key);

        const info = GAME_TEAM_INFO.get(key);
        if (!includeMultiWeek && info.weeksCovered > 1) continue;
        if (outcome === "win" && !info.win) continue;
        if (outcome === "loss" && !info.loss) continue;
        if (outcome === "tie" && !info.tie) continue;

        const players = playersFor(pw.gameNumber, pw.teamNumber);
        const opponentPlayers = playersFor(pw.gameNumber, info.opponentTeamNumber);
        const minPts = Math.min(...players.map(p => p.points));
        const maxPts = Math.max(...players.map(p => p.points));

        if (ruxbee != null && minPts < ruxbee) continue;
        if (bugton != null && maxPts > bugton) continue;

        results.push({
            season: pw.season,
            scoringPeriod: pw.week,
            weeksCovered: info.weeksCovered,
            teamNumber: pw.teamNumber,
            teamPoints: info.points,
            win: !!info.win, loss: !!info.loss, tie: !!info.tie,
            opponentTeamNumber: info.opponentTeamNumber,
            opponentPoints: info.opponentPoints,
            minPlayerPoints: minPts,
            maxPlayerPoints: maxPts,
            players, opponentPlayers,
        });
    }

    // both margin sorts use the ABSOLUTE margin (most = biggest blowouts, least = closest
    // games/ties, either direction) so a game's two team-perspective rows land right next to
    // each other -- then a tertiary win-before-loss key puts the winner's row on top of the
    // pair (ties keep either order, there's no winner). Secondary: total points descending --
    // how high were the highest ties, then the highest 1-pt wins, etc.
    const totalDesc = (a, b) => (b.teamPoints + b.opponentPoints) - (a.teamPoints + a.opponentPoints);
    const winFirst = (a, b) => (a.win ? 0 : 1) - (b.win ? 0 : 1);
    const sortKeys = {
        points_desc: (a, b) => b.teamPoints - a.teamPoints,
        points_asc: (a, b) => a.teamPoints - b.teamPoints,
        margin_desc: (a, b) => (Math.abs(b.teamPoints - b.opponentPoints) - Math.abs(a.teamPoints - a.opponentPoints)) || totalDesc(a, b) || winFirst(a, b),
        margin_asc: (a, b) => (Math.abs(a.teamPoints - a.opponentPoints) - Math.abs(b.teamPoints - b.opponentPoints)) || totalDesc(a, b) || winFirst(a, b),
        total_desc: (a, b) => totalDesc(a, b) || winFirst(a, b),
        total_asc: (a, b) => ((a.teamPoints + a.opponentPoints) - (b.teamPoints + b.opponentPoints)) || winFirst(a, b),
    };
    results.sort(sortKeys[sort] || sortKeys.points_desc);
    return results.slice(0, RESULT_SIZE);
}

// -- UI (unchanged from the Flask-backed build) --

function buildTeamButtons() {
    const container = document.getElementById("teams");

    for (const group of META.groups) {
        const groupDiv = document.createElement("div");
        groupDiv.className = "clickable group-header";
        groupDiv.textContent = group.name;
        groupDiv.onclick = () => selectTeams(group.teamNumbers, group.name);
        container.appendChild(groupDiv);

        for (const teamNumber of group.teamNumbers) {
            container.appendChild(buildOwnerButton(teamNumber));
        }
    }

    document.getElementById("all-teams").onclick = () => selectTeams(null, "all");
}

// [{owner, startSeason, endSeason}, ...] -- one entry per owner era for this team-number slot
function ownerEras(teamNumber) {
    const history = META.ownerHistory[teamNumber];
    return history.map((entry, i) => ({
        owner: entry.owner,
        startSeason: i === 0 ? META.minSeason : entry.fromSeason,
        endSeason: i + 1 < history.length ? history[i + 1].fromSeason - 1 : META.maxSeason,
    }));
}

// shrinks the current season-range selects to fit within an owner era, but leaves them
// alone if they're already a subset of it (only widens/narrows what's actually outside)
function clampSeasonRangeToEra(era) {
    const startSel = document.getElementById("start-season");
    const endSel = document.getElementById("end-season");
    const curStart = Number(startSel.value);
    const curEnd = Number(endSel.value);

    let newStart = Math.max(curStart, era.startSeason);
    let newEnd = Math.min(curEnd, era.endSeason);
    if (newStart > newEnd) {
        newStart = era.startSeason;
        newEnd = era.endSeason;
    }

    startSel.value = newStart;
    endSel.value = newEnd;
}

// a single shared dropdown menu, positioned via the clicked button's actual screen
// coordinates each time it opens -- avoids relying on position:absolute inside the
// team panel's CSS multi-column layout, which doesn't reliably anchor positioned
// descendants to their containing block across browsers.
let ownerMenuEl = null;
let ownerMenuOpenFor = null;

function getOwnerMenuEl() {
    if (!ownerMenuEl) {
        ownerMenuEl = document.createElement("div");
        ownerMenuEl.className = "owner-dropdown-menu";
        ownerMenuEl.style.display = "none";
        document.body.appendChild(ownerMenuEl);
    }
    return ownerMenuEl;
}

function closeAllOwnerDropdowns() {
    if (ownerMenuEl) ownerMenuEl.style.display = "none";
    ownerMenuOpenFor = null;
}

function toggleOwnerMenu(teamNumber, anchorEl, eras, comboLabel) {
    const menu = getOwnerMenuEl();
    const alreadyOpenForThis = ownerMenuOpenFor === teamNumber && menu.style.display !== "none";
    closeAllOwnerDropdowns();
    if (alreadyOpenForThis) return;

    menu.innerHTML = "";

    const comboItem = document.createElement("div");
    comboItem.className = "owner-dropdown-item";
    comboItem.textContent = "combined (all years)";
    comboItem.onclick = (e) => {
        e.stopPropagation();
        closeAllOwnerDropdowns();
        selectTeams([teamNumber], comboLabel);
    };
    menu.appendChild(comboItem);

    eras.forEach(era => {
        const item = document.createElement("div");
        item.className = "owner-dropdown-item";
        item.textContent = era.owner + " (" + era.startSeason + "-" + era.endSeason + ")";
        item.onclick = (e) => {
            e.stopPropagation();
            closeAllOwnerDropdowns();
            clampSeasonRangeToEra(era);
            selectTeams([teamNumber], era.owner);
        };
        menu.appendChild(item);
    });

    const rect = anchorEl.getBoundingClientRect();
    menu.style.left = rect.left + "px";
    menu.style.top = rect.bottom + "px";
    menu.style.minWidth = rect.width + "px";
    menu.style.display = "block";
    ownerMenuOpenFor = teamNumber;
}

// a plain colored button for single-owner teams; for teams whose team-number slot has
// changed hands, a "owner1/owner2" label with a small dropdown to restrict the season
// range to one era or view the combined (default) full history
function buildOwnerButton(teamNumber) {
    const eras = ownerEras(teamNumber);
    const comboLabel = eras.map(e => e.owner).join("/");

    const div = document.createElement("div");
    div.className = "clickable";
    div.style.backgroundColor = TEAM_COLORS[teamNumber];

    if (eras.length === 1) {
        div.textContent = comboLabel;
        div.onclick = () => selectTeams([teamNumber], comboLabel);
        return div;
    }

    div.onclick = () => selectTeams([teamNumber], comboLabel);

    const label = document.createElement("span");
    label.textContent = comboLabel;
    div.appendChild(label);

    const arrow = document.createElement("span");
    arrow.className = "owner-dropdown-arrow";
    arrow.textContent = " ▾";
    arrow.onclick = (e) => {
        e.stopPropagation();
        toggleOwnerMenu(teamNumber, div, eras, comboLabel);
    };
    div.appendChild(arrow);

    return div;
}

function buildPositionCheckboxes() {
    const container = document.getElementById("positions");
    for (const pos of META.positions) {
        const label = document.createElement("label");
        const input = document.createElement("input");
        input.type = "checkbox";
        input.onclick = () => {
            if (selectedPositions.has(pos)) selectedPositions.delete(pos);
            else selectedPositions.add(pos);
            refresh();
        };
        label.appendChild(input);
        label.appendChild(document.createTextNode(" " + pos));
        container.appendChild(label);
    }
}

function buildSeasonSelects() {
    const startSel = document.getElementById("start-season");
    const endSel = document.getElementById("end-season");
    for (let s = META.minSeason; s <= META.maxSeason; s++) {
        startSel.appendChild(new Option(s, s));
        endSel.appendChild(new Option(s, s));
    }
    startSel.value = Math.max(META.minSeason, 2006);
    endSel.value = META.maxSeason;
    startSel.onchange = refresh;
    endSel.onchange = refresh;
}

function buildGamesFilters() {
    const ruxbeeSel = document.getElementById("ruxbee-filter");
    ruxbeeSel.appendChild(new Option("none", ""));
    for (let n = 7; n <= META.ruxbeeMax; n++) ruxbeeSel.appendChild(new Option(n, n));

    const bugtonSel = document.getElementById("bugton-filter");
    bugtonSel.appendChild(new Option("none", ""));
    for (let n = META.bugtonMin; n <= META.bugtonMin + 5; n++) bugtonSel.appendChild(new Option(n, n));

    document.getElementById("outcome-filter").onchange = refresh;
    ruxbeeSel.onchange = refresh;
    bugtonSel.onchange = refresh;
    document.getElementById("multi-week-filter").onchange = refresh;
    document.getElementById("sort-filter").onchange = refresh;
}

function setMode(newMode) {
    mode = newMode;
    document.getElementById("mode-players").classList.toggle("active", mode === "players");
    document.getElementById("mode-games").classList.toggle("active", mode === "games");
    document.getElementById("players-filters").style.display = mode === "players" ? "" : "none";
    document.getElementById("games-filters").style.display = mode === "games" ? "" : "none";
    document.getElementById("results").style.display = mode === "players" ? "" : "none";
    document.getElementById("games-results").style.display = mode === "games" ? "" : "none";
    refresh();
}

function refresh() {
    if (mode === "players") loadStats();
    else loadGames();
}

function selectTeams(teamNumbers, label) {
    selectedTeams = teamNumbers;
    document.getElementById("team-name").textContent = label;
    refresh();
}

function loadStats() {
    const stats = queryPlayersPoints({
        teamNumbers: selectedTeams,
        positions: Array.from(selectedPositions),
        startSeason: Number(document.getElementById("start-season").value),
        endSeason: Number(document.getElementById("end-season").value),
    });
    renderStats(stats);
}

function loadGames() {
    const games = queryGamesList({
        teamNumbers: selectedTeams,
        startSeason: Number(document.getElementById("start-season").value),
        endSeason: Number(document.getElementById("end-season").value),
        outcome: document.getElementById("outcome-filter").value || null,
        ruxbee: document.getElementById("ruxbee-filter").value ? Number(document.getElementById("ruxbee-filter").value) : null,
        bugton: document.getElementById("bugton-filter").value ? Number(document.getElementById("bugton-filter").value) : null,
        includeMultiWeek: document.getElementById("multi-week-filter").checked,
        sort: document.getElementById("sort-filter").value,
    });
    renderGames(games);
}

function ownerChip(teamNumber, season) {
    const span = document.createElement("span");
    span.className = "owner-chip";
    const swatch = document.createElement("span");
    swatch.className = "owner-chip-swatch";
    swatch.style.backgroundColor = TEAM_COLORS[teamNumber];
    span.appendChild(swatch);
    span.appendChild(document.createTextNode(ownerName(teamNumber, season)));
    return span;
}

function renderGames(games) {
    const container = document.getElementById("games-results");
    container.innerHTML = "";

    games.forEach((g, index) => {
        const details = document.createElement("details");
        details.className = "game-entry";

        const summary = document.createElement("summary");

        const matchupCol = document.createElement("div");
        matchupCol.className = "game-summary-col game-summary-matchup";
        const rank = document.createElement("span");
        rank.className = "game-summary-rank";
        rank.textContent = (index + 1) + ". ";
        matchupCol.appendChild(rank);
        matchupCol.appendChild(ownerChip(g.teamNumber, g.season));
        matchupCol.appendChild(document.createTextNode(" vs "));
        matchupCol.appendChild(ownerChip(g.opponentTeamNumber, g.season));
        summary.appendChild(matchupCol);

        const weekLabel = g.season + " wk" + g.scoringPeriod + (g.weeksCovered > 1 ? " (2wk)" : "");
        summary.appendChild(summaryCol(weekLabel));

        const outcome = g.win ? "W" : g.loss ? "L" : "T";
        summary.appendChild(summaryCol(g.teamPoints + "-" + g.opponentPoints + " (" + outcome + ")"));

        details.appendChild(summary);

        let built = false;
        details.addEventListener("toggle", () => {
            if (details.open && !built) {
                built = true;
                const box = document.createElement("div");
                box.className = "game-entry-box";
                box.appendChild(buildGameTeamPanel({ teamNumber: g.teamNumber, points: g.teamPoints, players: g.players }, g.season));
                box.appendChild(buildGameTeamPanel({ teamNumber: g.opponentTeamNumber, points: g.opponentPoints, players: g.opponentPlayers }, g.season));
                details.appendChild(box);
            }
        });

        container.appendChild(details);
    });
}

function summaryCol(text) {
    const div = document.createElement("div");
    div.className = "game-summary-col";
    div.textContent = text;
    return div;
}

function renderStats(stats) {
    const container = document.getElementById("results");
    container.innerHTML = "";
    const maxPoints = stats.length ? stats[0].points : 1;

    stats.forEach((stat, index) => {
        const row = document.createElement("div");
        row.className = "stat-row";
        row.onclick = () => showPlayer(stat.playerId);

        const rank = col(String(index + 1) + ".", "stat-rank");
        const position = col(stat.position, "");
        const name = col(stat.name, "");
        const points = col(String(stat.points), "");
        const record = col(record_(stat), "");
        const current = col("", "stat-graph-active");
        if (stat.currentPointsPerTeam) {
            current.style.backgroundColor = TEAM_COLORS[stat.currentPointsPerTeam.teamNumber];
            current.title = ownerName(stat.currentPointsPerTeam.teamNumber) + ": " + stat.currentPointsPerTeam.points;
        }

        const graph = col("", "stat-graph");
        for (const ppt of stat.pointsPerTeam) {
            const bar = document.createElement("div");
            bar.className = "stat-graph-component";
            bar.style.backgroundColor = TEAM_COLORS[ppt.teamNumber];
            bar.style.width = (100 * ppt.points / maxPoints) + "%";
            bar.title = ownerName(ppt.teamNumber) + ": " + ppt.points;
            bar.innerHTML = "&nbsp;";
            graph.appendChild(bar);
        }

        [rank, position, name, points, record, current, graph].forEach(c => row.appendChild(c));
        container.appendChild(row);
    });
}

function record_(stat) {
    let s = stat.wins + "-" + stat.losses;
    if (stat.ties > 0) s += "-" + stat.ties;
    return s;
}

function col(text, cls) {
    const div = document.createElement("div");
    div.className = "stat-col stat-whatever " + cls;
    div.textContent = text;
    return div;
}

function showPlayer(playerId) {
    document.getElementById("feature-background").style.display = "block";
    const player = queryPlayerDetail(playerId);
    if (!player) return;

    document.getElementById("player-header").textContent =
        player.position + " " + player.name + " (" + player.points + ", " + record_(player) + ")";

    renderPlayerGrid(player);
}

function renderPlayerGrid(player) {
    const grid = document.getElementById("player-grid");
    grid.innerHTML = "";

    const bySeasonWeek = {};
    let minSeason = META.maxSeason, maxSeason = META.minSeason;
    for (const g of player.gameStats) {
        bySeasonWeek[g.season + ":" + g.scoringPeriod] = g;
        minSeason = Math.min(minSeason, g.season);
        maxSeason = Math.max(maxSeason, g.season);
    }

    grid.appendChild(gridItem("", true));
    for (let w = 1; w <= 17; w++) grid.appendChild(gridItem(String(w), true));

    for (let season = minSeason; season <= maxSeason; season++) {
        grid.appendChild(gridItem(String(season), true));
        for (let w = 1; w <= 17; w++) {
            const g = bySeasonWeek[season + ":" + w];
            if (g) {
                const item = gridItem(g.points + wlt(g), false);
                item.style.backgroundColor = TEAM_COLORS[g.teamNumber];
                item.style.opacity = (g.points + 10) / 40;
                item.title = ownerName(g.teamNumber, g.season) + ": " + g.points + " (" + wlt(g).toUpperCase() +
                    " v " + ownerName(g.opponentTeamNumber, g.season) + ", " + g.teamPoints + "-" + g.opponentPoints + ")";
                item.classList.add("game-summary");
                item.onclick = () => showGame(g.season, g.scoringPeriod, g.teamNumber);
                grid.appendChild(item);
            } else {
                grid.appendChild(gridItem("", false));
            }
        }
    }
}

function wlt(g) {
    if (g.teamPoints > g.opponentPoints) return "w";
    if (g.opponentPoints > g.teamPoints) return "l";
    return "t";
}

function gridItem(text, isHeader) {
    const div = document.createElement("div");
    div.className = "grid-item" + (isHeader ? " grid-header" : "");
    div.textContent = text;
    return div;
}

function hideFeature() {
    document.getElementById("feature-background").style.display = "none";
}

document.getElementById("feature-background").onclick = (e) => {
    if (e.target.id === "feature-background") hideFeature();
};

function hideGame() {
    document.getElementById("game-background").style.display = "none";
}

document.getElementById("game-background").onclick = (e) => {
    if (e.target.id === "game-background") hideGame();
};

function showGame(season, scoringPeriod, teamNumber) {
    document.getElementById("game-background").style.display = "block";
    const game = queryGameDetail(season, scoringPeriod, teamNumber);
    if (!game) return;

    document.getElementById("game-header").textContent = season + ", week " + scoringPeriod;
    const homeEl = document.getElementById("game-home");
    const awayEl = document.getElementById("game-away");
    homeEl.replaceWith(buildGameTeamPanel(game.home, season, "game-home"));
    awayEl.replaceWith(buildGameTeamPanel(game.away, season, "game-away"));
}

const POSITION_ORDER = ["QB", "RB", "RB/WR", "WR", "WR/TE", "TE", "D/ST", "K"];

// builds one side of a box score (team header + player rows + total). Used both by the
// game modal (showGame) and inline in the games list (renderGames).
function buildGameTeamPanel(side, season, elementId, onPlayerClick) {
    const container = document.createElement("div");
    if (elementId) container.id = elementId;
    container.className = "game-table game-team";
    container.style.backgroundColor = TEAM_COLORS[side.teamNumber];

    const title = document.createElement("div");
    title.className = "game-row game-row-title";
    title.textContent = ownerName(side.teamNumber, season);
    container.appendChild(title);

    const players = side.players.slice().sort((a, b) => {
        const ai = POSITION_ORDER.indexOf(a.position);
        const bi = POSITION_ORDER.indexOf(b.position);
        return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi);
    });

    for (const p of players) {
        const row = document.createElement("div");
        row.className = "game-row";
        row.appendChild(gameCol(p.position, ""));
        const name = gameCol(p.playerName, "game-col-player");
        name.onclick = () => { (onPlayerClick || defaultPlayerClick)(p.playerNumber); };
        row.appendChild(name);
        row.appendChild(gameCol(String(p.points), "game-col-points"));
        container.appendChild(row);
    }

    const total = document.createElement("div");
    total.className = "game-row";
    total.appendChild(gameCol("", ""));
    total.appendChild(gameCol("", ""));
    total.appendChild(gameCol(String(side.points), "game-col-points"));
    container.appendChild(total);

    return container;
}

function defaultPlayerClick(playerNumber) {
    hideGame();
    showPlayer(playerNumber);
}

function gameCol(text, cls) {
    const div = document.createElement("div");
    div.className = "game-col " + cls;
    div.textContent = text;
    return div;
}

async function init() {
    [META, GAMES, PLAYER_WEEKS] = await Promise.all([
        getJSON("data/meta.json"),
        getJSON("data/games.json"),
        getJSON("data/player_weeks.json"),
    ]);
    buildIndices();
    buildTeamButtons();
    buildPositionCheckboxes();
    buildSeasonSelects();
    buildGamesFilters();
    document.getElementById("mode-players").onclick = () => setMode("players");
    document.getElementById("mode-games").onclick = () => setMode("games");
    document.addEventListener("click", closeAllOwnerDropdowns);
    loadStats();
}

init();
