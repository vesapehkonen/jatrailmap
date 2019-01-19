package jatrailmap;

import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.Query;
import org.bson.types.ObjectId;
//import static org.junit.Assert.*;
//import org.junit.Assert;
import com.mongodb.client.result.UpdateResult;
import java.util.List;

public class PictureRepositoryImpl implements CustomPictureRepository {

    private final MongoOperations operations;

    @Autowired
    public PictureRepositoryImpl(MongoOperations operations) {
	//Assert.notNull(operations, "MongoOperations must not be null!");
	this.operations = operations;
    }
    public int updateLoc(String id, double lat, double lng) {
	Query query = new Query(Criteria.where("id").is(id));
	Update update = new Update();
	update.set("loc.coordinates.1", lat);
	update.set("loc.coordinates.0", lng);
	UpdateResult result = operations.updateFirst(query, update, Picture.class);
	return (int)result.getModifiedCount();
    }
    public int updateName(String id, String name) {
	Query query = new Query(Criteria.where("id").is(id));
	Update update = new Update();
	update.set("picturename", name);
	UpdateResult result = operations.updateFirst(query, update, Picture.class);
	return (int)result.getModifiedCount();
    }
    public int updateAccess(String picid, String access, List<ObjectId> groups) {
	Query query = new Query(Criteria.where("id").is(picid));
	Update update = new Update();
	update.set("access", access);
	update.set("groups", groups);
	UpdateResult result = operations.updateFirst(query, update, Picture.class);
	return (int)result.getModifiedCount();
    }
}
