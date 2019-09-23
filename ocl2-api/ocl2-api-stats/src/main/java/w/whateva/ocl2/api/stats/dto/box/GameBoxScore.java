package w.whateva.ocl2.api.stats.dto.box;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GameBoxScore {

    private int season;
    private int scoringPeriod;
    private int totalPoints;
    private TeamBoxScore home;
    private TeamBoxScore away;
}
