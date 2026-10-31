import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Turns the UI test results (JUnit XML) into a readable summary on the GitHub job page: a table in the job summary
 * ($GITHUB_STEP_SUMMARY) and an error annotation for each failed test. Without $GITHUB_STEP_SUMMARY it prints the
 * table instead.
 *
 * <p>Usage: {@code java .github/scripts/UiTestSummary.java <title> <results dir>}
 */
public class UiTestSummary {
    private static final int MAX_MESSAGE = 300;

    private record Failure(String test, String message) {}

    public static void main(String[] args) throws Exception {
        String title = args[0];
        Path resultsDir = Path.of(args[1]);
        List<String> passed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<Failure> failed = new ArrayList<>();
        Set<String> reported = new HashSet<>();

        for (Path report : reports(resultsDir)) {
            NodeList cases = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(report.toFile())
                    .getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element testCase = (Element) cases.item(i);
                String className = testCase.getAttribute("classname");
                String test = className.substring(className.lastIndexOf('.') + 1) + "." + testCase.getAttribute("name");
                reported.add(className + "-" + testCase.getAttribute("name"));
                Element problem = child(testCase, "failure");
                if (problem == null) problem = child(testCase, "error");
                // The managed-device report writes an @Ignore'd test as an empty <failure/>; Gradle counts it as skipped.
                boolean ignored = problem != null && problem.getAttribute("message").isBlank()
                        && problem.getTextContent().isBlank();
                if (ignored || child(testCase, "skipped") != null) {
                    skipped.add(test);
                } else if (problem != null) {
                    String message = problem.getAttribute("message");
                    failed.add(new Failure(test, firstLine(message.isBlank() ? problem.getTextContent() : message)));
                } else {
                    passed.add(test);
                }
            }
        }

        // Every test that starts leaves its logcat. One without a result was lost: its run never reported back,
        // which the emulator sometimes does, and Gradle doesn't count it as a failure.
        List<String> lost = lost(resultsDir, reported);

        List<String> lines = new ArrayList<>(List.of("### UI tests on " + title, ""));
        if (passed.isEmpty() && failed.isEmpty() && skipped.isEmpty()) {
            lines.add("No test results: the build or the emulator failed before the tests ran. See the job log.");
        } else {
            lines.add("✅ " + passed.size() + " passed · ❌ " + failed.size() + " failed · ⏭️ " + skipped.size() + " skipped"
                    + (lost.isEmpty() ? "" : " · ⚠️ " + lost.size() + " without a result"));
            if (!lost.isEmpty()) {
                lines.addAll(List.of("", "Started but without a result (not checked, rerun them): `"
                        + String.join("`, `", lost) + "`"));
            }
            if (!failed.isEmpty()) {
                lines.addAll(List.of("", "| Failed test | Message |", "|---|---|"));
                for (Failure failure : failed) {
                    lines.add("| `" + failure.test() + "` | " + failure.message().replace("|", "\\|") + " |");
                }
                lines.addAll(List.of("", "Screenshots and element trees of the failed tests, the HTML report and"
                        + " each test's logcat are in this run's `ui-test-reports-*` artifact."));
            }
            if (!skipped.isEmpty()) {
                lines.addAll(List.of("", "Skipped: `" + String.join("`, `", skipped) + "`"));
            }
        }

        String text = String.join("\n", lines) + "\n";
        String summary = System.getenv("GITHUB_STEP_SUMMARY");
        if (summary != null && !summary.isBlank()) {
            Files.writeString(Path.of(summary), text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            System.out.print(text);
        }
        for (Failure failure : failed) {
            System.out.println("::error title=" + escape(title + ": " + failure.test()) + "::" + escape(failure.message()));
        }
        for (String test : lost) {
            System.out.println("::warning title=" + escape(title + ": " + test) + "::Started but without a result; rerun it.");
        }
    }

    /** Tests that left a logcat (`logcat-<class>-<method>.txt`) but no result in the JUnit XML. */
    private static List<String> lost(Path dir, Set<String> reported) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("logcat-") && name.endsWith(".txt"))
                    .map(name -> name.substring("logcat-".length(), name.length() - ".txt".length()))
                    .filter(test -> !reported.contains(test))
                    .map(test -> test.substring(test.lastIndexOf('.') + 1).replace('-', '.'))
                    .sorted()
                    .toList();
        }
    }

    private static List<Path> reports(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList();
        }
    }

    private static Element child(Element parent, String tag) {
        NodeList children = parent.getElementsByTagName(tag);
        return children.getLength() > 0 ? (Element) children.item(0) : null;
    }

    private static String firstLine(String text) {
        String line = text == null ? "" : text.strip().lines().findFirst().orElse("");
        return line.length() <= MAX_MESSAGE ? line : line.substring(0, MAX_MESSAGE) + "…";
    }

    private static String escape(String text) {
        return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A");
    }
}
