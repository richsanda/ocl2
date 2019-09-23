package w.whateva.ocl2.api.stats.dto.box;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TeamBoxScore {

    private Integer season;
    private Integer scoringPeriod;
    private Integer teamNumber;
    private String header;
    private Integer points;
    private Boolean win;
    private Boolean loss;
    private Boolean tie;
    private Boolean ruxbee;
    private Boolean bugton;
    private List<PlayerBoxScore> players;
}
