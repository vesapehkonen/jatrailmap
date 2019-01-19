package jatrailmap;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.Query;
//import static org.junit.Assert.*;
//import org.junit.Assert;
import com.mongodb.client.result.UpdateResult;
import java.util.List;

public class TrailRepositoryImpl implements CustomTrailRepository {

    private final MongoOperations operations;

    @Autowired
    public TrailRepositoryImpl(MongoOperations operations) {
	//Assert.notNull(operations, "MongoOperations must not be null!");
	this.operations = operations;
    }

    public int update(String trailid, String trailname, String location, String desc) {
	Query query = new Query(Criteria.where("id").is(trailid));
	Update update = new Update();
	update.set("trailname", trailname);
	update.set("location", location);
	update.set("description", desc);
	UpdateResult result = operations.updateFirst(query, update, Trail.class);
	return (int)result.getModifiedCount();
    }

    public int updateAccess(String trailid, String access, String[] groups) {
	Query query = new Query(Criteria.where("id").is(trailid));
	Update update = new Update();
	update.set("access", access);
	update.set("groups", groups);
	UpdateResult result = operations.updateFirst(query, update, Trail.class);
	return (int)result.getModifiedCount();
    }

}
