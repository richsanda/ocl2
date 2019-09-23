package w.whateva.ocl2.job.beans;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.MapJobRepositoryFactoryBean;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.MultiResourceItemReader;
import org.springframework.batch.item.file.ResourceAwareItemReaderItemStream;
import org.springframework.batch.item.xml.StaxEventItemReader;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import w.whateva.ocl2.api.stats.StatsService;
import w.whateva.ocl2.api.stats.dto.box.GameBoxScore;
import w.whateva.ocl2.job.integration.xml.XmlGame;

import javax.persistence.EntityManagerFactory;
import java.io.IOException;

/**
 * Created by rich on 10/15/15.
 */
@Configuration
@EnableBatchProcessing()
public class Ocl2BatchConfiguration extends DefaultBatchConfigurer {

    @Autowired
    private JobBuilderFactory jobs;

    @Autowired
    private StepBuilderFactory steps;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private StatsService statsService;

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public Job oclLoadJob() throws Exception {
        return jobs
                .get("oclLoadJob")
                .start(loadXmlStep())
                .next(loadMatchupsStep())
                .build();
    }

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Bean
    protected Step loadMatchupsStep() throws Exception {
        return steps.get("loadMatchupsStep")
                .<GameBoxScore, GameBoxScore>chunk(1)
                .reader(matchupsReader())
                .writer(gameBoxScoreWriter())
                .build();
    }

    @Bean
    protected Step loadXmlStep() throws Exception {
        return steps.get("loadXmlStep")
                .<XmlGame, GameBoxScore>chunk(1)
                .reader(xmlGameReader())
                .processor(xmlGameProcessor())
                .writer(gameBoxScoreWriter())
                .build();
    }

    @Bean
    @StepScope
    protected ItemReader<GameBoxScore> matchupsReader() throws IOException {
        MultiResourceItemReader<GameBoxScore> reader = new MultiResourceItemReader<>();
        String path = "matchups/*.json";
        Resource[] resources = new PathMatchingResourcePatternResolver(resourceLoader).getResources(path);
        reader.setResources(resources);
        reader.setDelegate(mMatchupReader());
        return reader;
    }

    @Bean
    @StepScope
    protected ResourceAwareItemReaderItemStream<GameBoxScore> mMatchupReader() {
        return new MMatchupReader();
    }

    @Bean
    protected ItemWriter<GameBoxScore> gameBoxScoreWriter() {
        GameBoxScoreWriter writer = new GameBoxScoreWriter();
        writer.setStatsService(statsService);
        return writer;
    }

    @Bean
    protected Step oclLoadStep() throws Exception {
        return this.steps.get("oclLoadStep")
                .<XmlGame, GameBoxScore>chunk(10)
                .reader(xmlGameReader())
                .processor(xmlGameProcessor())
                .writer(gameBoxScoreWriter())
                .build();
    }

    @Bean
    @StepScope
    protected ItemReader<XmlGame> xmlGameReader() throws IOException {
        MultiResourceItemReader<XmlGame> reader = new MultiResourceItemReader<>();
        //String path = "file:/Users/rich/Downloads/games/*.xml";
        String path = "games/*.xml";
        Resource[] resources = new PathMatchingResourcePatternResolver(resourceLoader).getResources(path);
        reader.setResources(resources);
        reader.setDelegate(oneXmlGameReader());
        return reader;
    }

    @Bean
    @StepScope
    protected ResourceAwareItemReaderItemStream<XmlGame> oneXmlGameReader() {
        StaxEventItemReader<XmlGame> reader = new StaxEventItemReader<>();
        reader.setFragmentRootElementName("game");
        reader.setUnmarshaller(gameUnmarshaller());
        return reader;
    }

    @Bean
    @StepScope
    protected ItemProcessor<XmlGame, GameBoxScore> xmlGameProcessor() {
        XmlGameProcessor writer = new XmlGameProcessor();
        return writer;
    }

    @Bean
    protected Jaxb2Marshaller gameUnmarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setClassesToBeBound(XmlGame.class);
        return marshaller;
    }
}
