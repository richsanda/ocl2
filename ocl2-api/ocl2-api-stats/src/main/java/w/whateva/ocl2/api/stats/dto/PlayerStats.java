package w.whateva.ocl2.api.stats.dto;

import lombok.Getter;
import lombok.Setter;
import w.whateva.ocl2.api.stats.PointsPerTeam;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class PlayerStats {

    private Integer rank;
    private String position;
    private String name;
    private Integer games;
    private Integer points;
    private Double average;
    private List<PointsPerTeam> pointsPerTeam;
}
