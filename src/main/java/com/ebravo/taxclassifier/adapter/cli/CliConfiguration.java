package com.ebravo.taxclassifier.adapter.cli;

import com.ebravo.taxclassifier.application.ProductPublisher;
import com.ebravo.taxclassifier.application.PublishProducts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class CliConfiguration {

    @Bean
    PublishProducts publishProducts(ProductPublisher publisher) {
        return new PublishProducts(publisher);
    }
}
