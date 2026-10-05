package io.wulfcodes.messaging.ui;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MessagingUiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessagingUiApplication.class, args);
    }
}
