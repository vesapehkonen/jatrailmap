var express = require('express');
var router = express.Router();

function renderMainPage(req, res) {
    req.db.get('trails').find( {access: 'public'}, {fields: { 'trailname':1, 'location':1 }},
        function(err, doc) {
	if (err || doc == null) { throw err; }
	    res.render('main', { title: 'Just Another Trail Map', authenticated: false, trails: doc });
	});
}

function checkuser(req, res, callback) {
    var user = req.cookies.username;
    var pass = req.cookies.password;

    if (user == undefined) {
	console.log("ERROR: Username didn't find from cookies");
	callback(req, res, "ERROR Username didn't find from cookies");
	return;
    }
    if (pass == undefined) {
	console.log("ERROR: Password didn't find from cookies");
	callback(req, res, "ERROR: Password didn't find from cookies");
	return;
    }
    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 1 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 0) {
	    console.log("username wasn't found from database");
	    callback(req, res, "Username " + user + " wasn't found from database"); 
	    return;
	}
	if (doc[0].password != pass) {
	    console.log("wrong password");
	    callback(req, res, "Wrong password");
	    return;
	}
	callback(req, res, null, doc[0]._id);
    });
}

router.get('/groups', function(req, res) {
    checkuser(req, res, function(req, res, err, userid) {
	if (err == null) {
	    req.db.get('groups').find( {'ownerid': {$eq : userid}}, {fields: { 'name':1}}, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		res.render('groups', { title: 'Manage Groups', groups: doc });
	    });
	}
	else {
	    console.log("username and password don't match");
	    renderMainPage(req, res);
	}
    });
});

router.get('/newgroup', function(req, res) {
    checkuser(req, res, function(req, res, err, userid) {
	if (err == null) {
	    req.db.get('users').find( {'_id': {$ne : userid}}, {fields: { 'fullname':1}}, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		res.render('newgroup', { title: 'Add New Group', users: doc });
	    });
	}
	else {
	    renderMainPage(req, res);
	}
    });
});

router.post('/addgroup', function(req, res) {
    var name = req.body.groupname;
    var users = req.body.users;
    checkuser(req, res, function(req, res, err, userid) {
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
    checkuser(req, res, function(req, res, err, userid) {
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
		var ids = [];
		for (var i=0; i<doc[0].members.length; i++) {
		    ids[i] = { '_id':doc[0].members[i] };
		}
		req.db.get('users').find( { '$or': ids }, {fields: {'fullname':1} }, function(err, doc) {
		    if (err || doc == null) {
			throw err;
		    }
		    var members = [];
		    if (doc) {
			members = doc
		    }

		    // do a query for getting all non member ids
		    var ids = [];
		    for (var i=0; i<members.length; i++) {
			ids[i] = { '_id':members[i]._id };
		    }
		    ids[i] = { '_id':userid };
		    req.db.get('users').find( { '$nor': ids }, {fields: {'fullname':1} }, function(err, doc) {
			if (err || doc == null) {
			    throw err;
			}
			var nonmembers = [];
			if (doc) {
			    nonmembers = doc
			}
			res.render('editgroup', { title: 'Add New Group', groupid:groupid, groupname: groupname, members: members, nonmembers: nonmembers });
		    });
		});
	    });
	}
    });
});

router.post('/updategroup', function(req, res) {
    checkuser(req, res, function(req, res, err, userid) {
	var groupname = req.body.groupname;
	var members = req.body.members;
	var groupid = req.body.groupid;
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
    checkuser(req, res, function(req, res, err, userid) {
	if (err == null) {
	    var parts = req.url.split("/");
	    
	    if (parts.length == 3) {
		var id = parts[2];
		console.log('id', id);
		console.log('userid', userid);
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
