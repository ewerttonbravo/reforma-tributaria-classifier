package com.ebravo.taxclassifier.adapter.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.ebravo.taxclassifier.application.PublishProducts;
import com.ebravo.taxclassifier.domain.Product;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublishCommandTest {

    private static final String HEADER = "code;description;ncm;price";

    @TempDir
    Path dir;

    private final List<Product> published = new ArrayList<>();
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void validFileIsPublishedAndExitsZero() throws IOException {
        var file = csv(HEADER, "P001;Arroz tipo 1 5kg;10063021;25.90");

        assertThat(run("publish", file.toString())).isZero();
        assertThat(published).extracting(Product::code).containsExactly("P001");
        assertThat(out.toString(UTF_8)).isEqualTo("products published: 1, lines rejected: 0%n".formatted());
        assertThat(err.toString(UTF_8)).isEmpty();
    }

    @Test
    void rejectedLinesGoToStderrAndExitOne() throws IOException {
        var file = csv(HEADER, "P001;Arroz tipo 1 5kg;1006;25.90", "P002;Feijão carioca 1kg;07133319;8.49");

        assertThat(run("publish", file.toString())).isEqualTo(1);
        assertThat(published).extracting(Product::code).containsExactly("P002");
        assertThat(err.toString(UTF_8)).isEqualTo("line 2: NCM must have 8 digits%n".formatted());
        assertThat(out.toString(UTF_8)).isEqualTo("products published: 1, lines rejected: 1%n".formatted());
    }

    @Test
    void wrongHeaderCountsNoProductAsPublished() throws IOException {
        var file = csv("description;code;ncm;price", "Arroz tipo 1 5kg;P001;10063021;25.90");

        assertThat(run("publish", file.toString())).isEqualTo(1);
        assertThat(out.toString(UTF_8)).isEqualTo("products published: 0, lines rejected: 2%n".formatted());
    }

    @Test
    void byteOrderMarkIsRemovedBeforeParsing() throws IOException {
        // Excel writes a UTF-8 BOM; left in place it makes the header wrong and every line is rejected.
        var file = csv("\uFEFF" + HEADER, "P001;Arroz tipo 1 5kg;10063021;25.90");

        assertThat(run("publish", file.toString())).isZero();
        assertThat(published).extracting(Product::code).containsExactly("P001");
    }

    private int run(String... args) {
        var command = new PublishCommand(new PublishProducts(published::addAll));
        return command.publish(List.of(args), new PrintStream(out, true, UTF_8), new PrintStream(err, true, UTF_8));
    }

    private Path csv(String... lines) throws IOException {
        return Files.write(dir.resolve("products.csv"), List.of(lines), UTF_8);
    }
}
