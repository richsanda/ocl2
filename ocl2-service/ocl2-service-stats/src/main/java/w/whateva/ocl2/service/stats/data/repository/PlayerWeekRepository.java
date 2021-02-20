package w.whateva.ocl2.service.stats.data.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import w.whateva.ocl2.service.stats.data.domain.PlayerWeek;

import java.util.List;

public interface PlayerWeekRepository extends CrudRepository<PlayerWeek, Long> {

    @Query(
            "SELECT p FROM PlayerWeek p GROUP BY p.id ORDER BY max(p.points) DESC"
    )
    List<PlayerWeek> findPlayerWeeks();
}
