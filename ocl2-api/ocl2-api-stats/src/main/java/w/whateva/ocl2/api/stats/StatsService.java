package w.whateva.ocl2.api.stats;

import org.springframework.web.bind.annotation.*;
import w.whateva.ocl2.api.stats.dto.PlayerStats;
import w.whateva.ocl2.api.stats.dto.TeamPlayerStats;
import w.whateva.ocl2.api.stats.dto.TeamPositionStats;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;

import java.util.List;

import static w.whateva.ocl2.api.stats.StatsConstants.*;

@RequestMapping
public interface StatsService {

    @RequestMapping(value = "/games/{season}/{scoringPeriod}", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    List<GameBoxScore> gamesBySeasonAndScoringPeriod(@PathVariable("season") Integer season, @PathVariable("scoringPeriod") Integer scoringPeriod);

    @RequestMapping(value = "/games/highestScores", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    List<GameBoxScore> highestScoringGames(
            @RequestParam(required = false) Integer startWeek,
            @RequestParam(required = false) Integer startSeason,
            @RequestParam(required = false) Integer endWeek,
            @RequestParam(required = false) Integer endSeason,
            @RequestParam(required = false) List<Integer> teams,
            @RequestParam(required = false) Boolean wins,
            @RequestParam(required = false) Boolean losses,
            @RequestParam(required = false) Boolean ties,
            @RequestParam(required = false) Boolean ruxbees,
            @RequestParam(required = false) Boolean bugtons,
            @RequestParam(required = false) Boolean sortByTotal
    );

    @GetMapping(value = "/teams/topPlayers")
    List<TeamPlayerStats> topNPlayersPerTeam(
            Integer n,
            Integer startWeek,
            Integer startSeason,
            Integer endWeek,
            Integer endSeason,
            List<Integer> teams);

    @GetMapping(value = "/teams/positions")
    List<TeamPositionStats> teamPositionStats(
            Integer startSeason,
            Integer endSeason,
            List<Integer> teams
    );

    @GetMapping(value = "/players/appearances")
    List<PlayerStats> playersByAppearances(@RequestParam(value = "team", required = false) Integer team);

    @GetMapping(value = "/players/points")
    List<PlayerStats> playersByPoints(@RequestParam(value = "teamNumbers", required = false, defaultValue = DEFAULT_TEAMS) List<Integer> teamNumbers,
                                      @RequestParam(value = "startSeason", required = false, defaultValue = DEFAULT_START_SEASON) Integer startSeason,
                                      @RequestParam(value = "startScoringPeriod", required = false, defaultValue = DEFAULT_START_SCORING_PERIOD) Integer startScoringPeriod,
                                      @RequestParam(value = "endSeason", required = false, defaultValue = DEFAULT_END_SEASON) Integer endSeason,
                                      @RequestParam(value = "endScoringPeriod", required = false, defaultValue = DEFAULT_END_SCORING_PERIOD) Integer endScoringPeriod);
    @PostMapping
    GameBoxScore addGame(GameBoxScore game);
}
