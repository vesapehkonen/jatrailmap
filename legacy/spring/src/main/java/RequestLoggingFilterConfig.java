package jatrailmap;

import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RequestLoggingFilterConfig {
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
	CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
	loggingFilter.setIncludeClientInfo(false);
	loggingFilter.setIncludeQueryString(false);
	loggingFilter.setIncludePayload(true);
	loggingFilter.setIncludeHeaders(false);
        loggingFilter.setMaxPayloadLength(10000);
        loggingFilter.setAfterMessagePrefix("Request data : ");
	return loggingFilter;
    }
    
}
