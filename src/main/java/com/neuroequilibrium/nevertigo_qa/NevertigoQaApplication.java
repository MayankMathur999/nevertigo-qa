
package com.neuroequilibrium.nevertigo_qa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class NevertigoQaApplication {

    public static void main(String[] args) {
        SpringApplication.run(NevertigoQaApplication.class, args);
    }
}
