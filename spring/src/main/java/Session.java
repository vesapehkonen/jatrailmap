package jatrailmap;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.bson.types.ObjectId;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;

@Document(collection="sessions")
public class Session {
    @Id private String id;
    private String token;
    private ObjectId userid;

    @Indexed(name = "created", expireAfterSeconds = 86400)
    private Date created;

    public Session() { }
    public Session(String t, ObjectId u, Date d) {
	token = t;
	userid = u;
	created = d;
    }
    public void setToken(String t) {
	token = t;
    }
    public void setUserid(ObjectId u) {
	userid = u;
    }
    public void setCreated(Date d) {
	created = d;
    }
    public String getId() {
	return id;
    }
    public String get_id() {
	return id;
    }
    public String getToken() {
	return token;
    }
    public ObjectId getUserid() {
	return userid;
    }
    public Date getCreated() {
	return created;
    }
}
