package jatrailmap;

import de.neuland.jade4j.Jade4J;
import de.neuland.jade4j.JadeConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JadeConfig {
    @Bean
    public JadeConfiguration jadeConfiguration() {
	JadeConfiguration config = new JadeConfiguration();
	config.setPrettyPrint(true);
	return config;
    }
}

