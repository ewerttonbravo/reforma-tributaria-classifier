package com.ebravo.taxclassifier;

import com.ebravo.taxclassifier.adapter.cli.PublishCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaxClassifierApplication {

    static void main(String[] args) {
        var context = application(args).run(args);
        if (PublishCommand.isRequested(args)) {
            System.exit(SpringApplication.exit(context));
        }
    }

    static SpringApplication application(String... args) {
        return new SpringApplication(TaxClassifierApplication.class);
    }
}
