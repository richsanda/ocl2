package w.whateva.ocl2.service.stats.data.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Entity
public class Game {

    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private int season;
    @Column(nullable = false)
    private int scoringPeriod;
    @Column(nullable = false)
    private int homeTeamNumber;
    private String homeTeamName;
    private Integer awayTeamNumber;
    private String awayTeamName;
    private int homePoints;
    private int awayPoints;
    private int gameNumber;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<TeamWeek> teamWeeks;

    @Transient
    public TeamWeek getHome() {
        return teamWeeks.get(0);
    }

    @Transient
    public TeamWeek getAway() {
        return teamWeeks.get(1);
    }
}
