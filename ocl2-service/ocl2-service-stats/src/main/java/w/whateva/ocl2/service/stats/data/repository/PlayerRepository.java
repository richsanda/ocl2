package w.whateva.ocl2.service.stats.data.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import w.whateva.ocl2.service.stats.data.domain.Player;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface PlayerRepository extends CrudRepository<Player, Long> {

    Player findByPlayerNumber(Integer playerNumber);

    Player findByPre2018PlayerNumber(Integer pre2018PlayerNumber);

    List<Player> findByPlayerNameIndexed(String name);

    //Player findByPlayerWeek();

    @Query("select p from Player p order by p.weeks.size desc")
    List<Player> findAllByAppearances();

    @Query("select p from Player p join p.weeks pw group by p.id order by sum(pw.points) desc")
    List<Player> findAllByPoints();

    @Query("select p from Player p join p.weeks pw join pw.team t where t.teamNumber in :teamNumbers and t.gameNumber >= :startGame and t.gameNumber <= :endGame group by p.id, p.playerName order by sum(pw.points) desc")
    List<Player> findAllByPointsForTeams(@Param("teamNumbers") List<Integer> teamNumber,
                                         @Param("startGame") Integer startGame,
                                         @Param("endGame") Integer endGame);
}
