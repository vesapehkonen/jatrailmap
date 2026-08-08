/*
{ "_id" : ObjectId("59e43e2fe7004f1e3839d522"), "userid" : ObjectId("59e43b37e559eb1d51a3d29a"), "access" : "public", "date" : "2017-10-15T22:08:18-07:00", "trailname" : "Dog Mountain", "location" : "Columbia Rirver Gorge, WA", "description" : "Sunday picnic with kids.", "groups" : null }
*/
package jatrailmap;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Transient;

import java.util.List;

@Document(collection="trails")
public class Trail {
    // variables form database
    @Id private String id;
    private ObjectId userid;
    private String access;
    private String date;
    private String trailname;
    private String location;
    private String description;
    private List<ObjectId> groups;

    public Trail() { }
    public Trail(String id, ObjectId userid, String access, String date,
		 String trailname, String location, String description,
		 List<ObjectId> groups) {
	this.id = id;
	this.userid = userid;
	this.access = access;
	this.date = date;
	this.trailname = trailname;
	this.location = location;
	this.description = description;
	this.groups = groups;
    }
    public void setId(String id) {
	this.id = id;
    }
    public void set_id(String id) {
	this.id = id;
    }
    public void setUserid(ObjectId uid) {
	userid = uid;
    }
    public void setAccess(String a) {
	access = a;
    }
    public void setDate(String d) {
	date = d;
    }
    public void setTrailname(String n) {
	trailname = n;
    }
    public void setLocation(String l) {
	location = l;
    }
    public void setDescription(String d) {
	description = d;
    }
    public void setGroups(List<ObjectId> l) {
	groups = l;
    }
    
    public String getId() {
	return id;
    }
    public String get_id() {
	return id;
    }
    public ObjectId getUserid() {
	return userid;
    }
    public String getAccess() {
	return access;
    }
    public String getDate() {
	return date;
    }
    public String getTrailname() {
	return trailname;
    }
    public String getLocation() {
	return location;
    }
    public String getDescription() {
	return description;
    }
    public List<ObjectId> getGroups() {
	return groups;
    }

    // these are not stored to db, they are variables for setting jade doc
    @Transient private String user;
    @Transient private String distance;
    @Transient private boolean owner;
    @Transient private String time;
    
    public void setUser(String u) {
	user = u;
    }
    public void setDistance(String d) {
	distance = d;
    }
    public void setOwner(boolean b) {
	owner = b;
    }
    public void setTime(String t) {
	time = t;
    }

    public String getUser() {
	return user;
    }
    public String getDistance() {
	return distance;
    }
    public boolean getOwner() {
	return owner;
    }
    public String getTime() {
	return time;
    }
    
}
