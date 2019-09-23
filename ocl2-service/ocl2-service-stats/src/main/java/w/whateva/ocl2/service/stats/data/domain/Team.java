package w.whateva.ocl2.service.stats.data.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.List;

@Getter
@Setter
@Entity
public class Team {

    @Id
    @GeneratedValue
    private Long id;

    private String owner;
    private Integer teamNumber;

    @OneToMany(mappedBy = "team")
    private List<TeamWeek> weeks;
}
