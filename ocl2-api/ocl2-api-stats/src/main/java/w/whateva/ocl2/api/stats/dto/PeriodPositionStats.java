package w.whateva.ocl2.api.stats.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PeriodPositionStats {

    private Integer season;
    private Week start;
    private Week end;
    private List<PositionStats> positions;
}
