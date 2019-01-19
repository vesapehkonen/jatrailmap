package jatrailmap;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import java.util.Date;

import org.bson.types.ObjectId;
import de.neuland.jade4j.JadeConfiguration;

import javax.servlet.http.HttpServletResponse;
import org.springframework.web.server.ResponseStatusException;
import javax.servlet.http.HttpServletRequest;


import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.ExpectedException;
import org.junit.Rule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import org.springframework.util.FileCopyUtils;
    
@RunWith(MockitoJUnitRunner.class)
public class TrailCtrlTest {

    @Mock
    private UserRepository userRep;
    @Mock
    private TrailRepository trailRep;
    @Mock
    private LocationRepository locRep;
    @Mock
    private PictureRepository picRep;
    @Mock
    private ImageRepository imgRep;
    @Mock
    private SessionRepository sessionRep;
    @Spy
    private Map<String, Object> model = new HashMap<String, Object>();
    @Mock
    private JadeConfiguration jadeConfig;

    @InjectMocks
    TrailCtrl trailctrl;

    @Rule
    public ExpectedException thrown= ExpectedException.none();
    
    @Test
    public void testTrails() throws IOException {

	HttpServletRequest request = mock(HttpServletRequest.class);

	String userid = "59e43b37e559eb1d51a3d29a";
	String trailid = "59e6d076e7004f1e3839d804";
	String locationid1 = "59e6d076e7004f1e3839d804";
	String locationid2 = "59e6d076e7004f1e3839d804";
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	
	List<Trail> trailList = new ArrayList<Trail>();
	trailList.add(new Trail(trailid, new ObjectId(userid), "public", "2018-03-05T18:05:29-08:00", "trailname1", "", "", null));

	List<User> userList = new ArrayList<User>();
	userList.add(new User(userid, "username", "", "", "", "", "", ""));

	List<Location> locList = new ArrayList<Location>();
	Loc loc = new Loc("Point", new double[]{ -121.70601987, 45.6998306, 56 });
	locList.add(new Location(locationid1, new ObjectId(trailid), "2018-03-05T18:05:29-08:00", loc)); 
	loc = new Loc("Point", new double[]{ -121.70601987, 45.6998306, 56 });
	locList.add(new Location(locationid2, new ObjectId(trailid), "2018-03-05T18:05:29-08:00", loc)); 
	
	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	//when(request.getRequestURI()).thenReturn("/trails/" + trailid);
	when(trailRep.findTrailById(trailid)).thenReturn(trailList);
	when(userRep.findUserById(new ObjectId(userid))).thenReturn(userList);
	when(locRep.findByTrailid(new ObjectId(trailid))).thenReturn(locList);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	// test that the user is authenticated and he is owner of the trail
	trailctrl.trails(trailid, token);
	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/trail.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());

	Trail trail = (Trail)model.get("info");
	assertEquals(true, trail.getOwner());
	assertEquals(trailid, trail.getId());
	assertEquals(new ObjectId(userid), trail.getUserid());

	// test that the user is NOT authenticated
	trailList.get(0).setOwner(false);
	trailctrl.trails(trailid, "");
	verify(jadeConfig, times(2)).getTemplate("./src/main/resources/jade/trail.jade");
	verify(jadeConfig, times(2)).renderTemplate(any(), any());

	trail = (Trail)model.get("info");
	assertEquals(false, trail.getOwner());
	assertEquals(trailid, trail.getId());
	assertEquals(new ObjectId(userid), trail.getUserid());
    }   

    @Test
    public void testGettrail() throws IOException {

	String userid         = "59e43b37e559eb1d51a3d29a";
	String trailid        = "59e6d076e7004f1e3839d804";
	String locid          = "59e43e30e7004f1e3839d535";
	String picid          = "59e43e30e7004f1e3839d533";
	String imgid          = "59e43e30e7004f1e3839d530";
	String wrongTrailid   = "59e43e2fe7004f1e3839d525";
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	
	List<Trail> trailList = new ArrayList<Trail>();
	trailList.add(new Trail(trailid, new ObjectId(userid), "public", "2018-03-05T18:05:29-08:00", "trailname", "", "", null));

	List<Location> locList = new ArrayList<Location>();
	Loc loc = new Loc("Point", new double[]{ -121.70601987, 45.6998306, 56 });
	locList.add(new Location(locid, new ObjectId(trailid), "2018-03-05T18:05:29-08:00", loc)); 
	
	List<Picture> picList = new ArrayList<Picture>();
	loc = new Loc("Point", new double[]{ -121.70601987, 45.6998306, 56 });
	picList.add(new Picture(picid, new ObjectId(trailid), new ObjectId(imgid), "", "", "", "", loc));

	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));
	
	when(trailRep.findTrailById(trailid)).thenReturn(trailList);
	when(locRep.findByTrailid(new ObjectId(trailid))).thenReturn(locList);
	when(picRep.findByTrailid(new ObjectId(trailid))).thenReturn(picList);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);
	
	GetTrailResponse resp = trailctrl.gettrail(trailid, token);
	assertEquals("ok", resp.getStatus());
	assertEquals("trailname", resp.getTrailname());
	assertEquals(locid, resp.getLocs().get(0).getId());
	assertEquals(picid, resp.getPics().get(0).getId());

	thrown.expect(ResponseStatusException.class);
	trailctrl.gettrail(wrongTrailid, token);
    }   

    @Test
    public void testEdittrails() throws IOException {

	HttpServletRequest request = mock(HttpServletRequest.class);

	String userid = "59e43b37e559eb1d51a3d29a";
	String trailid = "59e6d076e7004f1e3839d804";
	String locationid1 = "59e6d076e7004f1e3839d804";
	String locationid2 = "59e6d076e7004f1e3839d804";
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";

	List<Trail> trailList = new ArrayList<Trail>();
	trailList.add(new Trail(trailid, new ObjectId(userid), "public", "2018-03-05T18:05:29-08:00", "trailname", "", "", null));

	List<Location> locList = new ArrayList<Location>();
	Loc loc = new Loc("Point", new double[]{ -121.70601987, 45.6998306, 56 });
	locList.add(new Location(locationid1, new ObjectId(trailid), "2018-03-05T18:05:29-08:00", loc)); 
	loc = new Loc("Point", new double[]{ -121.70601987, 45.6998306, 56 });
	locList.add(new Location(locationid2, new ObjectId(trailid), "2018-03-05T18:05:29-08:00", loc)); 

	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));
	
	//when(request.getRequestURI()).thenReturn("/trails/" + trailid);
	when(trailRep.findTrailById(trailid)).thenReturn(trailList);
	when(locRep.findByTrailid(new ObjectId(trailid))).thenReturn(locList);
	//when(sessionRep.findByToken(token)).thenReturn(sessionList);

	trailctrl.edittrail(trailid, token);
	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/edittrail.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());

	Trail trail = (Trail)model.get("info");
	assertEquals(trailid, trail.getId());
	assertEquals(new ObjectId(userid), trail.getUserid());
    }   

    @Test
    public void testAddtrail() throws IOException {

	String body = new String(FileCopyUtils.copyToByteArray(new File("./src/test/resources/newtrail.json")));

	Trail trail = new Trail("59e6d076e7004f1e3839d804", null, "", "", "", "", "", null);
	Image img = new Image("59e6d076e7004f1e3839d804", "");
	
	List<User> userList = new ArrayList<User>();
	userList.add(new User("59e6d076e7004f1e3839d804", "username", "password", "First User",
			      "USA", "OR", "Portland", "first.user@email.com"));
	when(userRep.findByUsername("username")).thenReturn(userList);
	when(trailRep.insert(any(Trail.class))).thenReturn(trail);
	when(imgRep.insert(any(Image.class))).thenReturn(img);

	Response resp = trailctrl.addtrail(body);
	assertEquals("ok", resp.getStatus());
    }   

    @Test
    public void testUpdate() throws IOException {
	// todo
	//trailctrl.update("username", "password", body);
    }
    
    @Test
    public void testPermissions() throws IOException {
	// todo
	//trailctrl.permissions("username", "password", request);
    }

    @Test
    public void testUpdatePermissions() throws IOException {
	// todo
	//trailctrl.permissions("username", "password", access, groups, request);
    }

    @Test
    public void testImages() throws IOException {
	// todo
	//trailctrl.images("username", "password", request);
    }

    @Test
    public void testDelete() throws IOException {
	// todo
	//trailctrl.delete("username", "password", request);
    }

}
