package w.whateva.ocl2.app.espn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import w.whateva.ocl2.api.stats.StatsService;
import w.whateva.service.utilities.controller.AutoControllers;

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
@AutoControllers(apis = {StatsService.class})
public class OclStatsApplication {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(OclStatsApplication.class, args);
    }
}
