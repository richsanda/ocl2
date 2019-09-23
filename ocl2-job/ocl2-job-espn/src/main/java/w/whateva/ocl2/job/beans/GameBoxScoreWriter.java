package w.whateva.ocl2.job.beans;

import org.springframework.batch.item.ItemWriter;
import w.whateva.ocl2.api.stats.StatsService;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;

import java.util.List;

public class GameBoxScoreWriter implements ItemWriter<GameBoxScore> {

    private StatsService statsService;

    public void setStatsService(StatsService statsService) {
        this.statsService = statsService;
    }

    @Override
    public void write(List<? extends GameBoxScore> items) throws Exception {

        items.forEach(i -> {

            System.out.println(
                    String.format("%d at %d, week %d, %d",
                            i.getAway().getTeamNumber(),
                            i.getHome().getTeamNumber(),
                            i.getScoringPeriod(),
                            i.getSeason())
            );

            statsService.addGame(i);
        });
    }
}
