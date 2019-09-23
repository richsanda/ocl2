package w.whateva.ocl2.api.stats.dto.box;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlayerBoxScore {

    private Integer playerId;
    private String playerName;
    private String playerTeam;
    private String position;
    private String link;
    private String opponent;
    private String gameStatus;
    private Integer points;
    private boolean v3Api;
}
