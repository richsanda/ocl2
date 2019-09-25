package w.whateva.ocl2.service.stats.data.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.List;

@Getter
@Setter
@Entity
public class TeamWeek {

    @Id
    @GeneratedValue
    private Long id;

    private Integer teamNumber;
    private String header;
    private int points = 0;
    private int lowest;
    private int highest;
    private int gameNumber;
    private boolean win = false;
    private boolean loss = false;
    private boolean tie = false;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, mappedBy = "team")
    private List<PlayerWeek> players;

    @ManyToOne
    private Team team;

    @ManyToOne
    private Game game;

    @Transient
    public TeamWeek getOpponent() {
        return isHome() ? getGame().getAway() : getGame().getHome();
    }

    @Transient
    public boolean isHome() {
        return getGame().getHome().equals(this);
    }

    @Transient
    public Integer getGameNumber() {
        return game.getGameNumber();
    }
}
