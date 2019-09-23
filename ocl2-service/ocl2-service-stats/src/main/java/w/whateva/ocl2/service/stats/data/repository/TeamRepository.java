package w.whateva.ocl2.service.stats.data.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import w.whateva.ocl2.service.stats.data.domain.Team;

import java.util.List;

@Repository
public interface TeamRepository extends CrudRepository<Team, Long> {

    List<Team> findByTeamNumber(Integer teamNumber);
    Team findByOwner(String owner);
}
