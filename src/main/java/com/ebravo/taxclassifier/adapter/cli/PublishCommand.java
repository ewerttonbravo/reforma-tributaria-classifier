package com.ebravo.taxclassifier.adapter.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.ebravo.taxclassifier.application.PublishProducts;
import com.ebravo.taxclassifier.domain.ProductCsvResult;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;

/**
 * {@code publish <file>}: exits with 0 when every line is published, 1 when some line was rejected and
 * 2 when the file could not be read or the products could not be published.
 */
@Component
public class PublishCommand implements ApplicationRunner, ExitCodeGenerator {

    private static final String NAME = "publish";

    /** The profile the command runs in, named after it. */
    public static final String PROFILE = NAME;

    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private final PublishProducts publishProducts;
    private int exitCode;

    PublishCommand(PublishProducts publishProducts) {
        this.publishProducts = publishProducts;
    }

    /** Whether the arguments ask for this command, read the way Spring reads them: options are skipped. */
    public static boolean isRequested(String... args) {
        var command = new DefaultApplicationArguments(args).getNonOptionArgs();
        return !command.isEmpty() && command.getFirst().equals(NAME);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isRequested(args.getSourceArgs())) {
            exitCode = publish(args.getNonOptionArgs(), System.out, System.err);
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    int publish(List<String> args, PrintStream out, PrintStream err) {
        if (args.size() != 2) {
            return fail(err, "expected one CSV file, usage: publish <file>");
        }
        Path file;
        try {
            file = Path.of(args.get(1));
        } catch (InvalidPathException e) {
            return fail(err, "invalid path (" + e.getReason() + ")");
        }
        List<String> lines;
        try {
            lines = Files.readString(file, UTF_8).replaceFirst("^" + BYTE_ORDER_MARK, "").lines().toList();
        } catch (CharacterCodingException e) {
            return fail(err, file + " is not UTF-8");
        } catch (IOException e) {
            return fail(err, "cannot read " + file + " (" + e.getClass().getSimpleName() + ")");
        }
        ProductCsvResult result;
        try {
            result = publishProducts.publish(lines);
        } catch (RuntimeException e) {
            var cause = rootCause(e);
            return fail(err, Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getSimpleName()));
        }
        result.rejected().forEach(line -> err.println("line " + line.lineNumber() + ": " + line.reason()));
        out.println("products published: " + result.products().size() + ", lines rejected: " + result.rejected().size());
        return result.rejected().isEmpty() ? 0 : 1;
    }

    private static int fail(PrintStream err, String reason) {
        err.println("publish failed: " + reason);
        return 2;
    }

    private static Throwable rootCause(Throwable e) {
        return e.getCause() == null ? e : rootCause(e.getCause());
    }
}
