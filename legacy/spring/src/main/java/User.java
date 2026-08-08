package jatrailmap;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.bson.types.ObjectId;

@Document(collection="users")
public class User {
    @Id private String id;
    private String username;
    private String password;
    private String fullname;
    private String country;
    private String state;
    private String city;
    private String email;

    public User() { }
    public User(String i, String u, String p, String f, String c, String s, String ci, String e) {
	id = i;
	username = u;
	password = p;
	fullname = f;
	country = c;
	state = s;
	city = ci;
	email = e;
    }
    public void setUsername(String u) {
	username = u;
    }
    public void setPassword(String p) {
	password = p;
    }
    public void setFullname(String n) {
	fullname = n;
    }
    public void setCountry(String c) {
	country = c;
    }
    public void setState(String s) {
	state = s;
    }
    public void setCity(String c) {
	city = c;
    }
    public void setEmail(String e) {
	email = e;
    }

    public String getId() {
	return id;
    }
    public String get_id() {
	return id;
    }
    public String getUsername() {
	return username;
    }
    public String getPassword() {
	return password;
    }
    public String getFullname() {
	return fullname;
    }
    public String getCountry() {
	return country;
    }
    public String getState() {
	return state;
    }
    public String getCity() {
	return city;
    }
    public String getEmail() {
	return email;
    }
}
