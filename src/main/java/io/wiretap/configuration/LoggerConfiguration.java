package io.wiretap.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @deprecated Use {@link WiretapAutoConfiguration}. Kept for source-level compatibility only.
 */
@Deprecated
@Configuration
@Import(WiretapAutoConfiguration.class)
public class LoggerConfiguration {
}
