package w.whateva.ocl2.job.integration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MMatchup {

    private List<Game> schedule;
    private Integer seasonId;
    private Integer scoringPeriodId;
}
