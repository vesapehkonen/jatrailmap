var bcrypt = require('bcrypt');
var express = require('express');
var router = express.Router();
var ObjectId = require('mongodb').ObjectID;

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
	    //console.log("session token '" + token + "' not found from db");
	    callback(req, res, "Session is expired", null);
	}
    });
}

function verifyTrailOwner(req, trailid, userid, callback) {
    req.db.get('trails').find( { "_id": trailid, 'userid': userid }, { fields: { _id: 1 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 1) {
	    callback(true, null);
	}
	else {
	    console.log("User '" + userid + "' is not owner of this trail '" + trailid + "'");
	    callback(false, "User is not owner of this trail");
	}
    });
}

function verifyTrailAccess(req, trail, userid, callback) {
    // if the user is owner of the trail
    if (userid && trail.userid.equals(userid)) {
	callback(true);
    }
    // if the user is member of group, that is listed in the trail
    else if (userid && trail.access == 'group') {
	var ids = [];
	if (trail.groups) {
	    ids = trail.groups;
	}
	req.db.get('groups').find( { _id: { '$in': ids } }, function(err, groups) {
	    if (err || groups === null) {
		throw err;
	    }
	    for (var i=0; i<groups.length; i++) {
		var members = groups[i].members;
		for (var j=0; j<members.length; j++) {
		    if (userid.equals(members[i])) {
			callback(true);
			return;
		    }
		}
	    }
	    callback(false);
	});
    }
    // if trail has public access
    else if (trail.access == 'public') {
	callback(true);
    }
    // all other cases, e.g: the user is not authenticated and the trail is not public
    //    or the trail is private and the user is not owner
    else {
	callback(false);
    }
}

function verifyImageAccess(req, imgid, userid, callback) {
    req.db.get('pictures').find( { 'imageid': ObjectId(imgid) }, function(err, pics) {
	if (err || pics === null) {
	    throw err;
	}
	if (pics.length != 1) {
	    callback(false);
	    return;
	}
	var pic = pics[0];

	req.db.get('trails').find( { _id: pic.trailid },  function(err, trails) {
	    if (err || trails === null) {
		throw err;
	    }
	    if (trails.length != 1) {
		callback(false);
		return;
	    }
	    verifyTrailAccess(req, trails[0], userid, function(result) {
		if (!result) {
		    callback(false);
		    return;
		}
		console.log("userid" + userid);
		// if the user is owner of the trail
		if (userid && trails[0].userid.toString() == userid) {
		    callback(true);
		}
		// if the user is member of group, that is listed in the picture
		else if (userid && pic.access == 'group') {
		    var ids = [];
		    if (pic.groups) {
			ids = pic.groups;
		    }
		    req.db.get('groups').find( { _id: { '$in': ids } }, function(err, groups) {
			if (err || groups === null) {
			    throw err;
			}
			for (var i=0; i<groups.length; i++) {
			    var members = groups[i].members;
			    for (var j=0; j<members.length; j++) {
				if (userid.equals(members[i])) {
				    callback(true);
				    return;
				}
			    }
			}
			callback(false);
		    });
		}
		// if picture has public access
		else if (pic.access == 'public') {
		    callback(true);
		}
		else if (pic.access == null) {
		    callback(true);
		}
		// all other cases, e.g: the user is not authenticated and the picture is not public
		//    or the picture is private and the user is not owner
		else {
		    callback(false);
		}
	    });
	});
    });
}

function checkSizeofImage(img) {
    var buf = new Buffer(img, 'base64');
    if (buf.length > 1024 * 1024 * 16) {
	msg = "Maximun size of picture is 16 MB. " +
	    "There was a picture with size " + img.length + " bytes"; 
	res.json({"status": "notok", "msg": msg}); 
	colose.log(msg);
	return false;
    }
    return;
}

function insertPicture(data, i) {
    if (i < data.pics.length) {
	if (checkSizeofImage(data.pics[0].file) === false) {
	    return;
	}
	// insert base64 encoded image
	var img = { 'img': data.pics[i].file };
	data.dbimgs.insert(img, function(err, doc) {
	    if (err) throw err;
	    var obj = { 'trailid': data.trailid,
			'imageid': doc._id,
			'timestamp': data.pics[i].timestamp,
			'filename': data.pics[i].filename,
			'picturename': data.pics[i].picturename,
			'description': data.pics[i].description,
			'loc': data.pics[i].loc
		      };
	    data.dbpics.insert(obj, function(err, doc) {
		if (err) throw err;
		insertPicture(data, i + 1);
	    });
	});
    }
    else {
	var msg = "The new trail was successfully added to db."; 
	data.res.json({"status": "ok", "msg": msg, "trailid": data.trailid }); 
    }
}

function insertLocation(data, i) {
    if (i < data.locs.length) {
	var obj = { 'trailid': data.trailid,
		    'timestamp': data.locs[i].timestamp,
		    'loc': data.locs[i].loc
		  };
	data.dblocs.insert(obj, function(err, doc) {
	    if (err) throw err;
	    insertLocation(data, i + 1);
	});
    }
    else {
	insertPicture(data, 0);
    }
}

router.post('/addtrail', function(req, res) {
    var user = '';
    var pass = '';
    var pics = [];
    var locs = [];
    var trailinfo = {};
    var array = [];
    array = req.body.newtrail;
    
    for (var i=0; i<array.length; i++) {
	if (array[i].type == 'UserInfo') {
	    user = array[i].username;
	    pass = array[i].password;
	}
	else if (array[i].type == 'TrailInfo') {
	    trailinfo = array[i];
	}
	else if (array[i].type == 'LocationCollection') {
	    if (array[i].locations) {
		locs = array[i].locations;
	    }
	}
	else if (array[i].type == 'PictureCollection') {
	    if (array[i].pictures) {
		pics = array[i].pictures;
	    }
	}
    }

    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 1 } }, function(err, doc) {
	if (doc.length === 0) {
	    console.log("username wasn't found");
	    res.json({"status": "notok", "msg": "Wrong username or password."}); 
            return;
	}
	var hash = doc[0].password;
	bcrypt.compare(pass, hash, function(err, result) {
	    if (err) {
		throw err;
	    }
	    if (!result) {
		console.log("wrong password");
		res.json({"status": "notok", "msg": "Wrong username or password."}); 
		return;
	    }
	    obj = { 'userid': doc[0]._id,
		    //'access': trailinfo.access,
		    'access': 'private',
		    'date': trailinfo.date,
		    'trailname': trailinfo.trailname,
		    'location': trailinfo.locationname,
		    'description': trailinfo.description
		  };
	    req.db.get('trails').insert(obj, function(err, doc) {
		insertLocation( { 'dblocs' : req.db.get('locations'),
				  'dbpics' : req.db.get('pictures'),
				  'dbimgs' : req.db.get('images'),
				  'locs'   : locs,
				  'pics'   : pics,
				  'trailid': doc._id,
				  'res'    : res }, 0);
	    });
	});
    });
});

router.get('/trail/:id/track', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	userid = userid ? userid : "";
	var trailid = ObjectId(req.params.id);

	req.db.get('locations').find( { 'trailid':  ObjectId(trailid) }, function(err, locs) {
	    if (err || locs === null) {
		throw err;
	    }
	    for (i=0; i<locs.length; i++) {
		locs[i].id =  locs[i]._id;
	    }
	    locs.sort(function(a, b) { return (new Date(a.timestamp)) - (new Date(b.timestamp))});	    

	    // find all groups where the user is a member
	    req.db.get('groups').find( {members: userid}, {fields: {_id:1}}, function(err, groups) {
		if (err || groups == null) {
		    throw err;
		}
		var ids = [];
		for (var i=0; i<groups.length; i++) {
		    ids[i] = ObjectId(groups[i]._id);
		}
		verifyTrailOwner(req, trailid, userid, function(owner, err) {
		    var query;
		    if (owner) {
			// all pictures of this trail
			query = { 'trailid': trailid };
		    }
		    else {
			// all pictures of this trail, where access is public, access not exists,
			//   or access is group and user belong to the group 
			query = { $and: [ {'trailid': trailid}, { $or: [ {'groups': {$in: ids}, 'access': 'group'},
				{'access': 'public'}, { 'access' : { $exists: false } } ] } ] };

		    }
		    req.db.get('pictures').find( query, function(err, pics) {
			if (err || pics === null) {
			    throw err;
			}
			console.log("pics.length=" + pics.length);
			console.log("trailid=" + trailid);
			//console.log("pics.length=" + pics.length);
			for (i=0; i<pics.length; i++) {
			    pics[i].id =  pics[i]._id;
			}
			res.json({ 'status': 'ok', 'locs': locs, 'pics': pics});
		    });
		});
	    });
	});
    });
});

function elapsedTime(time1, time2) {
    var date1 = new Date(time1);
    var date2 = new Date(time2);
    var msecs = date2.getTime() - date1.getTime();
    var secs = msecs / 1000;
    var hrs = Math.floor(secs / 3600);
    var mins = Math.floor((secs % 3600) / 60);
    return hrs + 'h ' + mins + ' min';
}

function convertDate(dateStr) {
    if (dateStr) {
	var date = new Date(dateStr);
	return  (date.getMonth() + 1) + '/' + date.getDate() + '/' + date.getFullYear();
    }
    return null;
}

router.get('/trail/:id', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	var trailid = req.params.id;
	req.db.get('trails').find( { _id: trailid },  function(err, trails) {
	    if (err || trails === null) {
		throw err;
	    }
	    if (trails.length === 0) {
		console.log("trail wasn't found");
		res.status(404);
		res.send("The trail wasn't found");
		return;
	    }
	    var trail = trails[0];
	    verifyTrailAccess(req, trail, userid, function(result) {
		if (!result) {
		    console.log("User does not have permission to look at this trail");
		    res.status(401);
		    res.send("User does not have permission to look at this trail");
		    return;
		}
		if (userid && userid.equals(trail.userid)) {
		    trail.owner = true;
		}
		else {
		    trail.owner = false;
		}
		trail.date = convertDate(trail.date);
	    
		req.db.get('locations').find({ trailid:  trail._id }, { fields: {'loc.coordinates': 1, 'timestamp': 1, _id: 0 } },
		function(err, locs) {
		    if (err || locs === null) {
			throw err;
		    }
		    locs.sort(function(a, b) { return (new Date(a.timestamp)) - (new Date(b.timestamp))});	    
		    trail.distance = req.geo.distance(locs);
		    if (locs.length > 0) {
			trail.time = elapsedTime(locs[0].timestamp, locs[locs.length-1].timestamp);
		    }
		    req.db.get('users').find({ _id: trail.userid }, { fields: {username: 1, fullname: 1, _id: 0 } },
		    function(err, users) {
			if (err || users === null) {
			    throw err;
			}
			trail.user = 'unknown';
			if (users.length == 1) {
			    if (users[0].fullname !== '') {
				trail.user = users[0].fullname;
			    }
			    else {
				trail.user = users[0].username;
			    }
			}
			if (!trail.groups) {
			    trail.groups = [];
			}
			req.db.get('groups').find( { _id: {'$in': trail.groups} }, {fields: {'name':1} }, function(err, groups) {
			    if (err || groups === null) {
				throw err;
			    }
			    res.render('trail', { 'info': trail, 'groups': groups });
			});
		    });
		});
	    });
	});
    });
});

router.get('/trail/:id/edit', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err) {
	    console.log(err);
	    res.status(401);
	    res.send(err);
	    return;
	}
	var trailid = req.params.id;
	req.db.get('trails').find( { _id: trailid }, function(err, trails) {
	    if (err || trails === null) {
		throw err;
	    }
	    if (trails.length === 0) {
		console.log("trail wasn't found");
		res.status(404);
		res.send("The trail wasn't found");
		return;
	    }
	    trail = trails[0];
	    if (!userid.equals(trail.userid)) {
		console.log("The user is not owner of this trail");
		res.status(401);
		res.send("The user is not owner of this trail");
		return;
	    }
	    trail.date = convertDate(trail.date);

	    req.db.get('locations').find({ 'trailid':  trail._id }, { fields: {'loc.coordinates': 1, 'timestamp': 1, _id: 0 } },
            function(err, locs) {
		if (err || locs === null) {
		    throw err;
		}
		trail.distance = req.geo.distance(locs);
		if (locs.length > 0) {
		    trail.time = elapsedTime(locs[0].timestamp, locs[locs.length-1].timestamp);
		}
		res.render('edittrail', { 'info': trail });
	    });
	});
    });
});

router.delete('/trail/:id', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err) {
	    console.log(err);
	    res.json({"status": "notok", "msg": err}); 
	    return;
	}
	var trailid = req.params.id;
	verifyTrailOwner(req, trailid, userid, function(result, err) {
	    if (!result) { 
		res.json({"status": "notok", "msg": err}); 
		return;
	    }
	    req.db.get('pictures').find( { trailid: trailid }, { fields: { imageid: 1 } }, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		for (var i=0; i<doc.length; i++) {
		    req.db.get('images').remove( { _id: doc[i].imageid }, function(err, result) { if (err) { throw err; } } );
		}
		req.db.get('pictures').remove( { trailid: trailid }, function(err, result) { if (err) { throw err; } } );
	    });
	    req.db.get('locations').remove( { 'trailid': trailid }, function(err, result) { if (err) { throw err; } } );
	    req.db.get('trails').remove( { "_id": trailid }, function(err, result) { if (err) { throw err; } } );
	    res.json({"status": "ok"});
	});
    });
});

router.get('/image/:id', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	var imgid = req.params.id;
	req.db.get('images').find( { _id: imgid }, { fields: { img: 1, _id: 0 } }, function(err, images) {
	    if (err || images === null)
		throw err;
	    if (images.length === 0) {
		console.log("image wasn't found ", id);
		res.status(404);
		res.send("Image wasn't found from the database.");
		return;
	    }
	    var img = images[0];
	    verifyImageAccess(req, imgid, userid, function(result) {
		if (!result) {
		    console.log("User does not have permission to get this image");
		    res.status(401);
		    res.send("User does not have permission to get this image");
		    return;
		}
		res.writeHead(200, {'Content-Type': 'image/gif' });
		var bin = new Buffer(img.img, 'base64').toString('binary');
		res.end(bin, 'binary');
	    });
	});
    });
});

router.put('/trail/:id', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err) {
	    console.log(err);
	    res.json({"status": "notok", "msg": err}); 
	    return;
	}
	var trailid = req.params.id;
	verifyTrailOwner(req, trailid, userid, function(result, err) {
	    if (!result) { 
		res.json({"status": "notok", "msg": err}); 
		return;
	    }
	    var d = req.body;
	    req.db.get('trails').update( { "_id": d.id },
		{ "$set": { "trailname": d.trailname, "location": d.location, "description": d.description } },
		function(err, result) {
		    if (err) { throw err; }
		});
	    res.json({"status": "ok"}); 
	    updatePathData(req, d.updates);
	});
    });
});

function updatePathData(req, data) { 
    if (typeof data == "undefined") {
	return;
    }
    for (var i=0; i<data.length; i++) {
	console.log(data[i].action);
	var item = data[i];
	var act = item.action;
	if (act == "updateLocation") {
	    req.db.get('locations').update( { "_id": item.id },
		{ "$set": { "loc.coordinates.1": item.lat, "loc.coordinates.0": item.lng } },
		function(err, result) { if (err) { throw err; }});
	}
	else if (act == "updatePictureLocation") {
	    req.db.get('pictures').update( { "_id": item.id },
		{ "$set": { "loc.coordinates.1": item.lat, "loc.coordinates.0": item.lng } },
		function(err, result) { if (err) { throw err; }});
	}
	else if (act == "updatePicturename") {
	    console.log("picture.id:" + item.id);
	    console.log("picture.name:" + item.name);
	    req.db.get('pictures').update( { "_id": item.id }, { "$set": { "picturename": item.name } },
		function(err, result) { if (err) { throw err; }});
	}
	else if (act == "removeLocation") {
	    req.db.get('locations').remove( { "_id": item.id }, function(err, result) { if (err) { throw err; } });
	}
	else if (act == "removePicture") {
	    req.db.get('pictures').find( { "_id": item.id }, { fields: {imageid: 1, _id: 0 } }, (function(item) {
		return function(err, doc) {
		    if (err || doc == null) {
			throw err;
		    }
		    if (doc.length == 0) {
			console.log("Find error: Picture doc" + item.id + " wasn't found from database");
			return;
		    }
		    req.db.get('images').remove( { "_id": doc[0].imageid }, function(err, result) { if (err) { throw err; }});
		    req.db.get('pictures').remove( { "_id": item.id }, function(err, result) { if (err) { throw err; }});
		}
	    })(item));
	}
    }
}

router.get('/trail/:id/permissions', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err) {
	    console.log(err);
	    res.json({"status": "notok", "msg": err}); 
	    return;
	}
	var trailid = req.params.id;
	verifyTrailOwner(req, trailid, userid, function(result, err) {
	    if (!result) { 
		res.json({"status": "notok", "msg": err}); 
		return;
	    }
	    req.db.get('trails').find( { _id: trailid }, {fields: {access: 1, groups: 1, trailname: 1} },
            function(err, trails) {
		if (err || trails === null) {
		    throw err;
		}
		if (trails.length === 0) {
		    console.log("trail wasn't found");
		    res.status(404);
		    res.send("The trail wasn't found");
		    return;
		}
		var trail = trails[0];
		if (trail.groups == undefined) {
		    trail.groups = [];
		}
		req.db.get('groups').find( {'ownerid': userid}, {fields: {'name':1} },
                function(err, groups) {
		    if (err || groups == null) {
			throw err;
		    }
		    for (var i=0; i<groups.length; i++) {
			groups[i].checked = false;
			for (var j=0; j<trail.groups.length; j++) {
			    if (groups[i]._id.equals(trail.groups[j])) {
				groups[i].checked = true;
				break;
			    }
			}
		    }
		    var access = trail.access;
		    var trailname = trail.trailname;
		    res.render('permissions', { title: 'Edit permissions of the trail',
			trailid: trailid, trailname: trailname, access: access, groups: groups });
		});
	    });
	});
    });
});

router.put('/trail/:id/permissions', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err) {
	    console.log(err);
	    res.json({"status": "notok", "msg": err}); 
	    return;
	}
	var trailid = req.params.id;
	verifyTrailOwner(req, trailid, userid, function(result, err) {
	    if (!result) { 
		res.json({"status": "notok", "msg": err}); 
		return;
	    }
	    var access = req.body.access;
	    var groups = req.body.groups;
	    if (groups) {
		for (var i=0; i<groups.length; i++) {
		    groups[i] = ObjectId(groups[i]);
		}
	    }
	    req.db.get('trails').update( { "_id": trailid }, { "$set": { "access": access, "groups": groups } },
            function(err, result) {
		if (err) {
		    throw err;
		}
		if (result.nModified == 1) {
		    res.json({"status": "ok"});
		}
		else {
		    console.log("The permissions weren't updated.");
		    res.json({"status": "notok", "msg": "The permissions weren't updated."}); 
		}
	    });
	});
    });
});

router.put('/trail/:trailid/picture/:picid/permissions', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	if (err) {
	    console.log(err);
	    res.json({"status": "notok", "msg": err}); 
	    return;
	}
	var trailid = req.params.trailid;
	var picid = req.params.picid;
	verifyTrailOwner(req, trailid, userid, function(result, err) {
	    if (!result) { 
		res.json({"status": "notok", "msg": err}); 
		return;
	    }
	    req.db.get('pictures').find( { "_id": picid }, { fields: {_id: 1, trailid: 1} },
            function(err, pics) {
		if (err) {
		    throw err;
		}
		if (pics.length != 1 || trailid != pics[0].trailid.toString()) {
		    console.log("The picture is not belong to this trail");
		    res.json({"status": "notok", "msg": "The picture is not belong to this trail"}); 
		    return;
		}
		var access = req.body.access;
		var groups = req.body.groups;
		if (groups) {
		    for (var i=0; i<groups.length; i++) {
			groups[i] = ObjectId(groups[i]);
		    }
		}
		req.db.get('pictures').update( { "_id": picid }, { "$set": { "access": access, "groups": groups } },
		function(err, result) {
		    if (err) {
			throw err;
		    }
		    if (result.nModified == 1) {
			res.json({"status": "ok"});
		    }
		    else {
			console.log("The picture permissions weren't updated.");
			res.json({"status": "notok", "msg": "The picture permissions weren't updated."}); 
		    }
		});
	    });
	});
    });
});

module.exports = router;
