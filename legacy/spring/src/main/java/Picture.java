package jatrailmap;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.bson.types.ObjectId;

@Document(collection="pictures")
public class Picture {
    @Id private String id;
    private ObjectId trailid;
    private ObjectId imageid;
    private String timestamp;
    private String filename;
    private String picturename;
    private String description;
    private Loc loc;
    private String access;
    private List<ObjectId> groups;

    Picture() { }
    Picture(String _id, ObjectId tid, ObjectId imgid, String ts,
	    String fn, String pn, String desc, Loc l) {
	id = _id;
	trailid = tid;
	imageid = imgid;
	timestamp = ts;
	filename = fn;
	picturename = pn;
	description = desc;
	loc = l;
    }
    public void setTrailid(ObjectId tid) {
	trailid = tid;
    }
    public void setImageid(ObjectId iid) {
	imageid = iid;
    }
    public void setTimestamp(String t) {
	timestamp = t;
    }
    public void setFilename(String n) {
	filename = n;
    }
    public void setPicturename(String n) {
	picturename = n;
    }
    public void setDescription(String d) {
	description = d;
    }
    public void setLoc(Loc l) {
	loc = l;
    }

    public String getId() {
	return id;
    }
    public String get_id() {
	return id;
    }
    public ObjectId getTrailid() {
	return trailid;
    }
    public String getImageid() {
	return imageid.toHexString();
    }
    public String getTimestamp() {
	return timestamp;
    }
    public String getFilename() {
	return filename;
    }
    public String getPicturename() {
	return picturename;
    }
    public String getDescription() {
	return description;
    }
    public Loc getLoc() {
	return loc;
    }
    public String getAccess() {
	return access;
    }
    public List<ObjectId> getGroups() {
	return groups;
    }
}

