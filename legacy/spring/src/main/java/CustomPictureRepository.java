package jatrailmap;

import java.util.List;
import org.bson.types.ObjectId;

interface CustomPictureRepository {
    int updateLoc(String id, double lat, double lng);
    int updateName(String id, String name);
    int updateAccess(String id, String access, List<ObjectId> groups);
}
