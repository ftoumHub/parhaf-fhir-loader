package fr.ftoumhub.parhaf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ParhafLoaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParhafLoaderApplication.class, args).close();
    }
}
