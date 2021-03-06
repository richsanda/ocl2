package w.whateva.ocl2.api.stats;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import w.whateva.ocl2.api.stats.dto.GameSortType;
import w.whateva.ocl2.api.stats.dto.PlayerStats;
import w.whateva.ocl2.api.stats.dto.TeamPositionStats;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;

import java.util.ArrayList;
import java.util.List;

import static w.whateva.ocl2.api.stats.StatsConstants.*;

public interface StatsService {

    @GetMapping(value = "/games/{season}/{scoringPeriod}", produces = "application/json")
    @ResponseBody
    List<GameBoxScore> gamesBySeasonAndScoringPeriod(@PathVariable("season") Integer season, @PathVariable("scoringPeriod") Integer scoringPeriod);

    @GetMapping(value = "/game/{season}/{scoringPeriod}/{teamNumber}", produces = "application/json")
    @ResponseBody
    GameBoxScore gameBySeasonAndScoringPeriodAndTeam(@PathVariable("season") Integer season, @PathVariable("scoringPeriod") Integer scoringPeriod, @PathVariable("teamNumber") Integer teamNumber);

    @GetMapping(value = "/games", produces = "application/json")
    @ResponseBody
    List<GameBoxScore> games(
            @RequestParam(required = false, defaultValue = DEFAULT_START_SCORING_PERIOD) Integer startWeek,
            @RequestParam(required = false, defaultValue = DEFAULT_START_SEASON) Integer startSeason,
            @RequestParam(required = false, defaultValue = DEFAULT_END_SCORING_PERIOD) Integer endWeek,
            @RequestParam(required = false, defaultValue = DEFAULT_END_SEASON) Integer endSeason,
            @RequestParam(required = false, defaultValue = DEFAULT_TEAMS) ArrayList<Integer> teams,
            @RequestParam(required = false, defaultValue = DEFAULT_TRUE) Boolean wins,
            @RequestParam(required = false, defaultValue = DEFAULT_TRUE) Boolean losses,
            @RequestParam(required = false, defaultValue = DEFAULT_TRUE) Boolean ties,
            @RequestParam(value = "ruxbeeLimit", required = false) Integer ruxbeeLimit,
            @RequestParam(required = false) Integer bugtonLimit,
            @RequestParam(required = false) GameSortType sortType
    );

    @GetMapping(value = "/teams/positions")
    List<TeamPositionStats> teamPositionStats(
            Integer startSeason,
            Integer endSeason,
            List<Integer> teams
    );

    @GetMapping(value = "/player/{season}:{playerNumber}")
    PlayerStats player(@PathVariable(value = "season") Integer season, @PathVariable(value = "playerNumber") Integer playerNumber);

    @GetMapping(value = "/players/appearances")
    List<PlayerStats> playersByAppearances(@RequestParam(value = "team", required = false) Integer team);

    @GetMapping(value = "/players/points")
    List<PlayerStats> playersByPoints(@RequestParam(value = "teamNumbers", required = false, defaultValue = DEFAULT_TEAMS) ArrayList<Integer> teamNumbers,
                                      @RequestParam(value = "positions", required = false, defaultValue = DEFAULT_POSITIONS) ArrayList<String> positions,
                                      @RequestParam(value = "startSeason", required = false, defaultValue = DEFAULT_START_SEASON) Integer startSeason,
                                      @RequestParam(value = "startScoringPeriod", required = false, defaultValue = DEFAULT_START_SCORING_PERIOD) Integer startScoringPeriod,
                                      @RequestParam(value = "endSeason", required = false, defaultValue = DEFAULT_END_SEASON) Integer endSeason,
                                      @RequestParam(value = "endScoringPeriod", required = false, defaultValue = DEFAULT_END_SCORING_PERIOD) Integer endScoringPeriod);
    //@PostMapping
    GameBoxScore addGame(GameBoxScore game);

    @GetMapping(value = "/games/text", produces = "application/json")
    @ResponseBody
    List<String> gamesText(
            @RequestParam(required = false, defaultValue = DEFAULT_START_SCORING_PERIOD) Integer startWeek,
            @RequestParam(required = false, defaultValue = DEFAULT_START_SEASON) Integer startSeason,
            @RequestParam(required = false, defaultValue = DEFAULT_END_SCORING_PERIOD) Integer endWeek,
            @RequestParam(required = false, defaultValue = DEFAULT_END_SEASON) Integer endSeason,
            @RequestParam(required = false, defaultValue = DEFAULT_TEAMS) ArrayList<Integer> teams,
            @RequestParam(required = false, defaultValue = DEFAULT_TRUE) Boolean wins,
            @RequestParam(required = false, defaultValue = DEFAULT_TRUE) Boolean losses,
            @RequestParam(required = false, defaultValue = DEFAULT_TRUE) Boolean ties,
            @RequestParam(value = "ruxbeeLimit", required = false) Integer ruxbeeLimit,
            @RequestParam(required = false) Integer bugtonLimit,
            @RequestParam(required = false) GameSortType sortType
    );

    @GetMapping(value = "/playerweeks")
    List<String> playerWeeks();
}
