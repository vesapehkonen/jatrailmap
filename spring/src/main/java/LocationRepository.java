package jatrailmap;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.bson.types.ObjectId;

public interface LocationRepository extends CrudRepository<Location, String>, CustomLocationRepository {
//public interface LocationRepository extends MongoRepository<Location, String> {
    @Query("{ 'trailid': ?0 }, { fields: {'loc.coordinates': 1, 'timestamp': 1, _id: 0 } }")
    List<Location> findByTrailid(ObjectId trailid);
    //public Location insert(Location locs);
    public void insert(List<Location> locs);
    public void deleteById(String id);
    public void deleteByTrailid(ObjectId id);
}
