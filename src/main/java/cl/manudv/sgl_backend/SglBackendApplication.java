package cl.manudv.sgl_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"cl.sgl", "cl.manudv.sgl_backend"})
@EnableJpaRepositories(basePackages = "cl.sgl.repository")
@EntityScan(basePackages = "cl.sgl.entity")
@EnableScheduling
public class SglBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SglBackendApplication.class, args);
    }

}
