package w.whateva.ocl2.service.stats.data.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import w.whateva.ocl2.service.stats.data.domain.Game;

import java.util.List;

@Repository
public interface GameRepository extends CrudRepository<Game, Long> {

    Game findBySeasonAndScoringPeriodAndHomeTeamNumber(Integer season, Integer scoringPeriod, Integer homeTeamNumber);

    Game findBySeasonAndScoringPeriodAndAwayTeamName(Integer season, Integer scoringPeriod, String awayTeamName);

    List<Game> findBySeasonAndScoringPeriod(Integer season, Integer scoringPeriod);

    List<Game> findAllByOrderByTotalPointsDesc();
}
