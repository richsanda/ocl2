package w.whateva.ocl2.job.espn;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.job.integration.dto.MMatchup;
import w.whateva.ocl2.job.mapping.GameMapper;

import java.io.IOException;
import java.util.List;

public class AppTest {

    @Test
    public void parseSchedule() throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        MMatchup mMatchup = objectMapper.readValue(getClass().getResourceAsStream("/2019.01.json"), MMatchup.class);

        List<GameBoxScore> boxScores = GameMapper.toBoxScores(mMatchup);

        System.out.println(boxScores.size());
    }
}
