package w.whateva.ocl2.job.integration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Player {

    private Integer defaultPositionId;
    private List<Integer> eligibleSlots;
    private String fullName;
    private Integer proTeamId;
}
