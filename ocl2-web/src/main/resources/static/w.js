var app = angular.module('app', ['ngSanitize']);

app.controller('controller', function($scope, $http) {

    $scope.seasons = [2005, 2006, 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017, 2018, 2019];
    $scope.startSeason = 2006;
    $scope.endSeason = 2019;
    $scope.teamName = 'all';
    $scope.availPositions = [
        'QB',
        'RB',
        'WR',
        'TE',
        'D/ST',
        'K'
    ];
    $scope.positions = [];
//        'QB',
//        'RB',
//        'WR',
//        'TE',
//        'D/ST',
//        'K'
//    ];

    $scope.points = function(teamNumbers) {

        $scope.stats = [];
        $scope.teamNumbers = teamNumbers;

        var url = 'players/points';

        $http.get(url, {params: {"teamNumbers" : teamNumbers, "startSeason" : $scope.startSeason, "endSeason" : $scope.endSeason, "positions" : $scope.positions}})
            .then(function(response) {
                $scope.maxPoints = response.data[0].points
                $scope.stats = response.data;
            });
    }

    $scope.read = function(index, playerNumber) {

        var previous = angular.element( document.querySelector('div.artifact-link-selected') );
        previous.toggleClass('artifact-link-selected')
        var current = angular.element( document.querySelector('#artifact' + index) );
        current.toggleClass('artifact-link-selected');

        $scope.playerName = null;
        $scope.playerPosition = null;
        $scope.playerData = [];

        showFeature();

        $http.get('player/' + playerNumber)
            .then(function(response) {
                var answer = rangeOfScoringPeriodsWithPoints(2005, 2019, response.data.gameStats);
                $scope.player = response.data
                $scope.playerData = answer[0];
                $scope.maxWeekPoints = answer[1];
            });
    }

    $scope.readGame = function(season, scoringPeriod, teamNumber) {

        showGame();

        $http.get('game/' + season + '/' + scoringPeriod + '/' + teamNumber)
            .then(function(response) {
                $scope.game = response.data
            });
    }

    $scope.appearances = function() {

        var url = 'players/appearances';

        $http.get(url)
            .then(function(response) {
                $scope.stats = response.data;
            });
    }

    $scope.enter = function(event) {
      if (event.which === 13)
        $scope.counts();
    }

    $scope.hideFeature = function() {
        $scope.feature = ''
        hideFeature();
    }

    $scope.hideGame = function() {
       $scope.game = ''
       hideGame();
    }

    $scope.sortByPosition = function(players) {
        return sortByPosition(players);
    }

    $scope.statGraphStyle = function(pointsPerTeam) {
        result = new Object();
        result.width = "" + 100 * (pointsPerTeam.points / $scope.maxPoints) + "%";
        return result;
    }

    $scope.statGraphClass = function(teamNumber, season) {
        return teamStyle(teamNumber, season);
    }

    $scope.playerGameStats = function(playerGame) {
        return $scope.statGraphClass(playerGame.teamNumber, playerGame.season) +
        ': ' + playerGame.points +
        ' (' + wlt(playerGame) + ' ' + vs(playerGame) + ', ' + playerGame.teamPoints + '-' + playerGame.opponentPoints + ')';
    }

    $scope.togglePosition = function(val) {
        i = $scope.positions.indexOf(val);
        if (i > -1) {
            $scope.positions.splice(i, 1);
        } else {
            $scope.positions.push(val);
        }
    }

    $scope.playerWeeks = function(data) {
        return rangeOfYears();
    }

    $scope.wltEmo = function(playerGame) {
        switch (wlt(playerGame)) {
            case 'w' : return ':)';
            case 'l' : return ':(';
            case 't' : return ':|';
        }
    }

    $scope.wlt = function(playerGame) {
        return wlt(playerGame).toUpperCase();
    }

    $scope.points();
});

function showFeature() {
  document.getElementById("feature-background").style.display = "block";
}

function hideFeature() {
  document.getElementById("feature-background").style.display = "none";
}

function showGame() {
  document.getElementById("game-background").style.display = "block";
}

function hideGame() {
  document.getElementById("game-background").style.display = "none";
}

function rangeOfYears() {
    var list = [];
    for (var i = 2006; i <= 2019; i++) {
        list.push(i);
    }
    return list;
}

function teamStyle(teamNumber, season) {
    switch(teamNumber) {
        case 1: return 'trav';
        case 2: return 'nick';
        case 3: return 'grum';
        case 4: return 'justin';
        case 5: return season < 2016 ? "rux" : 'baird';
        case 6: return 'rich';
        case 7: return 'greg';
        case 8: return 'spoth';
        case 9: return season < 2016 ? "mbug" : "paul";
        case 10: return 'argo';
        case 11: return 'bill';
        case 12: return season < 2015 ? "ruggs" : "dodge";
    }
}

function rangeOfScoringPeriodsWithPoints(start, end, playerWeeks) {

    var pwBySp = new Object();
    var max = 1;
    var seasonMin = end;
    var seasonMax = start;

    for (var i = 0; i < playerWeeks.length; i++) {
        var pw = playerWeeks[i];
        pwBySp[[pw.season, pw.scoringPeriod - 1]] = pw
        max = Math.max(max, pw.points)
        seasonMin = Math.min(seasonMin, pw.season)
        seasonMax = Math.max(seasonMax, pw.season)
    }

    result = [];
    for (var s = seasonMin; s <= seasonMax; s++) {

        var season = new Object();
        season.season = s;
        season.header = true;
        result.push(season);

        for (sp = 0; sp < 17; sp++) {

            var scoringPeriod = new Object();
            sp.season = s
            scoringPeriod.scoringPeriod = sp

            if (pwBySp[[s, sp]]) {
                var pw = pwBySp[[s, sp]];
                scoringPeriod.points = pw.points;
                scoringPeriod.teamNumber = pw.teamNumber;
                scoringPeriod.teamPoints = pw.teamPoints;
                scoringPeriod.opponentPoints = pw.opponentPoints;
                scoringPeriod.opponentTeamNumber = pw.opponentTeamNumber;
                scoringPeriod.season = s;
            } else {
                scoringPeriod.points = 0
            }

            result.push(scoringPeriod);
        }
    }

    return [result, max];
}

function wlt(playerGame) {
    return playerGame.teamPoints > playerGame.opponentPoints ? 'w' :
    playerGame.opponentPoints > playerGame.teamPoints ? 'l' : 't';
}

function vs(playerGame) {
    return 'v ' + teamStyle(playerGame.opponentTeamNumber, playerGame.season);
}

function sortByPosition(players) {

    if (!players) return [];

    var positions = ["QB", "RB", "RB/WR", "WR", "WR/TE", "TE", "D/ST", "K"];
    var playersByPosition = new Object();
    for (var i = 0; i < positions.length; i++) {
        playersByPosition[positions[i]] = [];
    }

    for (var i = 0; i < players.length; i++) {
        var player = players[i];
        var pos = player.position;
        if ($.inArray(pos, positions) != -1) {
            playersByPosition[pos].push(player);
        } else {
            // pass
        }
    }

    result = [];
    for (var i = 0; i < positions.length; i++) {
        var pos = positions[i];
        for (var j = 0; j < playersByPosition[pos].length; j++) {
            var player = playersByPosition[pos][j];
            result.push(player);
        }
    }

    return result;
}
