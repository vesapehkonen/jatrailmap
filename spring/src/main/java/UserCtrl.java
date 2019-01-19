package jatrailmap;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CookieValue;

import de.neuland.jade4j.Jade4J;
import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.JadeTemplate;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.servlet.http.HttpServletRequest;
import org.bson.types.ObjectId;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import java.util.Base64;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.MediaType;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;

@RestController
public class UserCtrl {

    private static final Logger log = LoggerFactory.getLogger(UserCtrl.class);
    @Autowired
    private UserRepository userRep;
    @Autowired
    private TrailRepository trailRep;
    @Autowired
    private GroupRepository groupRep;
    @Autowired
    private SessionRepository sessionRep;
    @Autowired
    private Map<String, Object> model;
    @Autowired
    private JadeConfiguration jadeConfig;
    @Autowired
    private BCryptPasswordEncoder encoder;

    @Value("${sessionMaxAge}")
    private int sessionMaxAge;
    
    private String randomString() {
	final int count = 64;
	final String chars = "abcdefghijklmopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	final int len = chars.length();
	StringBuilder builder = new StringBuilder();
	Random rand = new Random();
	for (int i=0; i<count; i++) {
	    builder.append(chars.charAt(rand.nextInt(len)));
	}
	return builder.toString();
    }
    
    @RequestMapping(value = "/login", method = RequestMethod.GET)

    public Response login(@RequestParam(name="username", required=true, defaultValue="") String username,
			  @RequestParam(name="password", required=true, defaultValue="") String passwd,
			  HttpServletResponse response) throws IOException {

	log.info("/login: user=" + username + " passwd=****");
	List<User> users = userRep.findByUsername(username);
	if (users.size() != 1) {
	    log.error("User '" + username + "' not found from database");
	    return new Response("notok", "Wrong username or password");
	}
	User user = users.get(0);
	String hash = user.getPassword();
	hash = hash.replaceAll("^\\$2b", "\\$2a"); // because spring-boot-starter-security doesn't support bcrypt 2b version 
	if (!encoder.matches(passwd, hash)) {
	    log.error("Wrong password");
	    return new Response("notok", "Wrong username or password");
	}
	String token = randomString();
	Session session = new Session(token, new ObjectId(user.getId()), new Date());
	if (sessionRep.insert(session) == null) {
	    log.error("Database error, the new session wasn't inserted");
	}
	Cookie cookie = new Cookie("token", token);
	cookie.setMaxAge(sessionMaxAge);
	response.addCookie(cookie);
	return new Response("ok", "");
    }

    @RequestMapping(value = "/logout", method = RequestMethod.GET)

    public String logout(@CookieValue(value = "token", defaultValue = "") String token,
			 HttpServletResponse response) throws IOException {
	log.info("/logout");
	sessionRep.deleteByToken(token);
	Cookie cookie = new Cookie("token", "");
	cookie.setMaxAge(0);
	response.addCookie(cookie);
	
	// render main page
	List<Trail> trails = trailRep.findByPublic();
	model.put("authenticated", false);
	model.put("trails", trails);
	model.put("title", "Just Another Trail Map");
	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/main.jade");
	return jadeConfig.renderTemplate(temp, model);
    }

    @RequestMapping(value = "/userinfo", method = RequestMethod.GET)

    public String userinfo(@CookieValue(value = "token", defaultValue = "") String token) throws IOException {

	log.info("/userinfo: token=" + token);
	List<Session> sessions = sessionRep.findByToken(token);
	if (sessions.size() != 1) {
	    log.error("token '" + token + "' not found from the database");
	    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The session is expired.");
	}
	ObjectId userid = sessions.get(0).getUserid();
	List<User> users = userRep.findUserById(userid);
	if (users.size() != 1) {
	    log.error("userid '" + userid.toHexString() + "' not found from the database");
	    sessionRep.deleteByToken(token);
	    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The userid not found.");
	}
	model.put("title", "User information");
	model.put("info", users.get(0));

	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/userinfo.jade");
	return jadeConfig.renderTemplate(temp, model);
    }

    @RequestMapping(value = "/newuser", method = RequestMethod.GET)

    public String newuser() throws IOException {
	log.info("/newuser");
	model.put("title", "New user");
	model.put("authenticated", false);

	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/newuser.jade");
	return jadeConfig.renderTemplate(temp, model);
    }

    @RequestMapping(value = "/adduser", method = RequestMethod.POST)

    public Response adduser(@RequestParam(name="username", required=true, defaultValue="") String username,
			    @RequestParam(name="password", required=true, defaultValue="") String passwd,
			    @RequestParam(name="fullname", required=false, defaultValue="") String fullname,
			    @RequestParam(name="country", required=false, defaultValue="") String country,
			    @RequestParam(name="state", required=false, defaultValue="") String state,
			    @RequestParam(name="city", required=false, defaultValue="") String city,
			    @RequestParam(name="email", required=false, defaultValue="") String email,
			    HttpServletResponse response) throws IOException {

	log.info("/adduser: username=" + username + " password=****");
	List<User> users = userRep.findByUsername(username);

	if (users != null && users.size() >= 1) {
	    log.error("username '" + username + "' is alreaydy exist");
	    return new Response("notok", "Username is already exist, try other one");
	}
	String hash = encoder.encode(passwd);
	User user = new User(null, username, hash, fullname, country, state, city, email);

	user = userRep.insert(user);
	if (user == null) {
	    log.error("Database error, the new user wasn't inserted");
	    return new Response("notok", "Database error, the new user wasn't inserted");
	}
	String token = randomString();
	Session session = new Session(token, new ObjectId(user.getId()), new Date());
	if (sessionRep.insert(session) == null) {
	    log.error("Database error, the new session wasn't inserted");
	}
	Cookie cookie = new Cookie("token", token);
	cookie.setMaxAge(sessionMaxAge);
	response.addCookie(cookie);
	return new Response("ok", "");
    }

    @RequestMapping(value = "/updateuser", method = RequestMethod.POST)

    public Response updateuser(@RequestParam(name="username", required=true, defaultValue="") String username,
			       @RequestParam(name="password", required=true, defaultValue="") String passwd,
			       @RequestParam(name="fullname", required=false, defaultValue="") String fullname,
			       @RequestParam(name="country", required=false, defaultValue="") String country,
			       @RequestParam(name="state", required=false, defaultValue="") String state,
			       @RequestParam(name="city", required=false, defaultValue="") String city,
			       @RequestParam(name="email", required=false, defaultValue="") String email) throws IOException {

	log.info("/updateuser: username=" + username + " password=****");
	List<User> users = userRep.findByUsername(username);
	if (users.size() != 1) {
	    log.error("username '" + username + "' not found from the database");
	    return new Response("notok", "username or password not found from the database");
	}
	User user = users.get(0);
	String hash = user.getPassword();
	hash = hash.replaceAll("^\\$2b", "\\$2a"); // because spring-boot-starter-security doesn't support bcrypt 2b version 
	if (!encoder.matches(passwd, hash)) {
	    log.error("Wrong password");
	    return new Response("notok", "Wrong username or password");
	}
	user.setFullname(fullname);
	user.setCountry(country);
	user.setState(state);
	user.setCity(city);
	user.setEmail(email);

	if (userRep.save(user) == null) {
	    log.error("Database error, the user data wasn't updated");
	    return new Response("notok", "Database error, the user data wasn't updated");
	}
	return new Response("ok", "");
    }

    @RequestMapping(value = "/updatepassword", method = RequestMethod.POST)

    public Response updatepassword(@RequestParam(name="username", required=true, defaultValue="") String username,
				   @RequestParam(name="oldpassword", required=true, defaultValue="") String oldpasswd,
				   @RequestParam(name="newpassword", required=true, defaultValue="") String newpasswd,
				   HttpServletResponse response) throws IOException {

	log.info("/updatepassword: username=" + username + " oldpassword=**** newpassword=****");
	List<User> users = userRep.findByUsername(username);

	if (users.size() != 1) {
	    log.error("username '" + username + "' not found from the database");
	    return new Response("notok", "Wrong username or password");
	}
	User user = users.get(0);
	String hash = user.getPassword();
	hash = hash.replaceAll("^\\$2b", "\\$2a"); // because spring-boot-starter-security doesn't support bcrypt 2b version 
	if (!encoder.matches(oldpasswd, hash)) {
	    log.error("Wrong password");
	    return new Response("notok", "Wrong username or password");
	}
	user.setPassword(newpasswd);
	if (userRep.save(user) == null) {
	    log.error("Database error, the password wasn't updated");
	    return new Response("notok", "Database error, the password wasn't updated");
	}
	response.addCookie(new Cookie("password", newpasswd));
	return new Response("ok", "");
    }
}
