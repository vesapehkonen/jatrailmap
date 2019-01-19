var express = require('express');
var router = express.Router();
var ObjectId = require('mongodb').ObjectID;

function renderMainPage(req, res) {
    req.db.get('trails').find( {access: 'public'}, {fields: { 'trailname':1, 'location':1 }},
        function(err, doc) {
	if (err || doc == null) { throw err; }
	    res.render('main', { title: 'Just Another Trail Map', authenticated: false, trails: doc });
	});
}

function authenticateUser(req, res, callback) {
    var token = req.cookies.token;
    
    req.db.get('sessions').find( { 'token': token }, { fields: {'userid': 1} }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 1) {
	    callback(req, res, null, doc[0].userid);
	}
	else {
	    console.log("session token '" + token + "' not found from db");
	    callback(req, res, "Session is expired");
	}
    });
}

router.get('/groups', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err == null) {
	    req.db.get('groups').find( {'ownerid': {$eq : userid}}, {fields: { 'name':1}}, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		res.render('groups', { title: 'Manage Groups', groups: doc });
	    });
	}
	else {
	    renderMainPage(req, res);
	}
    });
});

router.get('/newgroup', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err == null) {
	    req.db.get('users').find( {'_id': {$ne : userid}}, {fields: { 'fullname':1, 'username':1}}, function(err, users) {
		if (err || users == null) {
		    throw err;
		}
		for (var i=0; i<users.length; i++) {
		    if (users[i].fullname == "") {
			users[i].fullname = users[i].username;
		    }
		}
		res.render('newgroup', { title: 'Add New Group', users: users });
	    });
	}
	else {
	    renderMainPage(req, res);
	}
    });
});

router.post('/addgroup', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	var name = req.body.groupname;
	var users = req.body.users;
	if (err == null) {
	    req.db.get('groups').insert({'ownerid': userid, 'members': users, 'name': name}, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		res.json({"status": "ok"}); 
	    });
	}
	else {
	    res.json({"status": "notok", "msg": err}); 
	}
    });
});

router.get('/editgroup/*', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err == null) {
	    var parts = req.url.split("/");
	    if (parts.length != 3) {
		return;
	    }
	    var groupid = parts[2];
	    req.db.get('groups').find( {'_id': groupid, 'ownerid': userid}, {fields: { 'members':1, 'name':1 } }, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		if (doc.length == 0) {
		    console.log("group wasn't found");
		    res.status(404);
		    res.send("The group wasn't found");
		    return;
		}
		var groupname = doc[0].name;
		var ids = doc[0].members;

		// get all member ids
		req.db.get('users').find( { _id: {'$in': ids} }, {fields: {'fullname':1, 'username': 1} }, function(err, members) {
		    if (err || doc == null) {
			throw err;
		    }
		    // get all non member ids
		    ids.push(userid);
		    req.db.get('users').find( { '$nor': [ {_id: { '$in':ids } } ] }, {fields: {'fullname':1, 'username': 1} }, function(err, nonmembers) {
			if (err || doc == null) {
			    throw err;
			}
			// if full name is not available, use username
			for (var i=0; i<members.length; i++) {
			    if (members[i].fullname == "") {
				members[i].fullname = members[i].username;
			    }
			}
			for (var i=0; i<nonmembers.length; i++) {
			    if (nonmembers[i].fullname == "") {
				nonmembers[i].fullname = nonmembers[i].username;
			    }
			}
			res.render('editgroup', { title: 'Edit Group', groupid:groupid, groupname: groupname, members: members, nonmembers: nonmembers });
		    });
		});
	    });
	}
	else {
	    renderMainPage(req, res);
	}
    });
});

router.post('/updategroup', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	var groupname = req.body.groupname;
	var groupid = req.body.groupid;
	var members = [];

	if (req.body.members) {
	    for (var i=0; i<req.body.members.length; i++) {
		members[i] = ObjectId(req.body.members[i]);
	    }
	}
	if (err == null) {
	    req.db.get('groups').update( { '_id': groupid, 'ownerid':userid },
	     { "$set": { "name": groupname, "members": members } },
	      function(err, result) {
		if (err) { throw err; }
		if (result.nModified == 1) {
		    res.json({"status": "ok"});
		}
		else {
		    res.json({"status": "notok", "msg": "The group wasn't updated."}); 
		}
	      });
	}
	else {
	    res.json({"status": "notok", "msg": err}); 
	}
    });
});

router.delete('/group/*', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err == null) {
	    var parts = req.url.split("/");
	    
	    if (parts.length == 3) {
		var id = parts[2];
		req.db.get('groups').remove( { "_id": id, 'ownerid': userid }, function(err, result) {
		    if (err) {
			throw err;
		    }
		    if (result.result.n == 1) {
			res.json({"status": "ok"});
		    }
		    else {
			res.json({"status": "notok", "msg": "The group " + id + " was not deleted"}); 
		    }
		});
	    }
	    else {
		res.json({"status": "notok", "msg": "parse error"}); 
	    }
	}
	else {
	    res.json({"status": "notok", "msg": err}); 
	}
    });
});

module.exports = router;
