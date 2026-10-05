package io.wulfcodes.messaging.ui.config;

import com.samskivert.mustache.Mustache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JMustache is strict by default: a template variable missing from the model (e.g. {{error}}
 * on a fresh login page) throws instead of rendering empty. Optional values are normal in
 * our forms, so missing/null values render as "" and count as false in {{#section}} blocks.
 */
@Configuration
public class MustacheConfig {

    @Bean
    public Mustache.Compiler mustacheCompiler(Mustache.TemplateLoader templateLoader) {
        return Mustache.compiler()
                .withLoader(templateLoader)
                .defaultValue("")
                .emptyStringIsFalse(true);
    }
}
