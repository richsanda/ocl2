package w.whateva.ocl2.api.stats.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerGameStats {

    private Integer season;
    private Integer scoringPeriod;
    private Integer points;
    private Integer teamNumber;
    private String playerTeam;
    private String opponent;
}
