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
    $scope.positions = [
        'QB',
        'RB',
        'WR',
        'TE',
        'D/ST',
        'K'
    ];

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

        showFeature();

        $http.get('player/' + playerNumber)
            .then(function(response) {
                var answer = rangeOfScoringPeriodsWithPoints(2005, 2019, response.data.gameStats);
                $scope.playerData = answer[0];
                $scope.maxWeekPoints = answer[1];
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

    $scope.preify = function(text) {
        if (!text) return "<div>&nbsp;</div>";
        return "<div>&nbsp;</div><div>" + text + "</div>";
    }

    $scope.pretty = function(entry) {
        return entry.name + ", " + entry.position + " " + entry.points + " / " + entry.games;
    }

    $scope.statGraphStyle = function(pointsPerTeam) {
        result = new Object();
        result.width = "" + 100 * (pointsPerTeam.points / $scope.maxPoints) + "%";
        return result;
    }

    $scope.statGraphClass = function(teamNumber) {
        return teamStyle(teamNumber);
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

    $scope.points();
});

function showFeature() {
  document.getElementById("feature-background").style.display = "block";
}

function hideFeature() {
  document.getElementById("feature-background").style.display = "none";
}

function rangeOfYears() {
    var list = [];
    for (var i = 2006; i <= 2019; i++) {
        list.push(i);
    }
    return list;
}

function teamStyle(teamNumber) {
    switch(teamNumber) {
        case 1: return 'trav';
        case 2: return 'nick';
        case 3: return 'grum';
        case 4: return 'justin';
        case 5: return 'baird';
        case 6: return 'rich';
        case 7: return 'greg';
        case 8: return 'spoth';
        case 9: return 'paul';
        case 10: return 'argo';
        case 11: return 'bill';
        case 12: return 'dodge';
    }
}

function rangeOfScoringPeriodsWithPoints(start, end, playerWeeks) {

    var pointsBySp = new Object();
    var teamBySp = new Object();
    var max = 1;

    for (var i = 0; i < playerWeeks.length; i++) {
        pw = playerWeeks[i];
        pointsBySp[[pw.season, pw.scoringPeriod - 1]] = pw.points
        teamBySp[[pw.season, pw.scoringPeriod - 1]] = pw.teamNumber
        max = Math.max(max, pw.points)
    }

    result = [];
    for (var s = start; s <= end; s++) {
        var season = new Object();
        season.season = s;
        season.header = true;
        result.push(season);
        for (sp = 0; sp < 17; sp++) {
            var scoringPeriod = new Object();
            sp.season = s
            scoringPeriod.scoringPeriod = sp
            scoringPeriod.points = pointsBySp[[s, sp]];
            scoringPeriod.teamNumber = teamBySp[[s, sp]];
            if (!scoringPeriod.points) scoringPeriod.points = 0
            result.push(scoringPeriod);
        }
    }

    return [result, max];
}
