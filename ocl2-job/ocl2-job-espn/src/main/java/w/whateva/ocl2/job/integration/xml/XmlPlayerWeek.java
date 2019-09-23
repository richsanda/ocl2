package w.whateva.ocl2.job.integration.xml;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * Created by rich on 10/15/15.
 */
public class XmlPlayerWeek {

    private String playerId;
    private String playerName;
    private String playerTeam;
    private String position;
    private String link;
    private String opponent;
    private String gameStatus;
    private int points;
    private boolean initialized = false;

    @XmlAttribute(name = "id")
    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    @XmlElement(name = "player-name")
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    @XmlElement
    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    @XmlElement
    public String getOpponent() {
        return opponent;
    }

    public void setOpponent(String opponent) {
        this.opponent = opponent;
    }

    @XmlElement(name = "game-status")
    public String getGameStatus() {
        return gameStatus;
    }

    public void setGameStatus(String gameStatus) {
        this.gameStatus = gameStatus;
    }

    @XmlElement()
    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public String getPlayerTeam() {
        return playerTeam;
    }

    public void setPlayerTeam(String playerTeam) {
        this.playerTeam = playerTeam;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public void init() {

        try {

            String name = playerName;
            String[] splitName = name.split(new String(new char[]{160}));
            position = splitName[1].trim();

            if (name.contains(",")) {
                splitName = splitName[0].split(",");
                playerName = splitName[0].trim();
                playerTeam = splitName[1].trim();
            } else {
                playerName = splitName[0].trim();
            }

            initialized = true;

        } catch (Exception e) {
            System.out.println(e.getMessage());
            // pass
        }
    }
}
