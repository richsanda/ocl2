package w.whateva.ocl2.job.integration.xml;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * Created by rich on 10/15/15.
 */
@XmlRootElement(name = "game")
public class XmlGame {

    private String file;
    private int season;
    private int scoringPeriod;
    private int teamNumber;
    private int points;
    private int opponentPoints;
    private int totalPoints;
    private boolean win;
    private boolean loss;
    private boolean tie;
    private boolean ruxbee;
    private boolean bugton;
    private boolean initialized = false;

    private List<XmlTeamWeek> teamWeeks;

    @XmlElement(name = "team")
    public List<XmlTeamWeek> getTeamWeeks() {
        return teamWeeks;
    }

    public void setTeamWeeks(List<XmlTeamWeek> teamWeeks) {
        this.teamWeeks = teamWeeks;
    }

    @XmlAttribute(name = "file")
    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    @XmlAttribute(name = "season")
    public int getSeason() {
        return season;
    }

    public void setSeason(int season) {
        this.season = season;
    }

    @XmlAttribute(name = "team")
    public int getTeamNumber() {
        return teamNumber;
    }

    public void setTeamNumber(int teamNumber) {
        this.teamNumber = teamNumber;
    }

    @XmlAttribute(name = "scoringPeriod")
    public int getScoringPeriod() {
        return scoringPeriod;
    }

    public void setScoringPeriod(int scoringPeriod) {
        this.scoringPeriod = scoringPeriod;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public void init() {

        try {

            initialized = true;

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }


        teamWeeks.forEach(XmlTeamWeek::init);

        points = teamWeeks.get(0).getPoints();
        opponentPoints = teamWeeks.get(opponentTeamWeekIndex()).getPoints();
        totalPoints = points + opponentPoints;
        win = points > opponentPoints;
        tie = points == opponentPoints;
        loss = points < opponentPoints;
    }

    @Override
    public String toString() {
        return "" + season + "; " + scoringPeriod + "; " + teamNumber;
    }

    public boolean isWin() {
        return win;
    }

    public boolean isTie() {
        return tie;
    }

    public boolean isLoss() {
        return loss;
    }

    public boolean isWinOrTie() {
        return isWin() || isTie();
    }

    public boolean isLossOrTie() {
        return isLoss() || isTie();
    }

    public boolean isRegularSeasonGame() {
        return scoringPeriod <= 14;
    }

    public boolean isPlayoffGame() {
        return !isRegularSeasonGame();
    }

    public boolean isLastGameOfSeason(boolean includePlayoffs) {
        if (includePlayoffs && scoringPeriod == 16) {
            return true;
        } else if (scoringPeriod == 14) {
            return true;
        }
        return false;
    }

    public boolean isFirstGameOfSeason() {
        return scoringPeriod == 1;
    }

    public boolean isRuxbee() { return ruxbee; }

    public boolean isBugton() { return bugton; }

    public XmlTeamWeek getTeamWeek() {
        return teamWeeks.get(0);
    }

    public XmlTeamWeek opponentTeamWeek() {
        return teamWeeks.get(opponentTeamWeekIndex());
    }

    // in 2015, bench "teams" began recording, so from then on, get team week at index 2 instead of 1
    private int opponentTeamWeekIndex() {
        // return season >= 2015 ? 2 : 1;
        return 1;
    }
}
