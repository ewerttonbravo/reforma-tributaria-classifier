package com.ebravo.taxclassifier;

import com.ebravo.taxclassifier.adapter.cli.PublishCommand;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaxClassifierApplication {

    static void main(String[] args) {
        var context = SpringApplication.run(TaxClassifierApplication.class, args);
        if (args.length > 0 && args[0].equals(PublishCommand.NAME)) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
