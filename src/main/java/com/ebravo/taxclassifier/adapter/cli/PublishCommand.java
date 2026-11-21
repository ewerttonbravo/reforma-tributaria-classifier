package com.ebravo.taxclassifier.adapter.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.ebravo.taxclassifier.application.PublishProducts;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/** {@code publish <file>}: exits with 0 when every line is published and 1 when some line was rejected. */
@Component
public class PublishCommand implements ApplicationRunner, ExitCodeGenerator {

    public static final String NAME = "publish";

    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private final PublishProducts publishProducts;
    private int exitCode;

    PublishCommand(PublishProducts publishProducts) {
        this.publishProducts = publishProducts;
    }

    @Override
    public void run(ApplicationArguments args) {
        var command = args.getNonOptionArgs();
        if (!command.isEmpty() && command.getFirst().equals(NAME)) {
            exitCode = publish(command, System.out, System.err);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    int publish(List<String> args, PrintStream out, PrintStream err) {
        var lines = read(Path.of(args.get(1)));
        var result = publishProducts.publish(lines);
        result.rejected().forEach(line -> err.println("line " + line.lineNumber() + ": " + line.reason()));
        out.println("products published: " + result.products().size() + ", lines rejected: " + result.rejected().size());
        return result.rejected().isEmpty() ? 0 : 1;
    }

    private static List<String> read(Path file) {
        try {
            var text = Files.readString(file, UTF_8);
            return text.replaceFirst("^" + BYTE_ORDER_MARK, "").lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
