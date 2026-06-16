package io.wiretap.configuration;

import ch.qos.logback.access.servlet.TeeFilter;
import io.wiretap.http.incoming.filter.ContentTypeAwareTeeFilter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Replaces the logback-access tee filter registered by
 * {@code dev.akkinoc.spring.boot:logback-access-spring-boot-starter} with a
 * content-type-aware variant ({@link ContentTypeAwareTeeFilter}) so multipart and
 * binary request streams are not buffered — buffering drains them before the
 * controller reads {@code getParts()}.
 * <p>
 * akkinoc registers its tee filter from {@code LogbackAccessTeeServletFilterConfiguration},
 * which is {@code @Import}ed by the top-level {@code LogbackAccessAutoConfiguration};
 * ordering ahead of that auto-configuration means our {@code FilterRegistrationBean<TeeFilter>}
 * is defined first, so akkinoc's {@code @ConditionalOnMissingFilterBean} backs off and
 * only our filter is registered. The conditions mirror akkinoc's so behaviour is
 * unchanged when the tee filter is disabled or outside a servlet web application.
 */
@Configuration
@AutoConfigureBefore(name = "dev.akkinoc.spring.boot.logback.access.LogbackAccessAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "logback.access.tee-filter", name = "enabled", havingValue = "true", matchIfMissing = false)
public class WiretapTeeFilterConfiguration {

    @Bean
    public FilterRegistrationBean<TeeFilter> wiretapContentAwareTeeFilter() {
        return new FilterRegistrationBean<>(new ContentTypeAwareTeeFilter());
    }
}
