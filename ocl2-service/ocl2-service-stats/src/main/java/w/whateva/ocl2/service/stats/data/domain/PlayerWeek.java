package w.whateva.ocl2.service.stats.data.domain;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.FetchMode;

import javax.persistence.*;

@Getter
@Setter
@Entity
public class PlayerWeek {

    @Id
    @GeneratedValue
    private Long id;

    private Integer playerNumber;
    private String playerName;
    private String playerTeam;
    private String position;
    private String link;
    private String opponent;
    private String gameStatus;
    private Integer points;

    @ManyToOne
    private Player player;

    @ManyToOne
    private TeamWeek team;
}
