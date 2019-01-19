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

@RunWith(MockitoJUnitRunner.class)
public class GroupCtrlTest {

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

    @InjectMocks
    GroupCtrl groupctrl;

    @Rule
    public ExpectedException thrown= ExpectedException.none();
    
    @Test
    public void testGroups() throws IOException {

	String owner = "59e43b37e559eb1d51a3d29a";
	ObjectId ownerId = new ObjectId(owner);
	ObjectId memberId = new ObjectId("59e6d076e7004f1e3839d804");
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";

	List<ObjectId> memberList = new ArrayList<ObjectId>();
	memberList.add(memberId);

	List<Group> groupList = new ArrayList<Group>();
	groupList.add(new Group(null, ownerId, "groupname", memberList));

	//List<User> userList = new ArrayList<User>();
	//userList.add(new User(owner, "username", "password", "First User",
	//		      "USA", "OR", "Portland", "first.user@email.com"));

	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, ownerId, new Date()));
	
	when(groupRep.findByOwnerid(ownerId)).thenReturn(groupList);
	//when(userRep.findByUsername("username")).thenReturn(userList);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	groupctrl.groups(token);
	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/groups.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());

	groupList = (List<Group>)model.get("groups");
	assertEquals("groupname", groupList.get(0).getName());
	memberList = groupList.get(0).getMembers();
	assertEquals(memberId, memberList.get(0));
	
	thrown.expect(ResponseStatusException.class);
	groupctrl.groups("");
    }   

    @Test
    public void testNewgroup() throws IOException {

	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	String userid  = "59e43b37e559eb1d51a3d29a";
	String otherid = "59e6d076e7004f1e3839d804";
	
	//List<User> userList = new ArrayList<User>();
	//userList.add(new User(userid, "username", "password", "First User",
	//		      "USA", "OR", "Portland", "first.user@email.com"));
	List<User> othersList = new ArrayList<User>();
	othersList.add(new User(otherid, "username2", "password2", "Second User",
			      "USA", "OR", "Portland", "second.user@email.com"));

	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	//when(userRep.findByUsername("username")).thenReturn(userList);
	when(userRep.findAllNotCurrent(userid)).thenReturn(othersList);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	// test that the new group page is shown including the list of other users
	groupctrl.newgroup(token);

	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/newgroup.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());

	othersList = (List<User>)model.get("users");
	assertEquals("username2", othersList.get(0).getUsername());

	// test with user is not authenticated
	thrown.expect(ResponseStatusException.class);
	groupctrl.newgroup("");
    }   

    @Test
    public void testAddgroup() throws IOException {

	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	String userid  = "59e43b37e559eb1d51a3d29a";
	String[] others = {"59e6d076e7004f1e3839d804"};

	//List<User> userList = new ArrayList<User>();
	//userList.add(new User(userid, "username", "password", "First User",
	//		      "USA", "OR", "Portland", "first.user@email.com"));

	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	//when(userRep.findByUsername("username")).thenReturn(userList);
	when(groupRep.insert(any(Group.class))).thenReturn(new Group());
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	Response resp = groupctrl.addgroup(token, "groupname", others);
	assertEquals("ok", resp.getStatus());

	resp = groupctrl.addgroup("", "groupname", others);
	assertEquals("notok", resp.getStatus());

	resp = groupctrl.addgroup(null, null, null);
	assertEquals("notok", resp.getStatus());
    }   

    @Test
    public void testEditgroup() throws IOException {

	HttpServletRequest request = mock(HttpServletRequest.class);

	String groupid = "5a98acfedc114e44041db40b";
	String userid  = "59e43b37e559eb1d51a3d29a";

	String memberId1 = "5a986b99eeeb633cf3ac2496";
	String memberId2 = "5a986bdbeeeb633cf3ac2497";

	String nonMemberId1 = "5be12272e62361404a1423de";
	String nonMemberId2 = "5be4aec1e623614f75756b25";

	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	
	List<ObjectId> memberList = new ArrayList<ObjectId>();
	memberList.add(new ObjectId(memberId1));
	memberList.add(new ObjectId(memberId2));

	List<ObjectId> nonMemberList = new ArrayList<ObjectId>();
	nonMemberList.add(new ObjectId(nonMemberId1));
	nonMemberList.add(new ObjectId(nonMemberId2));

	List<Group> groupList = new ArrayList<Group>();
	groupList.add(new Group(groupid, new ObjectId(userid), "groupname", memberList));

	//List<User> userList = new ArrayList<User>();
	//userList.add(new User(userid, "username", "password", "First User",
	//		      "USA", "OR", "Portland", "first.user@email.com"));

	List<User> memberUserList = new ArrayList<User>();
	memberUserList.add(new User(memberId1, "", "", "Member 1", "", "", "", ""));
	memberUserList.add(new User(memberId2, "", "", "Member 2", "", "", "", ""));

	List<User> nonMemberUserList = new ArrayList<User>();
	nonMemberUserList.add(new User(nonMemberId1, "", "", "NonMember 1", "", "", "", ""));
	nonMemberUserList.add(new User(nonMemberId2, "", "", "NonMember 2", "", "", "", ""));

	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	when(request.getRequestURI()).thenReturn("/editgroup/" + groupid);
	//when(userRep.findByUsername("username")).thenReturn(userList);
	when(groupRep.findByIdAndOwnerid(new ObjectId(groupid), new ObjectId(userid))).thenReturn(groupList);
	when(userRep.findByIds(memberList)).thenReturn(memberUserList);
	when(userRep.findByNotIds(memberList)).thenReturn(nonMemberUserList);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	// test that the new group page is shown including the list of other users
	groupctrl.editgroup(token, request);

	verify(jadeConfig, times(1)).getTemplate("./src/main/resources/jade/editgroup.jade");
	verify(jadeConfig, times(1)).renderTemplate(any(), any());

	assertEquals((String)model.get("groupid"), groupid);
	assertEquals((String)model.get("groupname"), "groupname");

	memberUserList = (List<User>)model.get("members");
	assertEquals(memberUserList.get(0).getFullname(), "Member 1");
	assertEquals(memberUserList.get(1).getFullname(), "Member 2");

	nonMemberUserList = (List<User>)model.get("nonmembers");
	assertEquals(nonMemberUserList.get(0).getFullname(), "NonMember 1");
	assertEquals(nonMemberUserList.get(1).getFullname(), "NonMember 2");

	// test with user is not authenticated
	thrown.expect(ResponseStatusException.class);
	groupctrl.editgroup("", request);

	// test with invalid request
	thrown.expect(ResponseStatusException.class);
	when(request.getRequestURI()).thenReturn("/editgroup/");
	groupctrl.editgroup(token, request);

	// test with null arguments
	thrown.expect(ResponseStatusException.class);
	when(request.getRequestURI()).thenReturn("/editgroup/");
	groupctrl.editgroup(null, null);
    }

    @Test
    public void testUpdategroup() throws IOException {

	String userid  = "59e43b37e559eb1d51a3d29a";
	String groupid = "5a98acfedc114e44041db40b";
	String[] others = {"59e6d076e7004f1e3839d804"};
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	
	//List<User> userList = new ArrayList<User>();
	//userList.add(new User(userid, "username", "password", "First User",
	//		      "USA", "OR", "Portland", "first.user@email.com"));

	List<Group> groupList = new ArrayList<Group>();
	groupList.add(new Group(groupid, new ObjectId(userid), "", null));
	
	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	//when(userRep.findByUsername("username")).thenReturn(userList);
	when(groupRep.findByIdAndOwnerid(new ObjectId(groupid), new ObjectId(userid))).thenReturn(groupList);
	when(groupRep.save(any(Group.class))).thenReturn(new Group());
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	Response resp = groupctrl.updategroup(token, groupid, "groupname", others);
	assertEquals("ok", resp.getStatus());

	resp = groupctrl.updategroup("", groupid, "groupname", others);
	assertEquals("notok", resp.getStatus());

	resp = groupctrl.updategroup(null, null, null, null);
	assertEquals("notok", resp.getStatus());
    }   

    @Test
    public void testDelete() throws IOException {

	HttpServletRequest request = mock(HttpServletRequest.class);

	String userid  = "59e43b37e559eb1d51a3d29a";
	String groupid = "5a98acfedc114e44041db40b";
	String token = "wFSDp2FjApmsN2GfOK6FB19zeDEJ8G2zet6rRthZwbKY6uOmXOtaPzPk0dDtJrPX";
	
	//List<User> userList = new ArrayList<User>();
	//userList.add(new User(userid, "username", "password", "First User",
	//		      "USA", "OR", "Portland", "first.user@email.com"));

	List<Group> groupList = new ArrayList<Group>();
	groupList.add(new Group(groupid, new ObjectId(userid), "", null));
	
	List<Session> sessionList = new ArrayList<Session>();
	sessionList.add(new Session(token, new ObjectId(userid), new Date()));

	//when(userRep.findByUsername("username")).thenReturn(userList);
	when(groupRep.findByIdAndOwnerid(new ObjectId(groupid), new ObjectId(userid))).thenReturn(groupList);
	when(request.getRequestURI()).thenReturn("/group/" + groupid);
	when(sessionRep.findByToken(token)).thenReturn(sessionList);

	// test
	Response resp = groupctrl.delete(token, request);
	assertEquals("ok", resp.getStatus());

	// test with null arguments
	resp = groupctrl.delete(null, null);
	assertEquals("notok", resp.getStatus());

	// test with user is not authenticated
	resp = groupctrl.delete("", request);
	assertEquals("notok", resp.getStatus());

	// test with group not found or user is not owner
	when(groupRep.findByIdAndOwnerid(new ObjectId(groupid), new ObjectId(userid))).thenReturn(new ArrayList<Group>());
	resp = groupctrl.delete(token, request);
	assertEquals("notok", resp.getStatus());

	// test with invalid request string
	when(request.getRequestURI()).thenReturn("/group/");
	resp = groupctrl.delete(token, request);
	assertEquals("notok", resp.getStatus());
    }   
}
