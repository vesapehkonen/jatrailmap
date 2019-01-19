package jatrailmap;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import org.bson.types.ObjectId;


public interface ImageRepository extends MongoRepository<Image, String> {
    @Query("{ 'id': ?0 }, { fields: {'img': 1, '_id': 0 } }")
    List<Image> findImageById(ObjectId imageid);
    void deleteById(String id);
    Image insert(Image image);
}
