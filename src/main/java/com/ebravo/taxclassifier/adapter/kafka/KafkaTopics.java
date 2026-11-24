package com.ebravo.taxclassifier.adapter.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class KafkaTopics {

    /** Three partitions leave room for the two consumer instances of the rebalance scenario. One replica: a single local broker. */
    @Bean
    NewTopic products() {
        return TopicBuilder.name(KafkaProductPublisher.TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic classified() {
        return TopicBuilder.name(ClassificationListener.CLASSIFIED_TOPIC).partitions(3).replicas(1).build();
    }
}
