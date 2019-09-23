package w.whateva.ocl2.job.integration.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Game {

    private Team away;
    private Team home;
    private Integer id;
    private Integer matchupPeriodId;
    private String winner;
}
