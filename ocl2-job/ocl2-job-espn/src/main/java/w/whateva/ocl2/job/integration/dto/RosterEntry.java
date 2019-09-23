package w.whateva.ocl2.job.integration.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RosterEntry {

    private Integer playerId;
    private Integer lineupSlotId;
    private PlayerPoolEntry playerPoolEntry;
}
