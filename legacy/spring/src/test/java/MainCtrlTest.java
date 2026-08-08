package jatrailmap;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import org.bson.types.ObjectId;
import de.neuland.jade4j.JadeConfiguration;

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

@RunWith(MockitoJUnitRunner.class)
public class MainCtrlTest {

    //@Mock
    //private UserRepository userRep;
    @Mock
    private TrailRepository trailRep;
    @Mock
    private GroupRepository groupRep;
    @Mock
    private SessionRepository sessionRep;
    @Spy
    private Map<String, Object> model = new HashMap<String, Object>();
    @Mock
    private JadeConfiguration jadeConfig;

    @InjectMocks
    MainCtrl mainctrl;

    @Test
    @SuppressWarnings("unchecked")
    public void testMain() throws IOException {

	String userid = "59e43b37e559eb1d51a3d29a";
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";

	List<User> emptyList = new ArrayList<User>();
	List<User> userList = new ArrayList<User>();

	userList.add(new User(userid, "username", "password", "First User",
			      "USA", "OR", "Portland", "first.user@email.com"));

	List<ObjectId> members = new ArrayList<ObjectId>();
	members.add(new ObjectId(userid));
	members.add(new ObjectId("59e6d076e7004f1e3839d804"));

	List<Group> groups = new ArrayList<Group>();

	groups.add(new Group("5a98acfedc114e44041db40b",
			     new ObjectId("5a98acfedc114e44041db40b"),
			     "groupname", members));

	List<ObjectId> groupids = new ArrayList<ObjectId>();
	groupids.add(new ObjectId("5a98acfedc114e44041db40b"));

	List<Trail> publicTrails = new ArrayList<Trail>();

	publicTrails.add(new Trail("5bec9ce0e62361752a8a05b6",
				   new ObjectId("5bec9ce0e62361752a8a05b6"),
				   "public", "2017-10-15T22:08:18-07:00",
				   "publicTrailName", "", "", null));

	List<Trail> groupAndPublicTrails = new ArrayList<Trail>();

	groupAndPublicTrails.add(new Trail("5bec9ce0e62361752a8a05b6",
					   new ObjectId("5bec9ce0e62361752a8a05b6"),
					   "group", "2017-10-15T22:08:18-07:00",
					   "groupTrailName1", "", "", null));

	groupAndPublicTrails.add(new Trail("5a9df76cf861e71dac5aa26f",
					   new ObjectId("5bec9ce0e62361752a8a05b6"),
					   "group", "2017-10-15T22:08:50-07:00",
					   "groupTrailName2", "", "", groupids));

	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	//when(userRep.findByUsername(anyString())).thenReturn(emptyList);
	//when(userRep.findByUsername("username")).thenReturn(userList);

	when(groupRep.findByMembers(new ObjectId(userid))).thenReturn(groups);
	when(trailRep.findByPublic()).thenReturn(publicTrails);
	when(trailRep.findByGroupsOrOwnerOrPublic(groupids, new ObjectId(userid)))
	    .thenReturn(groupAndPublicTrails);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	// user is logged in, test that public, group and user's own trails are shown
	mainctrl.main(token);
	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/main.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());
	groupAndPublicTrails  = (ArrayList<Trail>)model.get("trails");
	assertEquals("groupTrailName2", groupAndPublicTrails.get(0).getTrailname());
	assertEquals("groupTrailName1", groupAndPublicTrails.get(1).getTrailname());
	
	// user is not logged in, test that only public trails are shown
	mainctrl.main("");
	verify(jadeConfig, times(2)).getTemplate("./src/main/resources/jade/main.jade");
	verify(jadeConfig, times(2)).renderTemplate(any(), any());
	publicTrails = (ArrayList<Trail>)model.get("trails");
	assertEquals(1, publicTrails.size());
	assertEquals("publicTrailName", publicTrails.get(0).getTrailname());

	// test with null arguments, that only public trails are shown
	mainctrl.main(null);
	verify(jadeConfig, times(3)).getTemplate("./src/main/resources/jade/main.jade");
	verify(jadeConfig, times(3)).renderTemplate(any(), any());
	publicTrails = (ArrayList<Trail>)model.get("trails");
	assertEquals(1, publicTrails.size());
	assertEquals("publicTrailName", publicTrails.get(0).getTrailname());

    }
}
