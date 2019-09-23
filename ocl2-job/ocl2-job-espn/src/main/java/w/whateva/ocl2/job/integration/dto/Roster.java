package w.whateva.ocl2.job.integration.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Roster {

    private Integer appliedStatTotal;
    private List<RosterEntry> entries;
}
