package w.whateva.ocl2.api.stats.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TeamPlayerStats {

    private String name;
    private Integer team;
    private Week start;
    private Week end;
    private List<PlayerStats> players;
}
