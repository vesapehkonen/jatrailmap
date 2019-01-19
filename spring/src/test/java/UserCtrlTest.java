package jatrailmap;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import org.bson.types.ObjectId;
import de.neuland.jade4j.JadeConfiguration;

import javax.servlet.http.HttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@RunWith(MockitoJUnitRunner.class)
public class UserCtrlTest {

    @Mock
    private UserRepository userRep;
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
    @Mock
    private BCryptPasswordEncoder encoder;

    @InjectMocks
    UserCtrl userctrl;

    
    @Test
    public void testLogin() throws IOException {

	String userid  = "59e43b37e559eb1d51a3d29a";
	String username = "username";
	String password = "password";
	String hash = "$2a$12$BxRqt.VcUSAwpLEKzhdUOeGWybMhrVTvU2DQEwGeoWXIo/SYV.HwG";

	HttpServletResponse resp = mock(HttpServletResponse.class);
	List<User> emptyList = new ArrayList<User>();
	List<User> userList = new ArrayList<User>();
	User user = new User(userid, username, hash, "First User", "USA", "OR", "Portland", "first.user@email.com");
	userList.add(user);

	when(userRep.findByUsername(anyString())).thenReturn(emptyList);
	when(userRep.findByUsername("username")).thenReturn(userList);
	when(encoder.matches(anyString(), anyString())).thenReturn(false);
	when(encoder.matches("password", hash)).thenReturn(true);

	Response response = userctrl.login("username", "password", resp);
	assertEquals("ok", response.getStatus());

	response = userctrl.login("wrongusername", "password", resp);
	assertEquals("notok", response.getStatus());

	response = userctrl.login("username", "wrongpassword", resp);
	assertEquals("notok", response.getStatus());

	response = userctrl.login(null, null, resp);
	assertEquals("notok", response.getStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLogout() throws IOException {
	HttpServletResponse resp = mock(HttpServletResponse.class);
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";

	List<Trail> trails = new ArrayList<Trail>();
	Trail trail = new Trail("5bec9ce0e62361752a8a05b6", new ObjectId("5bec9ce0e62361752a8a05b6"), "public", "", "trail1", "", "", null);
	trails.add(trail);
	when(trailRep.findByPublic()).thenReturn(trails);

	userctrl.logout(token, resp);

	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/main.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());

	trails = (ArrayList<Trail>)model.get("trails");
	assertEquals("trail1", trails.get(0).getTrailname());
    }

    @Rule
    public ExpectedException thrown= ExpectedException.none();

    @Test
    public void testUserinfo() throws IOException {
	HttpServletResponse resp = mock(HttpServletResponse.class);

	//List<User> emptyList = new ArrayList<User>();

	String userid  = "59e43b37e559eb1d51a3d29a";
	List<User> userList = new ArrayList<User>();
	User user = new User(userid, "username", "password", "First User", "USA", "OR", "Portland", "first.user@email.com");
	userList.add(user);

	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	List<Session> emptyList = new ArrayList<Session>();

	//when(userRep.findByUsername(anyString())).thenReturn(emptyList);
	//when(userRep.findByUsername("username")).thenReturn(userList);
	when(userRep.findUserById(new ObjectId(userid))).thenReturn(userList);

	when(sessionRep.findByToken(anyString())).thenReturn(emptyList);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	userctrl.userinfo(token);
	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/userinfo.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());
	user = (User)model.get("info");
	assertEquals("username", user.getUsername());
	assertEquals("first.user@email.com", user.getEmail());

	thrown.expect(ResponseStatusException.class);
	userctrl.userinfo("");
    }   

    @Test
    public void testNewuser() throws IOException {
	userctrl.newuser();
	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/newuser.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());
	assertEquals("New user", (String)model.get("title"));
	assertEquals(false, (boolean)model.get("authenticated"));
    }   

    @Test
    public void testUpdateuser() throws IOException {

	String userid  = "59e43b37e559eb1d51a3d29a";
	String username = "username";
	String password = "password";
	String hash = "$2a$12$BxRqt.VcUSAwpLEKzhdUOeGWybMhrVTvU2DQEwGeoWXIo/SYV.HwG";

	List<User> emptyList = new ArrayList<User>();
	List<User> userList = new ArrayList<User>();
	User user = new User(null, username, hash, "First User", "USA", "OR", "Portland", "first.user@email.com");
	userList.add(user);

	when(userRep.findByUsername(anyString())).thenReturn(emptyList);
	when(userRep.findByUsername("username")).thenReturn(userList);
	when(userRep.save(any())).thenReturn(new User());
	when(encoder.matches(anyString(), anyString())).thenReturn(false);
	when(encoder.matches("password", hash)).thenReturn(true);

	Response response = userctrl.updateuser("username", "password", "Changed Name", "USA", "OR", "Portland", "new.email@email.com");
	assertEquals("ok", response.getStatus());

	response = userctrl.updateuser("wrong username", "password", "Changed Name", "USA", "OR", "Portland", "new.email@email.com");
	assertEquals("notok", response.getStatus());

	response = userctrl.updateuser("username", "wrong password", "Changed Name", "USA", "OR", "Portland", "new.email@email.com");
	assertEquals("notok", response.getStatus());
    }   

    @Test
    public void testUpdatepassword() throws IOException {

	String userid  = "59e43b37e559eb1d51a3d29a";
	String hash = "$2a$12$BxRqt.VcUSAwpLEKzhdUOeGWybMhrVTvU2DQEwGeoWXIo/SYV.HwG";

	HttpServletResponse resp = mock(HttpServletResponse.class);
	List<User> emptyList = new ArrayList<User>();
	List<User> userList = new ArrayList<User>();
	User user = new User(userid, "username", hash, "First User", "USA", "OR", "Portland", "first.user@email.com");
	userList.add(user);

	when(userRep.findByUsername(anyString())).thenReturn(emptyList);
	when(userRep.findByUsername("username")).thenReturn(userList);
	when(userRep.save(any())).thenReturn(new User());
	when(encoder.matches(anyString(), anyString())).thenReturn(false);
	when(encoder.matches("password", hash)).thenReturn(true);

	Response response = userctrl.updatepassword("username", "password", "new password", resp);
	assertEquals("ok", response.getStatus());

	response = userctrl.updatepassword("wrong username", "password", "new password", resp);
	assertEquals("notok", response.getStatus());

	response = userctrl.updatepassword("username", "wrong password", "new password", resp);
	assertEquals("notok", response.getStatus());
    }   
}
