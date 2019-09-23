package w.whateva.ocl2.job.integration.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Team {

    private Integer gamesPlayed;
    private Object pointsByScoringPeriod;
    private Roster rosterForCurrentScoringPeriod;
    private Integer teamId;
    private Integer totalPoints;
}
