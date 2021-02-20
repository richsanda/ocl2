package w.whateva.ocl2.app.espn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {
        "w.whateva.ocl2.service",
        "w.whateva.ocl2.job",
        "w.whateva.ocl2.app",
        "w.whateva.ocl2.web"
})
@EnableJpaRepositories(basePackages = {
        "w.whateva.ocl2.service.stats.data.repository",
})
@EntityScan(basePackages = {
        "w.whateva.ocl2.service.stats.data.domain",
})
public class OclStatsApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(OclStatsApplication.class, args);
    }

    @Bean
    ObjectMapper objectMapper() {
        return new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }
}
