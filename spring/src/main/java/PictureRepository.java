package jatrailmap;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.bson.types.ObjectId;

//public interface PictureRepository extends MongoRepository<Picture, String> {
public interface PictureRepository extends CrudRepository<Picture, String>, CustomPictureRepository {

    @Query("{ 'id': ?0 }, { fields: {imageid: 0 } }")
    List<Picture> findByPictureid(String id);

    @Query("{ 'trailid': ?0 }, { fields: {'loc.coordinates': 1, 'timestamp': 1, 'imageid' : 1, _id: 0 } }")
    List<Picture> findByTrailid(ObjectId trailid);

    @Query("{ $and: [ {'trailid': ?0}, { $or: [ {'groups': {$in: ?1}, 'access': 'group'}, {'access': 'public'}, { 'access' : { $exists: false } } ] } ] }")
    List<Picture> findByTrailidAndGroupidsOrPublicAccess(ObjectId trailid, ObjectId[] ids);

    @Query("{ 'imageid': ?0 }, { fields: {'trailid': 1, 'access': 1, 'groups' : 1, _id: 0 } }")
    List<Picture> findByImageid(ObjectId imageid);

    List<Picture> insert(List<Picture> pics);
    Picture insert(Picture pic);
    void deleteById(String id);
}
