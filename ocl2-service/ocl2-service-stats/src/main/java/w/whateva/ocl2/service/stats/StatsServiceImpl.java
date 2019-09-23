package w.whateva.ocl2.service.stats;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import w.whateva.ocl2.api.stats.PointsPerTeam;
import w.whateva.ocl2.api.stats.StatsConstants;
import w.whateva.ocl2.api.stats.StatsService;
import w.whateva.ocl2.api.stats.dto.PlayerStats;
import w.whateva.ocl2.api.stats.dto.TeamPlayerStats;
import w.whateva.ocl2.api.stats.dto.TeamPositionStats;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.api.stats.dto.box.PlayerBoxScore;
import w.whateva.ocl2.api.stats.dto.box.TeamBoxScore;
import w.whateva.ocl2.service.stats.data.domain.Game;
import w.whateva.ocl2.service.stats.data.domain.Player;
import w.whateva.ocl2.service.stats.data.domain.PlayerWeek;
import w.whateva.ocl2.service.stats.data.domain.TeamWeek;
import w.whateva.ocl2.service.stats.data.repository.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
public class StatsServiceImpl implements StatsService {

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private TeamWeekRepository teamWeekRepository;

    @Autowired
    private PlayerWeekRepository playerWeekRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Override
    public List<GameBoxScore> gamesBySeasonAndScoringPeriod(Integer season, Integer scoringPeriod) {
        return gameRepository.findBySeasonAndScoringPeriod(season, scoringPeriod)
                .stream()
                .map(StatsServiceImpl::toApi)
                .collect(Collectors.toList());
    }

    @Override
    public List<GameBoxScore> highestScoringGames(Integer startWeek, Integer startSeason, Integer endWeek, Integer endSeason, List<Integer> teams, Boolean wins, Boolean losses, Boolean ties, Boolean ruxbees, Boolean bugtons, Boolean sortByTotal) {
        return gameRepository.findAllByOrderByTotalPointsDesc().stream().map(StatsServiceImpl::toApi).collect(Collectors.toList());
    }

    @Override
    public List<TeamPlayerStats> topNPlayersPerTeam(Integer n, Integer startWeek, Integer startSeason, Integer endWeek, Integer endSeason, List<Integer> teams) {
        return null;
    }

    @Override
    public List<TeamPositionStats> teamPositionStats(Integer startSeason, Integer endSeason, List<Integer> teams) {
        return null;
    }

    @Override
    public List<PlayerStats> playersByAppearances(Integer team) {

        List<Player> players = playerRepository.findAllByAppearances();

        return players
                .stream()
                .map(p -> {
                    PlayerStats result = new PlayerStats();
                    result.setName(p.getPlayerName());
                    result.setGames(p.getWeeks().size());
                    // result.setRank(i);
                    return result;
                })
                .limit(StatsConstants.RESULT_SIZE)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlayerStats> playersByPoints(List<Integer> teamNumbers,
                                             Integer startSeason,
                                             Integer startScoringPeriod,
                                             Integer endSeason,
                                             Integer endScoringPeriod) {

        Integer startGame = startSeason * 100 + startScoringPeriod;
        Integer endGame = endSeason * 100 + endScoringPeriod;

        List<Player> players = null;

        if (null != teamNumbers) {
            players = playerRepository.findAllByPointsForTeams(
                    teamNumbers,
                    startGame,
                    endGame);
        } else {
            players = playerRepository.findAllByPoints();
        }

        return players
                .stream()
                .map(p -> {

                    List<PlayerWeek> weeks = null != teamNumbers ?
                            p.getWeeks()
                                    .stream()
                                    .filter(w -> {
                                        TeamWeek tw = w.getTeam();
                                        return tw.getGameNumber() >= startGame &&
                                                tw.getGameNumber() <= endGame &&
                                                teamNumbers.contains(w.getTeam().getTeamNumber());
                                    })
                                    .collect(Collectors.toList()) :
                            p.getWeeks();

                    PlayerStats result = new PlayerStats();
                    result.setName(p.getPlayerName());
                    result.setGames(weeks.size());
                    result.setPosition(weeks.stream().findAny().get().getPosition());
                    result.setPoints(weeks.stream().mapToInt(PlayerWeek::getPoints).sum());
                    result.setPointsPerTeam(pointsPerTeam(weeks));
                    return result;
                })
                .limit(StatsConstants.RESULT_SIZE)
                .collect(Collectors.toList());
    }

    private static List<PointsPerTeam> pointsPerTeam(List<PlayerWeek> playerWeeks) {

        Map<Integer, Integer> pointsPerTeam = new LinkedHashMap<>();

        playerWeeks.forEach(pw -> {
            Integer teamNumber = pw.getTeam().getTeamNumber();
            pointsPerTeam.putIfAbsent(teamNumber, 0);
            pointsPerTeam.put(teamNumber, pointsPerTeam.get(teamNumber) + pw.getPoints());
        });

        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(pointsPerTeam.entrySet());
        list.sort(Map.Entry.<Integer, Integer>comparingByValue().reversed());

        return list.stream().map(e -> {
            PointsPerTeam result = new PointsPerTeam();
            result.setTeamNumber(e.getKey());
            result.setPoints(e.getValue());
            return result;
        })
        .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GameBoxScore addGame(GameBoxScore gameBoxScore) {

        // first, do we already have this game ?
        Game game = gameRepository.findBySeasonAndScoringPeriodAndHomeTeamNumber(
                gameBoxScore.getSeason(),
                gameBoxScore.getScoringPeriod(),
                gameBoxScore.getHome().getTeamNumber());

        // maybe we added the game already but we didn't know the away team number...
        if (null == game && !StringUtils.isEmpty(gameBoxScore.getHome().getHeader())) {

            game = gameRepository.findBySeasonAndScoringPeriodAndAwayTeamName(
                    gameBoxScore.getSeason(),
                    gameBoxScore.getScoringPeriod(),
                    gameBoxScore.getHome().getHeader());

        } else if (null != game) {

            System.out.println("Found game, skipping...");

            return gameBoxScore;
        }

        // ok fine, maybe we didn't add the game yet...
        if (null == game) {

            game = new Game();
            game.setSeason(gameBoxScore.getSeason());
            game.setScoringPeriod(gameBoxScore.getScoringPeriod());
            game.setGameNumber(game.getSeason() * 100 + game.getScoringPeriod());
            game.setHomeTeamNumber(gameBoxScore.getHome().getTeamNumber());
            game.setHomeTeamName(gameBoxScore.getHome().getHeader());
            game.setAwayTeamNumber(gameBoxScore.getAway().getTeamNumber());
            game.setAwayTeamName(gameBoxScore.getAway().getHeader());

            TeamWeek home = teamWeek(gameBoxScore.getHome());
            TeamWeek away = teamWeek(gameBoxScore.getAway());
            home.setGame(game);
            away.setGame(game);
            home.setGameNumber(game.getGameNumber());
            away.setGameNumber(game.getGameNumber());
            game.setTeamWeeks(Arrays.asList(home, away));

            game.setHomeTeamName(home.getHeader());
            game.setHomeTeamNumber(game.getHomeTeamNumber());
            game.setAwayTeamName(game.getAwayTeamName());

        } else {

            game.setAwayTeamNumber(gameBoxScore.getHome().getTeamNumber());
            game.getAway().setTeamNumber(gameBoxScore.getHome().getTeamNumber());
        }

        gameRepository.save(game);

        return gameBoxScore;
    }

    private TeamWeek teamWeek(TeamBoxScore teamBoxScore) {

        TeamWeek result = new TeamWeek();
        result.setHeader(teamBoxScore.getHeader());
        result.setTeamNumber(teamBoxScore.getTeamNumber());

        result.setPlayers(teamBoxScore.getPlayers()
                .stream()
                .map(this::playerWeek)
                .collect(Collectors.toList()));
        result.setPoints(result.getPlayers()
                .stream()
                .mapToInt(PlayerWeek::getPoints)
                .sum());

        // establish reverse link
        result.getPlayers().forEach(p -> p.setTeam(result));

        if (result.getPlayers().size() != 8) {
            System.out.println(String.format("not 8 !: %d", result.getPlayers().size()));
        }

        return result;
    }

    private PlayerWeek playerWeek(PlayerBoxScore playerBoxScore) {

        PlayerWeek result = new PlayerWeek();
        result.setGameStatus(playerBoxScore.getGameStatus());
        result.setLink(playerBoxScore.getLink());
        result.setOpponent(playerBoxScore.getOpponent());
        result.setPlayerName(playerBoxScore.getPlayerName());
        result.setPlayerNumber(playerBoxScore.getPlayerId());
        result.setPlayerTeam(playerBoxScore.getPlayerTeam());
        result.setPoints(playerBoxScore.getPoints());
        result.setPosition(playerBoxScore.getPosition());

        if (!StringUtils.isEmpty(result.getPlayerName())) {
            attachPlayer(result, playerBoxScore.isV3Api());
        }

        return result;
    }

    private void attachPlayer(PlayerWeek playerWeek, Boolean v3Api) {

        Player player = null;

        try {
            player = v3Api ?
                    playerRepository.findByPlayerNumber(playerWeek.getPlayerNumber()) :
                    playerRepository.findByPre2018PlayerNumber(playerWeek.getPlayerNumber());
        } catch (Exception e) {
            System.out.println("WHATE THE ? " + playerWeek.getPlayerName());
        }

        if (null == player) {

            List<Player> players = playerRepository.findByPlayerNameIndexed(index(playerWeek.getPlayerName()));

            if (players.size() == 1) {
                player = players.stream().findFirst().get();
            } else if (players.size() > 1) {
                System.out.println("WHAT THE HECK ? " + playerWeek.getPlayerName());
            }
        }

        if (null == player) {

            player = new Player();
        }

        player.setPlayerName(playerWeek.getPlayerName());
        player.setPlayerNameIndexed(index(player.getPlayerName()));
        if (v3Api) {
            player.setPlayerNumber(playerWeek.getPlayerNumber());
        } else {
            player.setPre2018PlayerNumber(playerWeek.getPlayerNumber());
        }

        playerRepository.save(player);

        playerWeek.setPlayer(player);
    }

    // assumes home team info is already set up...
    private static void updateGame(Game game, GameBoxScore gameBoxScore) {


    }

    private static GameBoxScore toApi(Game game) {

        GameBoxScore result = new GameBoxScore();
        result.setTotalPoints(game.getTotalPoints());
        result.setAway(teamBoxScore(game.getAway()));
        result.setHome(teamBoxScore(game.getHome()));
        result.setSeason(game.getSeason());
        result.setScoringPeriod(result.getScoringPeriod());
        return result;
    }

    private static TeamBoxScore teamBoxScore(TeamWeek teamWeek) {

        TeamBoxScore result = new TeamBoxScore();
        result.setHeader(teamWeek.getHeader());
        result.setPoints(teamWeek.getPoints());
        result.setTeamNumber(teamWeek.getTeamNumber());
        result.setPlayers(teamWeek.getPlayers()
                .stream()
                .map(StatsServiceImpl::playerBoxScore)
                .collect(Collectors.toList()));

        System.out.println("my opponent is: " + teamWeek.getOpponent());

        return result;
    }

    private static PlayerBoxScore playerBoxScore(PlayerWeek playerWeek) {

        PlayerBoxScore result = new PlayerBoxScore();
        result.setOpponent(playerWeek.getOpponent());
        result.setLink(playerWeek.getLink());
        result.setPlayerName(playerWeek.getPlayerName());
        result.setGameStatus(playerWeek.getGameStatus());
        result.setPlayerId(playerWeek.getPlayerNumber());
        result.setPoints(playerWeek.getPoints());
        result.setPosition(playerWeek.getPosition());
        result.setPlayerTeam(playerWeek.getPlayerTeam());

        return result;
    }

    private static String index(String playerName) {

        return playerName.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
