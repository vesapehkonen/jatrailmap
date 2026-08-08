package jatrailmap;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CookieValue;

import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.JadeTemplate;
import org.bson.types.ObjectId;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.LocalDateTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class TrailComparator implements Comparator<Trail> {

    private static final Logger log = LoggerFactory.getLogger(TrailComparator.class);

    public int compare(Trail a, Trail b) {
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
	try {
	    Date dateA = sdf.parse(a.getDate());
	    Date dateB = sdf.parse(b.getDate());
	    return dateB.compareTo(dateA);
	} catch (ParseException e) {
	    log.error("error when parsing timestamp: " + e.toString());
	} catch (NullPointerException e) {
	    log.error("error when parsing timestamp: " + e.toString());
	    if (a.getDate() == null && b.getDate() != null) return 1;
	    else if (a.getDate() != null && b.getDate() == null) return -1;
	}
	return 0;
    }
}

@RestController
public class MainCtrl {

    private static final Logger log = LoggerFactory.getLogger(MainCtrl.class);
    @Autowired
    private TrailRepository trailRep;
    @Autowired
    private GroupRepository groupRep;
    //@Autowired
    //private UserRepository userRep;
    @Autowired
    private SessionRepository sessionRep;
    @Autowired
    private Map<String, Object> model;
    @Autowired
    private JadeConfiguration jadeConfig;

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

    @RequestMapping(value = "/", method = RequestMethod.GET)

    public String main(@CookieValue(value = "token", defaultValue = "") String token) throws IOException {
	
	log.info("/");
	List<Trail> trails = null;

	List<Session> sessions = sessionRep.findByToken(token);
	if (sessions.size() == 1) {
	    // user is logged in, we show public and all trails where user can access
	    ObjectId userid = sessions.get(0).getUserid();
	    List<ObjectId> groupids = new ArrayList<ObjectId>();
	    List<Group> groups = groupRep.findByMembers(userid);
	    for (Group g : groups) {
		groupids.add(new ObjectId(g.getId()));
	    }
	    trails = trailRep.findByGroupsOrOwnerOrPublic(groupids, userid);
	    model.put("authenticated", true);
	}
	else {
	    // user is not logged in, we show only public trails
	    trails = trailRep.findByPublic();
	    model.put("authenticated", false);
	}
	Collections.sort(trails, new TrailComparator());
	for (Trail trail : trails) {
	    trail.setDate(formatDate(trail.getDate()));
	}
	model.put("trails", trails);
	model.put("title", "Just Another Trail Map");

	JadeTemplate temp = jadeConfig.getTemplate("./src/main/resources/jade/main.jade");
	return jadeConfig.renderTemplate(temp, model);
    }
}
