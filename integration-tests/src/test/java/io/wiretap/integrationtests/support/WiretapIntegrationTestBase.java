package io.wiretap.integrationtests.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Shared shell for wiretap integration tests.
 *
 * <ul>
 *   <li>{@code RANDOM_PORT} boots a real servlet container — needed because
 *       logback-access hooks into the servlet filter chain.</li>
 *   <li>{@link EmbeddedKafka} provides an in-JVM broker; tests resolve it via
 *       {@code spring.embedded.kafka.brokers}.</li>
 *   <li>{@link OutputCaptureExtension} wraps {@code System.out} so we can read
 *       both logback-classic and logback-access output uniformly.</li>
 *   <li>{@code @DirtiesContext(BEFORE_CLASS)} forces a fresh broker per test
 *       class to avoid Zookeeper thread leaks.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@EmbeddedKafka(
    partitions = 1,
    topics = {"demo.events", "secrets.test", "secrets.events"},
    controlledShutdown = true
)
@ExtendWith(OutputCaptureExtension.class)
public abstract class WiretapIntegrationTestBase {
}
