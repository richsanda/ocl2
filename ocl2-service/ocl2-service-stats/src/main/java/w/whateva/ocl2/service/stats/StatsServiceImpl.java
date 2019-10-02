package w.whateva.ocl2.service.stats;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import w.whateva.ocl2.api.stats.PointsPerTeam;
import w.whateva.ocl2.api.stats.StatsConstants;
import w.whateva.ocl2.api.stats.StatsService;
import w.whateva.ocl2.api.stats.dto.GameSortType;
import w.whateva.ocl2.api.stats.dto.PlayerGameStats;
import w.whateva.ocl2.api.stats.dto.PlayerStats;
import w.whateva.ocl2.api.stats.dto.TeamPositionStats;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.api.stats.dto.box.PlayerBoxScore;
import w.whateva.ocl2.api.stats.dto.box.TeamBoxScore;
import w.whateva.ocl2.service.stats.data.domain.Game;
import w.whateva.ocl2.service.stats.data.domain.Player;
import w.whateva.ocl2.service.stats.data.domain.PlayerWeek;
import w.whateva.ocl2.service.stats.data.domain.TeamWeek;
import w.whateva.ocl2.service.stats.data.repository.*;

import javax.persistence.EntityManager;
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

    @Autowired
    EntityManager entityManager;

    @Override
    public List<GameBoxScore> gamesBySeasonAndScoringPeriod(Integer season, Integer scoringPeriod) {
        return gameRepository.findBySeasonAndScoringPeriod(season, scoringPeriod)
                .stream()
                .map(StatsServiceImpl::toApi)
                .collect(Collectors.toList());
    }

    @Override
    public GameBoxScore gameBySeasonAndScoringPeriodAndTeam(Integer season, Integer scoringPeriod, Integer teamNumber) {
        Game game = gameRepository.findGame(season, scoringPeriod, teamNumber);
        return toApi(game);
    }

    @Override
    public List<GameBoxScore> games(Integer startWeek, Integer startSeason, Integer endWeek, Integer endSeason, List<Integer> teams, Boolean wins, Boolean losses, Boolean ties, Integer ruxbeeLimit, Integer bugtonLimit, GameSortType sortType) {
        return gameRepository.findGames(gameNumber(startSeason, startWeek),
                gameNumber(endSeason, endWeek),
                teams,
                wins,
                losses,
                ties,
                ruxbeeLimit,
                bugtonLimit).stream().map(StatsServiceImpl::toApi).collect(Collectors.toList());
    }

    @Override
    public List<TeamPositionStats> teamPositionStats(Integer startSeason, Integer endSeason, List<Integer> teams) {
        return null; // TODO
    }

    @Override
    public PlayerStats player(Integer season, Integer playerNumber) {

        Player player = season >= 2019 ?
                playerRepository.findByPlayerNumber(playerNumber) :
                playerRepository.findByPre2018PlayerNumber(playerNumber);

        if (null == player) return null;

        PlayerStats result = new PlayerStats();
        result.setPlayerNumber(playerNumber(player));
        result.setName(player.getPlayerName());
        result.setGames(player.getWeeks().size());
        result.setWins(Math.toIntExact(player.getWeeks().stream().filter(w -> w.getTeam().isWin()).count()));
        result.setLosses(Math.toIntExact(player.getWeeks().stream().filter(w -> w.getTeam().isLoss()).count()));
        result.setTies(Math.toIntExact(player.getWeeks().stream().filter(w -> w.getTeam().isTie()).count()));
        result.setPoints(player.getWeeks().stream().mapToInt(PlayerWeek::getPoints).sum());
        result.setName(player.getPlayerName());
        result.setPosition(player.getWeeks().stream().findAny().get().getPosition());
        result.setGameStats(player.getWeeks().stream().map(w -> {
            PlayerGameStats s = new PlayerGameStats();
            s.setOpponent(w.getOpponent());
            s.setPlayerTeam(w.getPlayerTeam());
            s.setPoints(w.getPoints());
            s.setScoringPeriod(w.getTeam().getGame().getScoringPeriod());
            s.setSeason(w.getTeam().getGame().getSeason());
            s.setTeamNumber(w.getTeam().getTeamNumber());
            s.setTeamPoints(w.getTeam().getPoints());
            s.setOpponentPoints(w.getTeam().getOpponent().getPoints());
            s.setOpponentTeamNumber(w.getTeam().getOpponent().getTeamNumber());
            return s;
        }).collect(Collectors.toList()));

        return result;
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
                    return result;
                })
                .limit(StatsConstants.RESULT_SIZE)
                .collect(Collectors.toList());
    }

    @Override
    public List<PlayerStats> playersByPoints(List<Integer> teamNumbers,
                                             List<String> positions,
                                             Integer startSeason,
                                             Integer startScoringPeriod,
                                             Integer endSeason,
                                             Integer endScoringPeriod) {

        Integer startGame = gameNumber(startSeason, startScoringPeriod);
        Integer endGame = gameNumber(endSeason, endScoringPeriod);

        List<Player> players = null;

        if (null != teamNumbers) {
            players = playerRepository.findAllByPointsForTeams(
                    teamNumbers,
                    positions,
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
                    result.setPlayerNumber(playerNumber(p));
                    result.setName(p.getPlayerName());
                    result.setGames(weeks.size());
                    result.setWins(Math.toIntExact(weeks.stream().filter(w -> w.getTeam().isWin()).count()));
                    result.setLosses(Math.toIntExact(weeks.stream().filter(w -> w.getTeam().isLoss()).count()));
                    result.setTies(Math.toIntExact(weeks.stream().filter(w -> w.getTeam().isTie()).count()));
                    result.setPosition(weeks.stream().findAny().get().getPosition());
                    result.setPoints(weeks.stream().mapToInt(PlayerWeek::getPoints).sum());
                    result.setPointsPerTeam(pointsPerTeam(weeks));
                    result.setCurrentPointsPerTeam(
                            pointsPerTeam(weeks
                                    .stream()
                                    .filter(w ->
                                            w.getTeam().getGame().getGameNumber() ==
                                                    gameNumber(StatsConstants.CURRENT_SEASON, StatsConstants.CURRENT_SCORING_PERIOD))
                                    .limit(1)
                                    .collect(Collectors.toList()))
                                    .stream()
                                    .findFirst()
                                    .orElse(null));
                    return result;
                })
                .limit(StatsConstants.RESULT_SIZE)
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
            game.setHomePoints(home.getPoints());
            game.setAwayPoints(away.getPoints());

            if (game.getHomePoints() > game.getAwayPoints()) {
                home.setWin(true);
                away.setLoss(true);
            } else if (game.getHomePoints() < game.getAwayPoints()) {
                away.setWin(true);
                home.setLoss(true);
            } else {
                home.setTie(true);
                away.setTie(true);
            }

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
        result.setLowest(result.getPlayers()
                .stream()
                .mapToInt(PlayerWeek::getPoints)
                .min().getAsInt());
        result.setHighest(result.getPlayers()
                .stream()
                .mapToInt(PlayerWeek::getPoints)
                .max().getAsInt());

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
        result.setTotalPoints(game.getHomePoints() + game.getAwayPoints());
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

        return playerName
                .toLowerCase()
                .replaceAll("[^(A-Z|a-z|\\s+)]+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static int gameNumber(Integer season, Integer scoringPeriod) {
        return season * 100 + scoringPeriod;
    }

    private static List<PointsPerTeam> pointsPerTeam(List<PlayerWeek> playerWeeks) {

        if (CollectionUtils.isEmpty(playerWeeks)) return Collections.emptyList();

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

    private static String playerNumber(Player player) {
        return null != player.getPlayerNumber() ? "2019:" + player.getPlayerNumber() : "2018:" + player.getPre2018PlayerNumber();
    }
}
