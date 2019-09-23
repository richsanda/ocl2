package w.whateva.ocl2.job.beans;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.*;
import org.springframework.batch.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.core.io.Resource;
import org.springframework.util.CollectionUtils;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.job.integration.dto.MMatchup;
import w.whateva.ocl2.job.mapping.GameMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public class MMatchupReader implements ResourceAwareItemReaderItemStream<GameBoxScore> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private InputStream inputStream;
    private List<GameBoxScore> boxScores = new LinkedList<>();

    MMatchupReader() {

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void setResource(Resource resource) {

        try {

            this.inputStream = resource.getInputStream();

            MMatchup mMatchup = objectMapper.readValue(inputStream, MMatchup.class);

            boxScores.addAll(GameMapper.toBoxScores(mMatchup));

        } catch (IOException e) {

            //
        }
    }

    @Override
    public GameBoxScore read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (CollectionUtils.isEmpty(boxScores)) return null;
        return boxScores.remove(0);
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {

    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {

    }

    @Override
    public void close() throws ItemStreamException {
        try {
            inputStream.close();
        } catch (IOException e) {
            //
        }
    }
}
