package w.whateva.ocl2.job.beans;

import org.springframework.batch.item.ItemProcessor;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.api.stats.dto.box.PlayerBoxScore;
import w.whateva.ocl2.api.stats.dto.box.TeamBoxScore;
import w.whateva.ocl2.job.integration.xml.XmlGame;
import w.whateva.ocl2.job.integration.xml.XmlPlayerWeek;
import w.whateva.ocl2.job.integration.xml.XmlTeamWeek;

import java.util.stream.Collectors;

/**
 * Created by rich on 10/15/15.
 */
public class XmlGameProcessor implements ItemProcessor<XmlGame, GameBoxScore> {

    @Override
    public GameBoxScore process(XmlGame game) throws Exception {

        game.init();

        GameBoxScore result = new GameBoxScore();
        result.setSeason(game.getSeason());
        result.setScoringPeriod(game.getScoringPeriod());

        TeamBoxScore home = teamBoxScore(game.getTeamWeek());
        home.setTeamNumber(game.getTeamNumber());
        result.setHome(home);

        TeamBoxScore away = teamBoxScore(game.opponentTeamWeek());
        result.setAway(away);

        return result;
    }

    private static TeamBoxScore teamBoxScore(XmlTeamWeek xmlTeamWeek) {

        TeamBoxScore result = new TeamBoxScore();
        result.setHeader(xmlTeamWeek.getHeader());
        result.setPoints(xmlTeamWeek.getPoints());
        result.setPlayers(xmlTeamWeek.getPlayerWeeks()
                .stream()
                .map(XmlGameProcessor::playerBoxScore)
                .collect(Collectors.toList()));
        return result;
    }

    private static PlayerBoxScore playerBoxScore(XmlPlayerWeek xmlPlayerWeek) {

        PlayerBoxScore result = new PlayerBoxScore();
        result.setPlayerId(playerId(xmlPlayerWeek.getPlayerId()));
        result.setPoints(xmlPlayerWeek.getPoints());
        result.setGameStatus(xmlPlayerWeek.getGameStatus());
        result.setPlayerName(xmlPlayerWeek.getPlayerName());
        result.setLink(xmlPlayerWeek.getLink());
        result.setOpponent(xmlPlayerWeek.getOpponent());
        result.setPlayerTeam(xmlPlayerWeek.getPlayerTeam());
        result.setPosition(xmlPlayerWeek.getPosition());
        result.setV3Api(false);

        return result;
    }

    private static Integer playerId(String playerId) {
        try {
            return Integer.parseInt(playerId.replaceAll("[^0-9]+", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
