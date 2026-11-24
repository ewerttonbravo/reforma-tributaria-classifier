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

    /** The publish command runs in the {@code publish} profile, which leaves the classification listener out. */
    static SpringApplication application(String... args) {
        var application = new SpringApplication(TaxClassifierApplication.class);
        if (PublishCommand.isRequested(args)) {
            application.setAdditionalProfiles(PublishCommand.PROFILE);
        }
        return application;
    }
}
