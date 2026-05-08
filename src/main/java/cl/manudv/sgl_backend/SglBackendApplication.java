package cl.manudv.sgl_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"cl.sgl", "cl.manudv.sgl_backend"})
public class SglBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SglBackendApplication.class, args);
    }

}
