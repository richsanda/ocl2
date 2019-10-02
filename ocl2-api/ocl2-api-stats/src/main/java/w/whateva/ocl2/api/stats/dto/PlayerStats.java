package w.whateva.ocl2.api.stats.dto;

import lombok.Getter;
import lombok.Setter;
import w.whateva.ocl2.api.stats.PointsPerTeam;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class PlayerStats {

    private String playerNumber;
    private String position;
    private String name;
    private Integer games;
    private Integer wins;
    private Integer losses;
    private Integer ties;
    private Integer points;
    private List<PointsPerTeam> pointsPerTeam;
    private PointsPerTeam currentPointsPerTeam;
    private List<PlayerGameStats> gameStats;
}
