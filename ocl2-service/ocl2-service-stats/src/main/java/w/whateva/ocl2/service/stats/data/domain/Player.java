package w.whateva.ocl2.service.stats.data.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.List;

@Entity
@Getter
@Setter
public class Player {

    @Id
    @GeneratedValue
    private Long id;

    private Integer pre2018PlayerNumber;
    private Integer playerNumber;
    private String playerName;
    private String playerNameIndexed;

    @OneToMany(mappedBy = "player")
    List<PlayerWeek> weeks;
}
