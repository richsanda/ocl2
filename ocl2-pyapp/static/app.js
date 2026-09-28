const TEAM_COLORS = {
    1: "#797979", 2: "#9a7a7a", 3: "#3f7f7f", 4: "#d6c8a3",
    5: "#495e7e", 6: "#757a2a", 7: "#a6e0ce", 8: "#c57a9a",
    9: "#cfa68e", 10: "#d498ed", 11: "#56b365", 12: "#8a9ac5",
};

let META = null;
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
        const o1 = new Option(s, s);
        const o2 = new Option(s, s);
        startSel.appendChild(o1);
        endSel.appendChild(o2);
    }
    startSel.value = Math.max(META.minSeason, 2006);
    endSel.value = META.maxSeason;
    startSel.onchange = refresh;
    endSel.onchange = refresh;
}

function buildPlayersFilters() {
    const minGamesSel = document.getElementById("min-games-filter");
    for (let n = 2; n <= META.maxGames; n += (n < 20 ? 1 : 5)) minGamesSel.appendChild(new Option(n, n));

    document.getElementById("players-sort-filter").onchange = refresh;
    document.getElementById("min-games-filter-enabled").onchange = refresh;
    minGamesSel.onchange = refresh;
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
    document.getElementById("mode-positions").classList.toggle("active", mode === "positions");
    document.getElementById("mode-teams").classList.toggle("active", mode === "teams");
    document.getElementById("position-filter-wrap").style.display = (mode === "players" || mode === "positions") ? "" : "none";
    document.getElementById("players-filters").style.display = mode === "players" ? "" : "none";
    document.getElementById("games-filters").style.display = mode === "games" ? "" : "none";
    document.getElementById("positions-filters").style.display = mode === "positions" ? "" : "none";
    document.getElementById("teams-filters").style.display = mode === "teams" ? "" : "none";
    document.getElementById("results").style.display = mode === "players" ? "" : "none";
    document.getElementById("games-results").style.display = mode === "games" ? "" : "none";
    document.getElementById("positions-results").style.display = mode === "positions" ? "" : "none";
    document.getElementById("team-seasons-results").style.display = mode === "teams" ? "" : "none";
    refresh();
}

function refresh() {
    if (mode === "players") loadStats();
    else if (mode === "games") loadGames();
    else if (mode === "positions") loadPositions();
    else loadTeamSeasons();
}

function selectTeams(teamNumbers, label) {
    selectedTeams = teamNumbers;
    document.getElementById("team-name").textContent = label;
    refresh();
}

async function loadStats() {
    const params = new URLSearchParams();
    if (selectedTeams) params.set("teamNumbers", selectedTeams.join(","));
    if (selectedPositions.size) params.set("positions", Array.from(selectedPositions).join(","));
    params.set("startSeason", document.getElementById("start-season").value);
    params.set("endSeason", document.getElementById("end-season").value);
    params.set("sort", document.getElementById("players-sort-filter").value);
    if (document.getElementById("min-games-filter-enabled").checked) {
        params.set("minGames", document.getElementById("min-games-filter").value);
    }

    const stats = await getJSON("/api/players/points?" + params.toString());
    renderStats(stats || []);
}

async function loadGames() {
    const params = new URLSearchParams();
    if (selectedTeams) params.set("teamNumbers", selectedTeams.join(","));
    params.set("startSeason", document.getElementById("start-season").value);
    params.set("endSeason", document.getElementById("end-season").value);

    const outcome = document.getElementById("outcome-filter").value;
    if (outcome) params.set("outcome", outcome);
    const ruxbee = document.getElementById("ruxbee-filter").value;
    if (ruxbee) params.set("ruxbee", ruxbee);
    const bugton = document.getElementById("bugton-filter").value;
    if (bugton) params.set("bugton", bugton);
    if (document.getElementById("multi-week-filter").checked) params.set("includeMultiWeek", "true");
    params.set("sort", document.getElementById("sort-filter").value);

    const games = await getJSON("/api/games?" + params.toString());
    renderGames(games || []);
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

function buildPositionsFilters() {
    document.getElementById("positions-sort-filter").onchange = refresh;
}

async function loadPositions() {
    const params = new URLSearchParams();
    if (selectedTeams) params.set("teamNumbers", selectedTeams.join(","));
    if (selectedPositions.size) params.set("positions", Array.from(selectedPositions).join(","));
    params.set("startSeason", document.getElementById("start-season").value);
    params.set("endSeason", document.getElementById("end-season").value);
    params.set("sort", document.getElementById("positions-sort-filter").value);

    const rows = await getJSON("/api/positions?" + params.toString());
    renderPositions(rows || []);
}

function contributorsText(contributors) {
    return contributors.map(c => c.name + " " + c.pct + "%").join(", ");
}

function recordText(row) {
    let s = row.wins + "-" + row.losses;
    if (row.ties > 0) s += "-" + row.ties;
    return "(" + s + ")";
}

function renderPositions(rows) {
    const container = document.getElementById("positions-results");
    container.innerHTML = "";

    rows.forEach((row, index) => {
        const div = document.createElement("div");
        div.className = "position-row";
        div.title = contributorsText(row.allContributors);
        div.onclick = () => showPositionDetail(row);

        div.appendChild(summaryCol((index + 1) + "."));
        div.appendChild(summaryCol(String(row.points)));
        div.appendChild(summaryCol(row.position));
        const owner = document.createElement("div");
        owner.className = "game-summary-col";
        owner.appendChild(ownerChip(row.teamNumber, row.season));
        div.appendChild(owner);
        div.appendChild(summaryCol(String(row.season)));
        div.appendChild(summaryCol(recordText(row)));
        div.appendChild(summaryCol(contributorsText(row.contributors)));

        container.appendChild(div);
    });
}

function buildTeamsFilters() {
    document.getElementById("teams-sort-filter").onchange = refresh;
}

async function loadTeamSeasons() {
    const params = new URLSearchParams();
    if (selectedTeams) params.set("teamNumbers", selectedTeams.join(","));
    params.set("startSeason", document.getElementById("start-season").value);
    params.set("endSeason", document.getElementById("end-season").value);
    params.set("sort", document.getElementById("teams-sort-filter").value);

    const rows = await getJSON("/api/team-seasons?" + params.toString());
    renderTeamSeasons(rows || []);
}

function renderTeamSeasons(rows) {
    const container = document.getElementById("team-seasons-results");
    container.innerHTML = "";

    rows.forEach((row, index) => {
        const div = document.createElement("div");
        div.className = "team-season-row";
        div.title = contributorsText(row.allContributors);
        div.onclick = () => showPositionDetail(row);

        div.appendChild(summaryCol((index + 1) + "."));
        div.appendChild(summaryCol(String(row.points)));
        const owner = document.createElement("div");
        owner.className = "game-summary-col";
        owner.appendChild(ownerChip(row.teamNumber, row.season));
        div.appendChild(owner);
        div.appendChild(summaryCol(String(row.season)));
        div.appendChild(summaryCol(recordText(row)));
        div.appendChild(summaryCol(contributorsText(row.contributors)));

        container.appendChild(div);
    });
}

function renderStats(stats) {
    const container = document.getElementById("results");
    container.innerHTML = "";
    // was assuming stats[0] (the top-sorted row) had the highest points total, which only
    // holds when sorting by points -- with games/average sorts the longest bar can be
    // anywhere in the list, so scale against the true max or bars overflow past 100% width
    const maxPoints = stats.length ? Math.max(...stats.map(s => s.points)) : 1;

    stats.forEach((stat, index) => {
        const row = document.createElement("div");
        row.className = "stat-row";
        row.onclick = () => showPlayer(stat.playerId);

        const rank = col(String(index + 1) + ".", "stat-rank");
        const position = col(stat.position, "");
        const name = col(stat.name, "");
        const points = col(String(stat.points), "");
        const games = col(String(stat.games), "");
        const average = col(String(stat.average), "");
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

        [rank, position, name, points, games, average, record, current, graph].forEach(c => row.appendChild(c));
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

async function showPlayer(playerId) {
    pushModal("feature-background");
    const player = await getJSON("/api/player/" + playerId);
    if (!player) return;

    document.getElementById("player-header").textContent =
        player.position + " " + player.name + " (" + player.points + ", " + record_(player) + ")";

    renderPlayerGrid(player);
}

function renderPlayerGrid(player) {
    const grid = document.getElementById("player-grid");
    grid.innerHTML = "";

    const bySeasonPeriod = {};
    let minSeason = META.maxSeason, maxSeason = META.minSeason;
    for (const g of player.gameStats) {
        bySeasonPeriod[g.season + ":" + g.scoringPeriod] = g;
        minSeason = Math.min(minSeason, g.season);
        maxSeason = Math.max(maxSeason, g.season);
    }

    // header row: blank + running real-week numbers (a 2-week combined period counts as 2
    // columns here, so these numbers track real NFL week, not raw scoringPeriodId)
    const maxCols = maxWeekColumns();
    placeGridItem(grid, gridItem("", true), 1, 1, 1);
    for (let w = 1; w <= maxCols; w++) placeGridItem(grid, gridItem(String(w), true), 1, w + 1, 1);

    for (let season = minSeason; season <= maxSeason; season++) {
        const row = season - minSeason + 2;
        placeGridItem(grid, gridItem(String(season), true), row, 1, 1);

        const layout = seasonPeriodLayout(season);
        for (const p of layout.periods) {
            const g = bySeasonPeriod[season + ":" + p.period];
            let item;
            if (g) {
                item = gridItem(g.points + wlt(g), false);
                item.style.backgroundColor = TEAM_COLORS[g.teamNumber];
                item.style.opacity = (g.points + 10) / 40;
                item.title = ownerName(g.teamNumber, g.season) + ": " + g.points + " (" + wlt(g).toUpperCase() +
                    " v " + ownerName(g.opponentTeamNumber, g.season) + ", " + g.teamPoints + "-" + g.opponentPoints + ")";
                item.classList.add("game-summary");
                item.onclick = () => showGame(g.season, g.scoringPeriod, g.teamNumber);
            } else {
                item = gridItem("", false);
            }
            placeGridItem(grid, item, row, p.colStart + 1, p.span);
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

// explicit grid-row/grid-column placement -- every cell in a week-grid declares its own
// position rather than relying on auto-flow, since a season's periods can span a variable
// number of real-week columns (2-week combined playoff rounds) and still need to line up
// under the correct real-week header across rows/seasons that don't share that layout.
function placeGridItem(grid, item, row, col, span) {
    item.style.gridRow = String(row);
    item.style.gridColumn = span > 1 ? `${col} / span ${span}` : String(col);
    grid.appendChild(item);
    return item;
}

const _periodLayoutCache = {};

// {periods: [{period, colStart, span}, ...] in period order, totalCols}. colStart/span are
// real-week column positions (1-based) -- a 2-week combined period occupies 2 columns, so
// column numbers "run" ahead of raw scoringPeriodId once the first one appears. That's
// intentional: the displayed axis is real NFL week, not ESPN's period index.
function seasonPeriodLayout(season) {
    if (_periodLayoutCache[season]) return _periodLayoutCache[season];
    const periods = (META.periodsBySeason && META.periodsBySeason[season]) || [];
    let col = 1;
    const list = [];
    for (const p of periods) {
        list.push({ period: p.period, colStart: col, span: p.weeksCovered });
        col += p.weeksCovered;
    }
    const layout = { periods: list, totalCols: col - 1 };
    _periodLayoutCache[season] = layout;
    return layout;
}

let _maxWeekColumnsCache = null;

function maxWeekColumns() {
    if (_maxWeekColumnsCache != null) return _maxWeekColumnsCache;
    let max = 0;
    for (const season in (META.periodsBySeason || {})) {
        max = Math.max(max, seasonPeriodLayout(Number(season)).totalCols);
    }
    _maxWeekColumnsCache = max || 17;
    return _maxWeekColumnsCache;
}

function weekLabel(p) {
    return p.span > 1 ? (p.colStart + "-" + (p.colStart + p.span - 1)) : String(p.colStart);
}

// -- modal stack: opening a modal pushes it on top WITHOUT hiding whatever's already
// open beneath it (each modal type only ever occupies one stack slot -- reopening an
// already-open type just moves it to the top and refreshes its content). Closing the
// topmost modal (via clicking its backdrop) pops it and reveals whichever was
// underneath, instead of dropping all the way back to the main page.
const MODAL_IDS = ["feature-background", "game-background", "position-detail-background", "schedule-background"];
let modalStack = [];

function pushModal(id) {
    modalStack = modalStack.filter(x => x !== id);
    modalStack.push(id);
    reindexModals();
}

function popModal(id) {
    modalStack = modalStack.filter(x => x !== id);
    reindexModals();
}

function reindexModals() {
    MODAL_IDS.forEach(id => {
        const el = document.getElementById(id);
        const pos = modalStack.indexOf(id);
        if (pos === -1) {
            el.style.display = "none";
        } else {
            el.style.zIndex = 100 + pos * 10;
            el.style.display = "block";
        }
    });
}

function hideFeature() {
    popModal("feature-background");
}

document.getElementById("feature-background").onclick = (e) => {
    if (e.target.id === "feature-background") hideFeature();
};

function hidePositionDetail() {
    popModal("position-detail-background");
}

document.getElementById("position-detail-background").onclick = (e) => {
    if (e.target.id === "position-detail-background") hidePositionDetail();
};

async function showPositionDetail(row) {
    pushModal("position-detail-background");

    const header = document.getElementById("position-detail-header");
    header.innerHTML = "";
    const posPrefix = row.position ? row.position + " " : "";
    header.appendChild(document.createTextNode(
        posPrefix + ownerName(row.teamNumber, row.season) + " " + row.season +
        " (" + row.points + ") " + recordText(row) + " "
    ));
    const scheduleBtn = document.createElement("button");
    scheduleBtn.type = "button";
    scheduleBtn.className = "schedule-btn";
    scheduleBtn.textContent = "schedule";
    scheduleBtn.style.backgroundColor = TEAM_COLORS[row.teamNumber];
    scheduleBtn.style.opacity = (5 + 10) / 40; // ~a "5-point week" cell's intensity
    scheduleBtn.onclick = () => showSchedule(row);
    header.appendChild(scheduleBtn);

    const params = new URLSearchParams({ teamNumber: row.teamNumber, season: row.season });
    if (row.position) params.set("position", row.position);
    const detail = await getJSON("/api/position-detail?" + params.toString());
    renderPositionDetail(row, detail || { players: [] });
}

function renderPositionDetail(row, detail) {
    const grid = document.getElementById("position-detail-grid");
    grid.innerHTML = "";

    const maxCols = maxWeekColumns();
    const columnCount = maxCols + 2; // position + name + real weeks

    // header row: 2 blank (position + name) + running real-week numbers
    placeGridItem(grid, gridItem("", true), 1, 1, 1);
    placeGridItem(grid, gridItem("", true), 1, 2, 1);
    for (let w = 1; w <= maxCols; w++) placeGridItem(grid, gridItem(String(w), true), 1, w + 2, 1);

    const layout = seasonPeriodLayout(row.season);

    let gridRow = 2;
    let previousPosition = null;
    detail.players.forEach(player => {
        const isNewGroup = player.position !== previousPosition;
        if (isNewGroup && previousPosition !== null) {
            for (let i = 0; i < columnCount; i++) {
                const spacer = document.createElement("div");
                spacer.className = "grid-divider";
                placeGridItem(grid, spacer, gridRow, i + 1, 1);
            }
            gridRow++;
        }
        previousPosition = player.position;

        const posCell = gridItem(isNewGroup ? player.position : "", true);
        posCell.classList.add("grid-row-label");
        placeGridItem(grid, posCell, gridRow, 1, 1);

        const nameCell = gridItem(player.name, true);
        nameCell.classList.add("grid-row-label");
        nameCell.onclick = () => showPlayer(player.playerId);
        placeGridItem(grid, nameCell, gridRow, 2, 1);

        for (const p of layout.periods) {
            const wk = player.weeks[p.period];
            let item;
            if (wk) {
                const outcome = wk.win ? "w" : wk.loss ? "l" : "t";
                item = gridItem(wk.points + outcome, false);
                item.style.backgroundColor = TEAM_COLORS[row.teamNumber];
                item.style.opacity = (wk.points + 10) / 40;
                item.title = player.name + ", week " + weekLabel(p) + ": " + wk.points + " (" + outcome.toUpperCase() + ")";
                item.classList.add("game-summary");
                item.onclick = () => showGame(row.season, p.period, row.teamNumber);
            } else {
                item = gridItem("", false);
            }
            placeGridItem(grid, item, gridRow, p.colStart + 2, p.span);
        }

        gridRow++;
    });
}

function hideGame() {
    popModal("game-background");
}

document.getElementById("game-background").onclick = (e) => {
    if (e.target.id === "game-background") hideGame();
};

async function showGame(season, scoringPeriod, teamNumber) {
    pushModal("game-background");
    const game = await getJSON("/api/game/" + season + "/" + scoringPeriod + "/" + teamNumber);
    if (!game) return;

    document.getElementById("game-header").textContent = season + ", week " + scoringPeriod;
    const homeEl = document.getElementById("game-home");
    const awayEl = document.getElementById("game-away");
    homeEl.replaceWith(buildGameTeamPanel(game.home, season, "game-home"));
    awayEl.replaceWith(buildGameTeamPanel(game.away, season, "game-away"));
}

function hideSchedule() {
    popModal("schedule-background");
}

document.getElementById("schedule-background").onclick = (e) => {
    if (e.target.id === "schedule-background") hideSchedule();
};

async function showSchedule(row) {
    pushModal("schedule-background");
    document.getElementById("schedule-header").textContent =
        ownerName(row.teamNumber, row.season) + " " + row.season + " schedule";

    const params = new URLSearchParams({
        teamNumbers: row.teamNumber, startSeason: row.season, endSeason: row.season,
        includeMultiWeek: "true", sort: "points_desc",
    });
    const games = (await getJSON("/api/games?" + params.toString())) || [];
    games.sort((a, b) => a.scoringPeriod - b.scoringPeriod);
    renderSchedule(games);
}

function renderSchedule(games) {
    const container = document.getElementById("schedule-results");
    container.innerHTML = "";

    games.forEach(g => {
        const label = document.createElement("div");
        label.className = "schedule-week-label";
        const outcome = g.win ? "W" : g.loss ? "L" : "T";
        label.textContent = "week " + g.scoringPeriod + (g.weeksCovered > 1 ? " (2wk)" : "") +
            " -- " + g.teamPoints + "-" + g.opponentPoints + " (" + outcome + ")";
        container.appendChild(label);

        const box = document.createElement("div");
        box.className = "game-entry-box";
        box.appendChild(buildGameTeamPanel({ teamNumber: g.teamNumber, points: g.teamPoints, players: g.players }, g.season));
        box.appendChild(buildGameTeamPanel({ teamNumber: g.opponentTeamNumber, points: g.opponentPoints, players: g.opponentPlayers }, g.season));
        container.appendChild(box);
    });
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
    showPlayer(playerNumber);
}

function gameCol(text, cls) {
    const div = document.createElement("div");
    div.className = "game-col " + cls;
    div.textContent = text;
    return div;
}

async function init() {
    META = await getJSON("/api/meta");
    buildTeamButtons();
    buildPositionCheckboxes();
    buildSeasonSelects();
    buildPlayersFilters();
    buildGamesFilters();
    buildPositionsFilters();
    buildTeamsFilters();
    document.getElementById("mode-players").onclick = () => setMode("players");
    document.getElementById("mode-games").onclick = () => setMode("games");
    document.getElementById("mode-positions").onclick = () => setMode("positions");
    document.getElementById("mode-teams").onclick = () => setMode("teams");
    document.addEventListener("click", closeAllOwnerDropdowns);
    setMode(mode);
}

init();
