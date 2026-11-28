package com.ebravo.taxclassifier.adapter.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductMessageTest {

    @ParameterizedTest
    @MethodSource("productsMissingAField")
    void productMissingAFieldFailsWithItsName(ProductMessage message, String cause) {
        assertThatThrownBy(message::toProduct).isInstanceOf(NullPointerException.class).hasMessage(cause);
    }

    static Stream<Arguments> productsMissingAField() {
        return Stream.of(
                Arguments.of(new ProductMessage(null, "Arroz", "10063021", "5.00"), "the product has no code"),
                Arguments.of(new ProductMessage("P001", null, "10063021", "5.00"), "the product has no description"),
                Arguments.of(new ProductMessage("P001", "Arroz", null, "5.00"), "the product has no ncm"),
                Arguments.of(new ProductMessage("P001", "Arroz", "10063021", null), "the product has no price"));
    }
}
