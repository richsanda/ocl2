package w.whateva.ocl2.job.mapping;

import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.api.stats.dto.box.PlayerBoxScore;
import w.whateva.ocl2.api.stats.dto.box.TeamBoxScore;
import w.whateva.ocl2.job.integration.dto.*;
import w.whateva.ocl2.job.integration.xml.XmlGame;
import w.whateva.ocl2.job.integration.xml.XmlPlayerWeek;
import w.whateva.ocl2.job.integration.xml.XmlTeamWeek;

import java.util.List;
import java.util.stream.Collectors;

public class GameMapper {

    public static List<GameBoxScore> toBoxScores(MMatchup mMatchup) {

        Integer seasonId = mMatchup.getSeasonId();
        Integer scoringPeriodId = mMatchup.getScoringPeriodId();

        return mMatchup.getSchedule()
                .stream()
                .map(g -> toBoxScore(seasonId, scoringPeriodId, g))
                .filter(GameMapper::legit)
                .collect(Collectors.toList());
    }

    private static GameBoxScore toBoxScore(Integer seasonId, Integer scoringPeriodId, Game game) {

        GameBoxScore result = new GameBoxScore();
        result.setAway(toBoxScore(game.getAway()));
        result.setHome(toBoxScore(game.getHome()));
        result.setScoringPeriod(scoringPeriodId);
        result.setSeason(seasonId);

        return result;
    }

    private static boolean legit(GameBoxScore boxScore) {
        return null != boxScore.getAway() && null != boxScore.getHome();
    }

    private static TeamBoxScore toBoxScore(Team team) {

        if (null == team.getRosterForCurrentScoringPeriod()) {
            return null;
        }

        TeamBoxScore result = new TeamBoxScore();
        result.setPoints(team.getTotalPoints());
        result.setTeamNumber(team.getTeamId());
        result.setPlayers(team.getRosterForCurrentScoringPeriod().getEntries()
                .stream()
                .filter(GameMapper::active)
                .map(GameMapper::toBoxScore)
                .collect(Collectors.toList()));

        return result;
    }

    private static PlayerBoxScore toBoxScore(RosterEntry player) {

        PlayerBoxScore result = new PlayerBoxScore();
        result.setPlayerId(player.getPlayerId());
        result.setV3Api(true);
        updateBoxScore(result, player.getPlayerPoolEntry());

        return result;
    }

    private static void updateBoxScore(PlayerBoxScore boxScore, PlayerPoolEntry player) {

        boxScore.setPlayerId(player.getId());
        boxScore.setPoints(player.getAppliedStatTotal());
        updateBoxScore(boxScore, player.getPlayer());
    }

    private static void updateBoxScore(PlayerBoxScore boxScore, Player player) {

        boxScore.setPlayerName(player.getFullName());
        boxScore.setPosition(position(player.getDefaultPositionId()));
        boxScore.setPlayerTeam(proTeam(player.getProTeamId()));
    }

    private static String position(Integer in) {

        switch (in) {
            case 1 : return "QB";
            case 2 : return "RB";
            case 3 : return "WR";
            case 4 : return "TE";
            case 16 : return "D/ST";
            case 5 : return "K";
            default : return "?";
        }
    }

    private static boolean active(RosterEntry entry) {
        return entry.getLineupSlotId() != 20 && entry.getLineupSlotId() != 21;
    }

    private static String proTeam(Integer in) {

        switch (in) {
            case 1 : return "Atl";
            case 2 : return "Buf";
            case 3 : return "Chi";
            case 4 : return "Cin";
            case 5 : return "Cle";
            case 6 : return "Dal";
            case 7 : return "Den";
            case 8 : return "Det";
            case 9 : return "GB";
            case 10 : return "Ten";
            case 11 : return "Ind";
            case 12 : return "KC";
            case 13 : return "Oak";
            case 14 : return "LAR";
            case 15 : return "Mia";
            case 16 : return "Min";
            case 17 : return "NE";
            case 18 : return "NO";
            case 19 : return "NYG";
            case 20 : return "NYJ";
            case 21 : return "Phi";
            case 22 : return "Ari";
            case 23 : return "Pit";
            case 24 : return "LAC";
            case 25 : return "SF";
            case 26 : return "Sea";
            case 27 : return "TB";
            case 28 : return "Was";
            case 29 : return "Car";
            case 30 : return "Jac";
            case 31 : return "31";
            case 32 : return "32";
            case 33 : return "Bal";
            case 34 : return "Hou";
            default : return "?";
        }
    }

    private static GameBoxScore toBoxScore(Integer seasonId, Integer scoringPeriodId, XmlGame game) {

        GameBoxScore result = new GameBoxScore();
        result.setAway(toBoxScore(game.opponentTeamWeek(), null));
        result.setHome(toBoxScore(game.getTeamWeek(), game.getTeamNumber()));
        result.setScoringPeriod(scoringPeriodId);
        result.setSeason(seasonId);

        return result;
    }

    private static TeamBoxScore toBoxScore(XmlTeamWeek team, Integer teamNumber) {

        TeamBoxScore result = new TeamBoxScore();
        result.setTeamNumber(teamNumber);
        result.setPoints(team.getPoints());
        result.setHeader(team.getHeader());
        result.setPlayers(team.getPlayerWeeks()
                .stream()
                .map(GameMapper::toBoxScore)
                .collect(Collectors.toList()));

        return result;
    }

    private static PlayerBoxScore toBoxScore(XmlPlayerWeek player) {

        PlayerBoxScore result = new PlayerBoxScore();
        result.setPlayerId(Integer.parseInt(player.getPlayerId()));
        result.setPlayerName(player.getPlayerName());
        result.setPosition(player.getPosition());
        result.setGameStatus(player.getGameStatus());
        result.setPlayerTeam(player.getPlayerTeam());
        result.setLink(player.getLink());
        result.setOpponent(player.getOpponent());
        result.setPoints(player.getPoints());
        result.setV3Api(false);

        return result;
    }
}
