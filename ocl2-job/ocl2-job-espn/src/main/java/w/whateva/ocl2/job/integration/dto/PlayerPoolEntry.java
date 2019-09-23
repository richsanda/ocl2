package w.whateva.ocl2.job.integration.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerPoolEntry {

    private Integer appliedStatTotal;
    private Integer id;
    private Integer onTeamId;
    private Player player;
}
