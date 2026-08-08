package jatrailmap;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CookieValue;

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

import java.util.Base64;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

//import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RestController
public class GroupCtrl {

    private static final Logger log = LoggerFactory.getLogger(GroupCtrl.class);
    @Autowired
    private GroupRepository groupRep;
    @Autowired
    private UserRepository userRep;
    @Autowired
    private SessionRepository sessionRep;
    @Autowired
    private Map<String, Object> model;
    @Autowired
    private JadeConfiguration jadeConfig;

    private String getUserid(String token) {
	List<Session> sessions = sessionRep.findByToken(token);
	if (sessions.size() != 1) {
	    log.error("Expired or invalid session token '" + token + "'");
	    return null;
	}
	return sessions.get(0).getUserid().toHexString();
    }
    
    @RequestMapping(value = "/groups", method = RequestMethod.GET)

    public String groups(@CookieValue(value = "token", defaultValue = "") String token) throws IOException {

	log.info("/groups");
	String userid = getUserid(token);
	if (userid == null) {
	    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The username or password not found or they are wrong.");
	}
	List<Group> groups = groupRep.findByOwnerid(new ObjectId(userid));
	model.put("title", "Manage Groups");
	model.put("groups", groups);
	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/groups.jade");
	return jadeConfig.renderTemplate(temp, model);
    }

    @RequestMapping(value = "/newgroup", method = RequestMethod.GET)

    public String newgroup(@CookieValue(value = "token", defaultValue = "") String token) throws IOException {

	log.info("/newgroup");
	String userid = getUserid(token);
	if (userid == null) {
	    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The username or password not found or they are wrong.");
	}
	List<User> users = userRep.findAllNotCurrent(userid);
	//Map<String, Object> model = new HashMap<String, Object>();
	model.put("title", "Add New Group");
	model.put("users", users);
	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/newgroup.jade");
	return jadeConfig.renderTemplate(temp, model);
    }

    @RequestMapping(value = "/addgroup", method = RequestMethod.POST)

    public Response addgroup(@CookieValue(value = "token", defaultValue = "") String token,
			     @RequestParam(name="groupname", required=true, defaultValue="") String groupname,
			     @RequestParam(name="users[]", required=true) String[] users) throws IOException {

	log.info("/addgroup");
	String userid = getUserid(token);
	if (userid == null) {
	    return new Response("notok", "The username or password not found or they are wrong.");
	}
	Group group = new Group(null, new ObjectId(userid), groupname, Utils.stringArrayToObjectIdList(users));
	
	if (groupRep.insert(group) == null) {
	    log.error("Database error, the new group wasn't inserted");
	    return new Response("notok", "Database error, the new group wasn't inserted");
	}
	return new Response("ok", "");
    }

    @RequestMapping(value = "/editgroup/*", method = RequestMethod.GET)

    public String editgroup(@CookieValue(value = "token", defaultValue = "") String token,
			    HttpServletRequest request) throws IOException {

	log.info("/editgroup/*");
	String userid = getUserid(token);
	if (userid == null) {
	    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The username or password not found or they are wrong.");
	}
	String[] parts =  request.getRequestURI().split("/", 0);
	if (parts.length < 3) {
	    log.error("Invalid query string '" + request.getRequestURI() + "'");
	    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid query string.");
	}
	String groupid = parts[2];

	List<Group> groups = groupRep.findByIdAndOwnerid(new ObjectId(groupid), new ObjectId(userid));

	if (groups.size() != 1) {
	    log.error("Group not found from the database or user is not owner of the group");
	    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found from the database.");
	}
	Group group = groups.get(0);
	List<ObjectId> memberids = group.getMembers();
	
	List<User> members = userRep.findByIds(memberids);

	memberids.add(new ObjectId(userid));
	List<User> nonmembers = userRep.findByNotIds(memberids);

	for (User u : members) {
	    if ("".equals(u.getFullname())) {
		u.setFullname(u.getUsername());
	    }
	}
	for (User u : nonmembers) {
	    if ("".equals(u.getFullname())) {
		u.setFullname(u.getUsername());
	    }
	}
	model.put("title", "Edit Group");
	model.put("groupid", groupid);
	model.put("groupname", group.getName());
	model.put("members", members);
	model.put("nonmembers", nonmembers);
	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/editgroup.jade");
	return jadeConfig.renderTemplate(temp, model);
    }

    
    @RequestMapping(value = "/updategroup", method = RequestMethod.POST)

    public Response updategroup(@CookieValue(value = "token", defaultValue = "") String token,
				@RequestParam(name="groupid", required=true, defaultValue="") String groupid,
				@RequestParam(name="groupname", required=true, defaultValue="") String groupname,
				@RequestParam(name="members[]", required=true) String[] members) throws IOException {
	

	log.info("/updategroup");
	String userid = getUserid(token);
	if (userid == null) {
	    return new Response("notok", "The username or password not found or they are wrong.");
	}

	//List<Group> groups = groupRep.findGroupById(groupid);
	List<Group> groups = groupRep.findByIdAndOwnerid(new ObjectId(groupid), new ObjectId(userid));
	if (groups.size() != 1) {
	    log.error("Group not found from the database or user is not owner of the group");
	    return new Response("notok", "Group not found from the database");
	}
	Group group = groups.get(0);
	group.setName(groupname);
	group.setMembers(Utils.stringArrayToObjectIdList(members));
	
	if (groupRep.save(group) == null) {
	    log.error("Database error, the group wasn't updated");
	    return new Response("notok", "Database error, the group wasn't updated");
	}
	return new Response("ok", "");
    }

    @RequestMapping(value = "/group/*", method = RequestMethod.DELETE)

    public Response delete(@CookieValue(value = "token", defaultValue = "") String token,
			   HttpServletRequest request) throws IOException {

	log.info("/group/* method:delete");
	String userid = getUserid(token);
	if (userid == null) {
	    return new Response("notok", "The username or password not found or they are wrong.");
	}
	String[] parts =  request.getRequestURI().split("/", 0);
	if (parts.length < 3) {
	    log.error("Invalid query string '" + request.getRequestURI() + "'");
	    return new Response("notok", "Invalid query string.");
	}
	String groupid = parts[2];

	List<Group> groups = groupRep.findByIdAndOwnerid(new ObjectId(groupid), new ObjectId(userid));
	if (groups.size() != 1) {
	    log.error("Group not found from the database or user is not owner of the group");
	    return new Response("notok", "Group not found from the database");
	}
	groupRep.deleteById(groupid);
	return new Response("ok", "");
    }

}
