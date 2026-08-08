package jatrailmap;

import org.springframework.stereotype.Controller;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LocationComparator implements Comparator<Location> {

    private static final Logger log = LoggerFactory.getLogger(LocationComparator.class);

    public int compare(Location a, Location b) {
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
	//2017-10-15T13:09:06-07:00
	try {
	    Date dateA = sdf.parse(a.getTimestamp());
	    Date dateB = sdf.parse(b.getTimestamp());
	    return dateA.compareTo(dateB);
	} catch (ParseException e) {
	    log.error("error when parsing timestamp: " + e.toString());
	} catch (NullPointerException e) {
	    log.error("error when parsing timestamp: " + e.toString());
	}
	return 0;
    }
}

@RestController
public class TrailCtrl {

    private static final Logger log = LoggerFactory.getLogger(TrailCtrl.class);

    @Autowired
    private TrailRepository trailRep;
    @Autowired
    private GroupRepository groupRep;
    @Autowired
    private UserRepository userRep;
    @Autowired
    private LocationRepository locRep;
    @Autowired
    private PictureRepository picRep;
    @Autowired
    private ImageRepository imgRep;
    @Autowired
    private SessionRepository sessionRep;
    @Autowired
    private Map<String, Object> model;
    @Autowired
    private JadeConfiguration jadeConfig;

    private String authenticateUser(String username, String passwd) {
	List<User> users = userRep.findByUsername(username);
	if (users.size() != 1) {
	    return null;
	}
	User user = users.get(0);
	if (!passwd.equals(user.getPassword())) {
	    return null;
	}
	return user.getId();
    }

    private String authenticateUser(String token) {
	List<Session> sessions = sessionRep.findByToken(token);
	if (sessions.size() != 1) {
	    return null;
	}
	return sessions.get(0).getUserid().toHexString();
    }

    private boolean verifyTrailOwner(String trailid, String userid) {
	List<Trail> trails = trailRep.findTrailById(trailid);
	if (trails.size() != 1) {
	    log.error("Trail not found" + trailid);
	    return false;
	}
	Trail trail = trails.get(0);
	
	if (!trail.getUserid().toHexString().equals(userid)) {
	    log.error("User is not owner of this trail, trailid=" + trailid +
		      " userid=" + userid + " ownerid=" + trail.getUserid());
	    return false;
	}
	return true;
    }

    private boolean verifyTrailAccess(Trail trail, String userid) {
	// if the user is owner of the trail
	if (userid != null && userid.equals(trail.getUserid().toHexString())) {
	    return true;
	}
	// if the user is member of group, that is listed in the trail
	else if (userid != null && "group".equals(trail.getAccess())) {
	    List<ObjectId> ids = trail.getGroups();
	    if (ids != null) {
		List<Group> groups = groupRep.findByIds(ids);
		for (Group g : groups) {
		    List<ObjectId> members = g.getMembers();
		    for (ObjectId m : members) {
			if (userid.equals(m.toHexString())) {
			return true;
			}
		    }
		}
	    }
	    return false;
	}
	// if trail has public access
	else if ("public".equals(trail.getAccess())) {
	    return true;
	}
	// all other cases, e.g: the user is not authenticated and the trail is not public
	//    or the trail is private and the user is not owner
	else {
	    return false;
	}
    }

    private boolean verifyImageAccess(Image img, String userid) {

	// permissions of image finds from picture collection
	List<Picture> pics = picRep.findByImageid(new ObjectId(img.getId()));
	if (pics.size() != 1) {
	    return false;
	}
	Picture pic = pics.get(0);
	String access = pic.getAccess();
	List<ObjectId> groupids = pic.getGroups();

	List<Trail> trails = trailRep.findTrailById(pic.getTrailid().toHexString());
	if (trails.size() != 1) {
	    return false;
	}
	Trail trail = trails.get(0);
	if (!verifyTrailAccess(trail, userid)) {
	    return false;
	}
	// if the user is owner of the trail
	if (userid != null && userid.equals(trail.getUserid().toHexString())) {
	    return true;
	}
	// if the user is member of group, that is listed in the picture
	else if (userid != null && "group".equals(access)) {
	    if (groupids != null) {
		List<Group> groups = groupRep.findByIds(groupids);
		for (Group g : groups) {
		    List<ObjectId> members = g.getMembers();
		    for (ObjectId m : members) {
			if (userid.equals(m.toHexString())) {
			    return true;
			}
		    }
		}
	    }
	    return false;
	}
	// if picture has public access or the access is not defined yet
	else if (access == null || "public".equals(access)) {
	    return true;
	}
	// all other cases, e.g: the user is not authenticated and the picture is not public
	//    or the picture is private and the user is not owner
	else {
	    return false;
	}
    }
    
    public String elapsedTime(List<Location> locs) {
	if (locs.size() > 1) {
	    String dateStr1 = locs.get(0).getTimestamp();
	    String dateStr2 = locs.get(locs.size() - 1).getTimestamp();
	    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
	    LocalDateTime date1 = LocalDateTime.parse(dateStr1, formatter);
	    LocalDateTime date2 = LocalDateTime.parse(dateStr2, formatter);
	    Duration duration = Duration.between(date1, date2);
	    long sec = duration.getSeconds();
	    long hr = sec / 3600;
	    long min = (sec % 3600) / 60;
	    return hr + " h " + min + " min";
	}
	return "--";
    }

    private String distance(List<Location> locs) {
	double dist = 0;
	if (locs.size() > 1) {
	    double lat, lng, preLat=0, preLng=0; 
	    boolean first = true;
	    for (Location loc : locs) {
		lat = loc.getLoc().getCoordinates()[1];
		lng = loc.getLoc().getCoordinates()[0];
		if (first) {
		    first = false;
		}
		else {
		    dist += DistanceCalculator.distance(preLat, preLng, lat, lng, "M");
		}
		preLat = lat; 
		preLng = lng;
	    }
	}
	return String.format("%.1f", dist);
    }

    private String formatDate(String str) {
	try {
	    LocalDateTime date =
		LocalDateTime.parse(str, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
	    str = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
	} catch (DateTimeParseException e) {
	    log.error("formatDate failed: " + e.toString());
	} catch (NullPointerException e) {
	    log.error("formatDate failed: " + e.toString());
	}
	return str;
    }
    
    @RequestMapping(value = "/trail/{id}", method = RequestMethod.GET)

    public String trails(@PathVariable("id") String trailid,
			 @CookieValue(value = "token", defaultValue = "") String token) throws IOException {

	log.info("/trails/*");

	String userid = authenticateUser(token);

	List<Trail> trails = trailRep.findTrailById(trailid);
	if (trails.size() != 1) {
	    log.error("Trail not found, trailid=" + trailid);
	    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trail not found");
	}
	Trail trail = trails.get(0);

	if (!verifyTrailAccess(trail, userid)) {
	    log.error("User don't have permission to this trail");
	    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "You don't have permission to this trail");
	}
	trail.setDate(formatDate(trail.getDate()));

	List<Location> locs = locRep.findByTrailid(new ObjectId(trailid));

	Collections.sort(locs, new LocationComparator());
	trail.setTime(elapsedTime(locs));
	trail.setDistance(distance(locs));

	List<User> users = userRep.findUserById(trail.getUserid());

	if (users.size() == 1) {
	    User user = users.get(0);
	    if (!user.getFullname().equals("")) {
		trail.setUser(user.getFullname());
	    }
	    else {
		trail.setUser(user.getUsername());
	    }
	    if (userid != null && userid.equals(user.getId())) {
		trail.setOwner(true);
	    }
	}
	List<Group> groups = new ArrayList<Group>();
	List<ObjectId> groupids = trail.getGroups();

	if (groupids != null && groupids.size() > 0) {
	    groups = groupRep.findByIds(groupids);
	}
	model.put("info", trail);
	model.put("groups", groups);
	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/trail.jade");
	return jadeConfig.renderTemplate(temp, model);
    }

    @RequestMapping(value = "/trail/{id}/track", method = RequestMethod.GET)

    public GetTrailResponse gettrail(@PathVariable("id") String trailid,
				     @CookieValue(value = "token", defaultValue = "") String token)
	throws IOException {

	String userid = authenticateUser(token);

	log.info("/trail/{id}/track");
	List<Trail> trails = trailRep.findTrailById(trailid);
	if (trails.size() != 1) {
	    log.error("Trail not found, trailid=" + trailid);
	    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trail not found");
	}
	Trail trail = trails.get(0);
	if (!verifyTrailAccess(trail, userid)) {
	    log.error("User don't have permission to this trail");
	    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "You don't have permission to this trail");
	}
	List<Location> locs = locRep.findByTrailid(new ObjectId(trailid));
	Collections.sort(locs, new LocationComparator());

	List<Picture> pics;

	if (userid != null && verifyTrailOwner(trailid, userid)) {
	    pics = picRep.findByTrailid(new ObjectId(trailid));
	}
	else {
	    //List<ObjectId> ids = new ArrayList<ObjectId>();
	    ObjectId[] ids = new ObjectId[0];
 	    if (userid != null) {
		List<Group> groups = groupRep.findByMembers(new ObjectId(userid));
		ids = new ObjectId[groups.size()];
		    for (int i=0; i < groups.size(); i++) {
		    //ids.add(new ObjectId(g.getId()));
			ids[i] = new ObjectId(groups.get(i).getId());
		}
	    }
	    pics = picRep.findByTrailidAndGroupidsOrPublicAccess(new ObjectId(trailid), ids);
	    //pics = picRep.findByTrailid(new ObjectId(trailid));
	}
	return new GetTrailResponse("ok",
				    trail.getDate(),
				    trail.getTrailname(),
				    trail.getLocation(),
				    trail.getDescription(),
				    locs,
				    pics);
    }

    @RequestMapping(value = "/trail/{id}/edit", method = RequestMethod.GET)

    public String edittrail(@PathVariable("id") String trailid,
			    @CookieValue(value = "token", defaultValue = "") String token) throws IOException {


	log.info("/trail/{id}/edit");

	List<Trail> trails = trailRep.findTrailById(trailid);
	if (trails.size() != 1) {
	    log.error("Trail not found, trailid=" + trailid);
	    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trail not found");
	}
	Trail trail = trails.get(0);
	List<Location> locs = locRep.findByTrailid(new ObjectId(trailid));

	Collections.sort(locs, new LocationComparator());
	trail.setTime(elapsedTime(locs));
	    
	model.put("info", trail);
	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/edittrail.jade");
	return jadeConfig.renderTemplate(temp, model);
    }
    
    private String insertTrail(JsonNode node, String userid) {
	if (node == null)
	    return null;
	Trail trail = new Trail();
	//trail.setAccess(node.path("access").textValue());
	trail.setAccess("private");
	trail.setDate(node.path("date").textValue());
	trail.setTrailname(node.path("trailname").textValue());
	trail.setLocation(node.path("locationname").textValue());
	trail.setDescription(node.path("description").textValue());
	trail.setUserid(new ObjectId(userid));
	trail = trailRep.insert(trail);
	return (trail != null) ? trail.getId() : null;
    }

    private void insertLocations(JsonNode root, String trailid) {
	if (root == null)
	    return;
	List<Location> locations = new ArrayList<Location>();
	Iterator<JsonNode> it = root.elements();
	while (it.hasNext()) {
	    JsonNode node = it.next();
	    Location location = new Location();
	    Loc loc = new Loc();

	    location.setTrailid(new ObjectId(trailid));
	    location.setTimestamp(node.path("timestamp").textValue());

	    loc.setType(node.path("loc").path("type").textValue());

	    node = node.path("loc").path("coordinates");
	    Iterator<JsonNode> it2 = node.elements();
	    double[] coords = new double[3];
	    int i = 0;
	    while (it2.hasNext()) {
		coords[i++] = it2.next().doubleValue();
	    }
	    loc.setCoordinates(coords);
	    location.setLoc(loc);
	    locations.add(location);
	}
	locRep.insert(locations);
    }
    
    private void insertPictures(JsonNode root, String trailid) {
	if (root == null)
	    return;
	Iterator<JsonNode> it = root.elements();
	while (it.hasNext()) {
	    JsonNode node = it.next();
	    Image img = new Image();
	    Picture pic = new Picture();
	    Loc loc = new Loc();

	    img.setImg(node.path("file").textValue());
	    img = imgRep.insert(img);
	    
	    pic.setImageid(new ObjectId(img.getId()));
	    pic.setTrailid(new ObjectId(trailid));
	    pic.setTimestamp(node.path("timestamp").textValue());
	    pic.setFilename(node.path("filename").textValue());
	    pic.setPicturename(node.path("picturename").textValue());
	    pic.setDescription(node.path("description").textValue());

	    loc.setType(node.path("loc").path("type").textValue());
	    
	    node = node.path("loc").path("coordinates");
	    Iterator<JsonNode> it2 = node.elements();
	    double[] coords = new double[3];
	    int i = 0;
	    while (it2.hasNext()) {
		coords[i++] = it2.next().doubleValue();
	    }
	    loc.setCoordinates(coords);

	    pic.setLoc(loc);
	    picRep.insert(pic);
	}
    }

    @RequestMapping(value = "/addtrail", method = RequestMethod.POST)

    public Response addtrail(@RequestBody String body) throws IOException {

	JsonNode trailNode = null;
	JsonNode locationsNode = null;
	JsonNode picturesNode = null;
	String username = "";
	String passwd = "";
	
	ObjectMapper mapper = new ObjectMapper();
	JsonNode root = mapper.readTree(body);
	root = root.path("newtrail");
	
	Iterator<JsonNode> it = root.elements();
	while (it.hasNext()) {
            JsonNode node = it.next();
	    String type = node.path("type").textValue();

	    if ("UserInfo".equals(type)) {
		username = node.path("username").textValue();
		passwd = node.path("password").textValue();
	    }
	    else if ("TrailInfo".equals(type)) {
		trailNode = node;
	    }
	    else if ("LocationCollection".equals(type)) {
		locationsNode = node.path("locations");
	    }
	    else if ("PictureCollection".equals(type)) {
		picturesNode = node.path("pictures");
	    }
	}

	String userid = authenticateUser(username, passwd);
	if (userid == null) {
	    log.error("Wrong username '" + username + "' or password");
	    return new Response("notok", "Wrong username or password");
	}

	String trailid = insertTrail(trailNode, userid);
	if (trailid == null) {
	    log.error("Database error: The new trail wasn't added to db.");
	    return new Response("notok", "Database error: The new trail wasn't added to db.");
	}
	insertLocations(locationsNode, trailid);
	insertPictures(picturesNode, trailid);
	
	return new Response("ok", "");
    }
    
    @RequestMapping(value = "/trail/{id}", method = RequestMethod.PUT)

    public Response update(@PathVariable("id") String trailid,
			   @CookieValue(value = "token", defaultValue = "") String token,
			   @RequestBody String body) throws IOException {
	
	log.info("/updatetrail");
	String userid = authenticateUser(token);
	if (userid == null) {
	    log.error("Expired or invalid token '" + token + "'");
	    return new Response("notok", "Expired or invalid session");
	}
	ObjectMapper mapper = new ObjectMapper();
	JsonNode root = mapper.readTree(body);
	String trailname = root.path("trailname").textValue();
	String location = root.path("location").textValue();
	String description = root.path("description").textValue();

	if (!verifyTrailOwner(trailid, userid)) {
	    return new Response("notok", "User is not owner of this trail");
	}

	trailRep.update(trailid, trailname, location, description);
	// update will return 0, if there are not changes in fields, so we don't want to return notok here 
	//    return new Response("notok", "Database error, the trail wasn't updated");

	Iterator<JsonNode> it = root.path("updates").elements();
	while (it.hasNext()) {
            JsonNode node = it.next();
	    String action = node.path("action").textValue();

	    if ("updatePictureLocation".equals(action)) {
		String id = node.path("id").textValue();
		double lat = node.path("lat").doubleValue();
		double lng = node.path("lng").doubleValue();
		picRep.updateLoc(id, lat, lng);
	    }
	    else if ("removePicture".equals(action)) {
		String id = node.path("id").textValue();
		List<Picture> pics = picRep.findByPictureid(id);
		if (pics.size() == 1) {
		    imgRep.deleteById(pics.get(0).getImageid());
		    picRep.deleteById(id);
		}
		else {
		    log.error("Couldn't delete picture, because id[" + id + "] not found from database");
		}
	    }
	    else if ("updatePicturename".equals(action)) {
		String id = node.path("id").textValue();
		String name = node.path("name").textValue();
		picRep.updateName(id, name);
	    }
	    else if ("removeLocation".equals(action)) {
		String id = node.path("id").textValue();
		locRep.deleteById(id);
	    }
	    else if ("updateLocation".equals(action)) {
		String id = node.path("id").textValue();
		double lat = node.path("lat").doubleValue();
		double lng = node.path("lng").doubleValue();
		locRep.update(id, lat, lng);
	    }
	}
	return new Response("ok", "");
    }

    @RequestMapping(value = "/trail/{id}/permissions", method = RequestMethod.GET)

    public String permissions(@PathVariable("id") String trailid,
			      @CookieValue(value = "token", defaultValue = "") String token) throws IOException, ResponseStatusException {

	log.info("/trail/{id}/permissions");
	String userid = authenticateUser(token);
	if (userid == null) {
	    log.error("Expired or invalid session token '" + token + "'");
	    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired session");
	}
	List<Trail> trails = trailRep.findTrailById(trailid);

	if (trails.size() != 1) {
	    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trail not found");
	}
	List<Group> groups = groupRep.findByOwnerid(new ObjectId(userid));

	Trail trail = trails.get(0);
	List<ObjectId> ids = trail.getGroups();
	if (ids != null) {
	    for (Group g : groups) {
		for (ObjectId id : ids) {
		    if (g.getId().equals(id.toString())) {
			g.setChecked(true);
		    }
		}
	    }
	}
	model.put("title", "Edit permissions of the trail");
	model.put("trailid", trail.getId());
	model.put("trailname", trail.getTrailname());
	model.put("access", trail.getAccess());
	model.put("groups", groups);
	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/permissions.jade");
	return jadeConfig.renderTemplate(temp, model);
    }


    @RequestMapping(value = "/trail/{id}/permissions", method = RequestMethod.PUT)

    public Response updatePermissions(@PathVariable("id") String trailid,
				      @CookieValue(value = "token", defaultValue = "") String token,
				      @RequestParam(name="access", required=true, defaultValue="") String access,
				      @RequestParam(value="groups[]", required=false) String[] groups)
	throws IOException, ResponseStatusException {

	log.info("/trail/{id}/permissions");
	String userid = authenticateUser(token);
	if (userid == null) {
	    log.error("Expired or invalid session token '" + token + "'");
	    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired session");
	}
	if (!verifyTrailOwner(trailid, userid)) {
	    return new Response("notok", "User is not owner of this trail");
	}
	if (trailRep.updateAccess(trailid, access, groups) != 1) {
	    log.error("Database error, accesses of the trail weren't updated");
	    return new Response("notok", "Database error, accesses of the trail weren't updated");
	}
	return new Response("ok", "");
    }

    @RequestMapping(value = "/image/{id}", method = RequestMethod.GET, produces = MediaType.IMAGE_JPEG_VALUE)

    public @ResponseBody byte[] images(@CookieValue(value = "token", defaultValue = "") String token,
				       @PathVariable("id") String imgid) throws IOException {

	log.info("/images/*");
	String userid = authenticateUser(token);

	List<Image> imgs = imgRep.findImageById(new ObjectId(imgid));
	if (imgs.size() != 1) {
	    log.error("Image not found, id=" + imgid);
	    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
	}
	Image img = imgs.get(0);

	if (!verifyImageAccess(img, userid)) {
	    log.error("User don't have access to this image");
	    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "You don't have permission to get this image");
	}
	String strImg = img.getImg();
	byte[] decodedString = Base64.getDecoder().decode(new String(strImg).getBytes("UTF-8"));
	return decodedString;
    }

    @RequestMapping(value = "/trail/*", method = RequestMethod.DELETE)

    public Response delete(@CookieValue(value = "token", defaultValue = "") String token,
			   HttpServletRequest request) throws IOException, ResponseStatusException {

	String userid = authenticateUser(token);
	if (userid == null) {
	    log.error("Expired or invalid session token '" + token + "'");
	    return new Response("notok", "Expired token");
	}
	String[] parts =  request.getRequestURI().split("/", 0);
	if (parts.length < 3) {
	    log.error("Invalid query string '" + request.getRequestURI() + "'");
	    return new Response("notok", "Invalid query string");
	}
	String trailid = parts[2];

	if (!verifyTrailOwner(trailid, userid)) {
	    return new Response("notok", "User is not owner of this trail");
	}
	locRep.deleteByTrailid(new ObjectId(trailid)); // delete all locations of this trail
	List<Picture> pics = picRep.findByTrailid(new ObjectId(trailid)); // find all pictures of this trail
	for (Picture pic : pics) {
	    imgRep.deleteById(pic.getImageid()); // delete one image
	    picRep.deleteById(pic.getId());      // delete one picture
	}
	trailRep.deleteById(trailid); // delete this trail
	return new Response("ok", "");
    }

    @RequestMapping(value = "/trail/{trailid}/picture/{picid}/permissions", method = RequestMethod.PUT)

    public Response updatePicturePermissions(@PathVariable("trailid") String trailid,
					     @PathVariable("picid") String picid,
					     @CookieValue(value = "token", defaultValue = "") String token,
					     @RequestParam(name="access", required=true, defaultValue="") String access,
					     @RequestParam(value="groups[]", required=false, defaultValue="") String[] groups)
	throws IOException, ResponseStatusException {

	log.info("/trail/{trailid}/picture/{picid}/permissions");
	String userid = authenticateUser(token);
	if (userid == null) {
	    log.error("Expired or invalid session token '" + token + "'");
	    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired session");
	}
	if (!verifyTrailOwner(trailid, userid)) {
	    return new Response("notok", "User is not owner of this trail");
	}
	List<Picture> pics = picRep.findByPictureid(picid);
	if (pics.size() != 1) {
	    log.error("The picture not found from database.");
	    return new Response("notok", "The picture not found from database.");
	}
	if (!trailid.equals(pics.get(0).getTrailid().toHexString())) {
	    log.error("The picture is not belong to this trail");
	    return new Response("notok", "The picture is not belong to this trail");
	}
	if (picRep.updateAccess(picid, access, Utils.stringArrayToObjectIdList(groups)) != 1) {
	    log.error("Database error, accesses of the picture weren't updated");
	    return new Response("notok", "Database error, accesses of the picture weren't updated");
	}
	return new Response("ok", "");
    }
}
