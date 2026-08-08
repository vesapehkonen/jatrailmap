package jatrailmap;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Transient;
import org.bson.types.ObjectId;

import java.util.List;
@Document(collection="groups")
public class Group {
    @Id private String id;
    private ObjectId ownerid;
    private String name;
    private List<ObjectId> members;
    @Transient private boolean checked = false;

    public Group() { }
    public Group(String i, ObjectId o, String n, List<ObjectId> m) {
	id = i;
	ownerid = o;
	name = n;
	members = m;
    }
    public void setOwnerid(ObjectId o) {
	ownerid = o;
    }
    public void setName(String n) {
	name = n;
    }
    public void setMembers(List<ObjectId> m) {
	members = m;
    }
    public void setChecked(boolean b) {
	checked = b;
    }

    public String getId() {
	return id;
    }
    public String get_id() {
	return id;
    }
    public ObjectId getOwnerid() {
	return ownerid;
    }
    public String getName() {
	return name;
    }
    public List<ObjectId> getMembers() {
	return members;
    }
    public boolean getChecked() {
	return checked;
    }
}
