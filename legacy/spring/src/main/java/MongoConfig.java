package jatrailmap;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import com.mongodb.MongoClient;

/* these are for "do not add the field _class to db" */
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
/********/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableMongoRepositories(basePackages = "jatrailmap")
public class MongoConfig extends AbstractMongoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TrailComparator.class);
    
    @Override
    protected String getDatabaseName() {
        return "jatrailmap";
    }
    @Override
    public MongoClient mongoClient() {
	MongoClient mc = null; 
	try{
	    mc = new MongoClient("127.0.0.1", 27017);
	} catch(Exception e) {
	    log.error(e.toString());
	}
	return mc;
    }

    /* Don’t want that extra _class key on all your records, read on!… */
    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDbFactory factory, MongoMappingContext context, BeanFactory beanFactory) {
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter mappingConverter = new MappingMongoConverter(dbRefResolver, context);
        try {
            //mappingConverter.setCustomConversions(beanFactory.getBean(CustomConversions.class));
            mappingConverter.setCustomConversions(beanFactory.getBean(MongoCustomConversions.class));
        }
        catch (NoSuchBeanDefinitionException ignore) {}

        // Don't save _class to mongo
        mappingConverter.setTypeMapper(new DefaultMongoTypeMapper(null));

        return mappingConverter;
    }
    /*****************************/
}
