package jatrailmap;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

public class Utils {

    public static List<ObjectId> stringArrayToObjectIdList(String[] arr) {
	List<ObjectId> ids = new ArrayList<ObjectId>();
	for (String item : arr) {
	    ids.add(new ObjectId(item));
	}
	return ids;
    }

    public static List<ObjectId> stringListToObjectIdList(List<String> list) {
	List<ObjectId> ids = new ArrayList<ObjectId>();
	for (String item : list) {
	    ids.add(new ObjectId(item));
	}
	return ids;
    }
}

