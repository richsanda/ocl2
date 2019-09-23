package w.whateva.ocl2.api.stats.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PositionStats {

    private Integer position;
    private List<PlayerStats> playerStats;
}
