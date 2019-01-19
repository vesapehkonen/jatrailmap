package jatrailmap;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.bson.types.ObjectId;

@Document(collection="locations")
public class Location {
    @Id private String id;
    private ObjectId trailid;
    private String timestamp;
    private Loc loc;

    Location() { }
    Location(String i, ObjectId tid, String ts, Loc l) {
	id = i;
	trailid = tid;
	timestamp = ts;
	loc = l;
    }
    public void setTrailid(ObjectId tid) {
	trailid = tid;
    }
    public void setTimestamp(String t) {
	timestamp = t;
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
    public String getTimestamp() {
	return timestamp;
    }
    public Loc getLoc() {
	return loc;
    }
}
