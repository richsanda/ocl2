package w.whateva.ocl2.service.stats.data.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import w.whateva.ocl2.service.stats.data.domain.Game;

import java.util.List;

@Repository
public interface GameRepository extends CrudRepository<Game, Long> {

    Game findBySeasonAndScoringPeriodAndHomeTeamNumber(Integer season, Integer scoringPeriod, Integer homeTeamNumber);

    Game findBySeasonAndScoringPeriodAndAwayTeamName(Integer season, Integer scoringPeriod, String awayTeamName);

    List<Game> findBySeasonAndScoringPeriod(Integer season, Integer scoringPeriod);

    @Query("select g from Game g join g.teamWeeks w where g.season = :season and g.scoringPeriod = :scoringPeriod and w.teamNumber = :teamNumber")
    Game findGame(@Param("season") Integer season,
                  @Param("scoringPeriod") Integer scoringPeriod,
                  @Param("teamNumber") Integer teamNumber);

    @Query(
            "SELECT g FROM Game g join g.teamWeeks w WHERE " +
                    "g.gameNumber >= :startGameNumber " +
                    "AND g.gameNumber <= :endGameNumber " +
                    "AND w.teamNumber IN :teamNumbers " +
                    "AND ((w.win = true AND :wins = true) OR " +
                    "(w.loss = true AND :losses = true) OR " +
                    "(w.tie = true AND :ties = true)) " +
                    "AND ((:ruxbeeLimit IS NULL) OR (:ruxbeeLimit <= w.lowest)) " +
                    "AND ((:bugtonLimit IS NULL) OR (:bugtonLimit >= w.highest)) " +
                    "GROUP BY g.id " +
                    "ORDER BY max(w.points) DESC"
    )
    List<Game> findGames(@Param("startGameNumber") Integer startGameNumber,
                         @Param("endGameNumber") Integer endGameNumber,
                         @Param("teamNumbers") List<Integer> teamNumbers,
                         @Param("wins") Boolean wins,
                         @Param("losses") Boolean losses,
                         @Param("ties") Boolean ties,
                         @Param("ruxbeeLimit") Integer ruxbeeLimit,
                         @Param("bugtonLimit") Integer bugtonLimit);
}
