package jatrailmap;

import java.util.List;
import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserRepository extends MongoRepository<User, String> {
    public List<User> findByUsername(String user);
    public List<User> findUserById(ObjectId id);
    //public User insert(User user);
    //public User save(User user); // this will update user data

    @Query("{ id: {'$in': ?0} }, { fields: {fullname: 1 } }")
    public List<User> findByIds(List<ObjectId> ids);
    /*
    @Query("{ $nor: [ {id: { $in: ?0}}, {id: ?1} ] }, {fields: {'fullname':1} }")
    public List<User> findByNotIds(List<ObjectId> ids, String id);
    */
    @Query("{ $nor: [ {id: { $in: ?0 } } ] }, {fields: {'fullname':1} }")
    public List<User> findByNotIds(List<ObjectId> ids);

    @Query("{id: {$ne : ?0}}, {fields: { 'fullname':1}}")
    public List<User> findAllNotCurrent(String id);
}
