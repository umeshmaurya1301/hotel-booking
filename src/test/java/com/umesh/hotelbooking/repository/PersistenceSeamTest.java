package com.umesh.hotelbooking.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the property the port/adapter split exists to create: <b>the choice of persistence
 * technology is confined to one package</b>.
 *
 * <p>Without this test the seam is a convention, and a convention is one careless
 * {@code @Autowired JpaBookingRepository} away from being gone — the leak would compile, pass
 * every other test, and quietly restore exactly the coupling the split removed. Reviewing for it
 * by eye works until the day someone is in a hurry.
 *
 * <p>Reading source text rather than using a bytecode-level architecture library (ArchUnit) is a
 * deliberate trade: the project has no such dependency, three checks do not justify adding one,
 * and an import scan is legible to anyone who opens this file. The cost is honest — this sees
 * {@code import} statements, so it would miss a fully-qualified {@code
 * org.springframework.data.Foo} written inline. That is a real gap, and a small one next to the
 * accident this actually catches.
 */
class PersistenceSeamTest {

    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path ADAPTERS = MAIN.resolve(Path.of("com", "umesh", "hotelbooking", "repository", "jpa"));
    private static final Path PORTS = MAIN.resolve(Path.of("com", "umesh", "hotelbooking", "repository"));

    @Test
    void onlyTheJpaAdapterPackageKnowsAboutSpringData() throws IOException {
        List<String> offenders = sourceFilesOutside(ADAPTERS)
                .filter(file -> importsAnyOf(file, "org.springframework.data", "jakarta.persistence.EntityManager"))
                .map(Path::toString)
                .toList();

        assertThat(offenders)
                .as("Only repository.jpa may name a Spring Data type; everything else goes through a *Store port")
                .isEmpty();
    }

    @Test
    void portsAreFreeOfPersistenceFrameworkTypes() throws IOException {
        List<String> offenders = javaFilesDirectlyIn(PORTS)
                .filter(file -> importsAnyOf(file, "org.springframework", "jakarta.persistence"))
                .map(Path::toString)
                .toList();

        assertThat(offenders)
                .as("A port that imports a framework type is not a port")
                .isEmpty();
    }

    /**
     * The ports are only a seam if the domain actually goes through them. A service holding a
     * {@code Jpa*Repository} would satisfy both tests above and still have bypassed the layer, so
     * the adapter types are checked for containment separately: they are package-private, and
     * this asserts that the compiler's guarantee has not been given away by someone widening one
     * to {@code public}.
     */
    @Test
    void adapterTypesStayPackagePrivate() throws IOException {
        List<String> exported = javaFilesDirectlyIn(ADAPTERS)
                .filter(file -> !file.getFileName().toString().equals("JpaStores.java"))
                .filter(file -> !file.getFileName().toString().equals("package-info.java"))
                .filter(PersistenceSeamTest::declaresPublicType)
                .map(Path::toString)
                .toList();

        assertThat(exported)
                .as("Only JpaStores is public here — the adapters and their Spring Data interfaces "
                        + "are package-private so nothing outside can inject one")
                .isEmpty();
    }

    private static Stream<Path> sourceFilesOutside(Path excluded) throws IOException {
        return Files.walk(MAIN)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.startsWith(excluded));
    }

    private static Stream<Path> javaFilesDirectlyIn(Path directory) throws IOException {
        return Files.list(directory).filter(path -> path.toString().endsWith(".java"));
    }

    private static boolean importsAnyOf(Path file, String... packagePrefixes) {
        return lines(file).stream()
                .filter(line -> line.startsWith("import "))
                .anyMatch(line -> Stream.of(packagePrefixes)
                        .anyMatch(prefix -> line.startsWith("import " + prefix)
                                || line.startsWith("import static " + prefix)));
    }

    private static boolean declaresPublicType(Path file) {
        return lines(file).stream()
                .anyMatch(line -> line.startsWith("public class ") || line.startsWith("public interface "));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }
}
