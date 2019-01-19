package jatrailmap;

import java.util.List;
import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.Query;

public interface GroupRepository extends MongoRepository<Group, String> {
    public List<Group> findByMembers(ObjectId id);

    public List<Group> findGroupById(String id);

    @Query("{ id: {'$in': ?0} }, { fields: {name: 1 } }")
    public List<Group> findByIds(List<ObjectId> ids);
    
    public List<Group> findByOwnerid(ObjectId id);

    @Query("{ $and: [ {id: ?0}, {ownerid: ?1} ] }, {fields: { members:1, name:1 } }")
    public List<Group> findByIdAndOwnerid(ObjectId id, ObjectId ownerid);
    
    //public Group insert(Group group);
    //public Group save(Group group); // this will update user data
    public List<Group> removeById(String id);
    public void deleteById(String id);
}
