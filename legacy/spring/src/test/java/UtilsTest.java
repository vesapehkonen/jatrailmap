package jatrailmap;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class UtilsTest {
    @Test
    public void testStringArrayToObjectIdList() {
	String[] arr = {"5bec9ce0e62361752a8a05b6", "5bef416ee62361784d48a7c0"};
	List<ObjectId> objs = Utils.stringArrayToObjectIdList(arr);
	assertEquals(objs.size(), 2);
	assertEquals(objs.get(0), new ObjectId("5bec9ce0e62361752a8a05b6"));
	assertEquals(objs.get(1), new ObjectId("5bef416ee62361784d48a7c0"));
    }
    @Test
    public void testStringListToObjectIdList() {
	List<String> strs = new ArrayList<String>();
	strs.add("5bec9ce0e62361752a8a05b6");
	strs.add("5bef416ee62361784d48a7c0");
	List<ObjectId> objs = Utils.stringListToObjectIdList(strs);
	assertEquals(objs.size(), 2);
	assertEquals(objs.get(0), new ObjectId("5bec9ce0e62361752a8a05b6"));
	assertEquals(objs.get(1), new ObjectId("5bef416ee62361784d48a7c0"));
    }
}
