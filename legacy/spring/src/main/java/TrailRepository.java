package jatrailmap;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.bson.types.ObjectId;

public interface TrailRepository extends CrudRepository<Trail, String>, CustomTrailRepository {

    @Query("{ $or: [ {groups: {$in: ?0}, access: 'group'}, {access: 'public'}, {userid: ?1} ] }, {fields: { 'trailname':1, 'location':1 }}")
    List<Trail> findByGroupsOrOwnerOrPublic(List<ObjectId> groups, ObjectId ownerid);

    @Query("{access: 'public'}, {fields: { 'trailname':1, 'location':1 }}")
    List<Trail> findByPublic();
    
    List<Trail> findTrailById(String id);

    Trail insert(Trail trail);
    //Trail save(Trail trail); // this will update the trail
    List<Trail> removeById(String id);
    void deleteById(String id);
}
