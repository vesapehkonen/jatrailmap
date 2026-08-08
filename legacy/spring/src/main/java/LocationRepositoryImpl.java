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

public class LocationRepositoryImpl implements CustomLocationRepository {

    private final MongoOperations operations;

    @Autowired
    public LocationRepositoryImpl(MongoOperations operations) {
	//Assert.notNull(operations, "MongoOperations must not be null!");
	this.operations = operations;
    }
    public void insert(List<Location> locs) {
	operations.insert(locs, "locations");
    }

    public int update(String id, double lat, double lng) {
	Query query = new Query(Criteria.where("id").is(id));
	Update update = new Update();
	update.set("loc.coordinates.1", lat);
	update.set("loc.coordinates.0", lng);
	UpdateResult result = operations.updateFirst(query, update, Location.class);
	return (int)result.getModifiedCount();
    }
}
