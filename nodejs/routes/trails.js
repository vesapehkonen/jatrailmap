var express = require('express');
var router = express.Router();

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
	    msg = "Username wasn't found from database";
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	if (doc[0].password != pass) {
	    console.log("wrong password");
	    msg = "Wrong password"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	obj = { 'userid': doc[0]._id,
		'access': trailinfo.access,
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

router.get('/gettrail', function(req, res) {
    var locs = [];
    var pics = [];
    var info;
    
    req.db.get('trails').find( { _id: req.query.id }, function(err, doc) {
	if(err || doc === null) throw err;
	if (doc.length === 0) {
	    console.log("trail wasn't found");
	    res.json({"status": "notok", "msg": "Trail wasn't found from the db."}); 
	    return;
	}
	info = doc[0];
	req.db.get('locations').find( { trailid:  info._id }, function(err, doc) {
	    if(err || doc === null) throw err;
	    for (i=0; i<doc.length; i++) {
		locs[i] = {};
		locs[i].timestamp =  doc[i].timestamp;
		locs[i].loc =  doc[i].loc;
	    }
	    req.db.get('pictures').find( { trailid:  info._id }, function(err, doc) {
		if(err || doc === null) throw err;
		pics = doc;
		for (i=0; i<doc.length; i++) {
		    pics[i].imageid =  doc[i].imageid;
		    pics[i].timestamp =  doc[i].timestamp;
		    pics[i].filename =  doc[i].filename;
		    pics[i].picturename =  doc[i].picturename;
		    pics[i].description =  doc[i].description;
		    pics[i].loc =  doc[i].loc;
		}
		var respdata = { 'GetTrailResponse': [
		    { 'type': 'TrailInfo',
		      'date': info.date,
		      'trailname': info.trailname,
		      'locationname': info.location,
		      'description': info.description
		    },
		    { 'type': 'LocationCollection',
		      'locations': locs 
		    },
		    { 'type': 'PictureCollection',
		      'pictures': pics 
		    }
		]};
		res.json({"status": "ok", "data": respdata}); 
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
    var date = new Date(dateStr);
    return  (date.getMonth() + 1) + '/' + date.getDate() + '/' + date.getFullYear();
}

router.get('/trails/*', function(req, res) {
    var info;
    var parts = req.url.split("/");
    
    if (parts.length == 3) {
	var id = parts[2];
	req.db.get('trails').find( { _id: id }, function(err, doc) {
	    if (err || doc === null) {
		throw err;
	    }
	    if (doc.length === 0) {
		console.log("trail wasn't found");
		res.status(404);
		res.send("The trail wasn't found");
		return;
	    }
	    info = doc[0];
	    info.date = convertDate(doc[0].date);

	    req.db.get('locations').find(
		{ trailid:  info._id }, { fields: {'loc.coordinates': 1, 'timestamp': 1, _id: 0 } },  function(err, doc) {
		    if(err || doc === null) throw err;
		    info.distance = req.geo.distance(doc);
		    if (doc.length > 0) {
			info.time = elapsedTime(doc[0].timestamp, doc[doc.length-1].timestamp);
		    }
		    req.db.get('users').find(
			{ _id: info.userid }, { fields: {username: 1, fullname: 1, _id: 0 } },
			function(err, doc) {
			    if (err || doc === null) {
				throw err;
			    }
			    if (doc.length === 0) {
				info.user = 'Unknown';
			    }
			    else if (doc[0].fullname !== '') {
				info.user = doc[0].fullname;
			    }
			    else {
				info.user = doc[0].username;
			    }
			    res.render('trail', { 'info': info });
			});
		});
	
	});
    }
});

router.get('/images/*', function(req, res) {
    var user= 'sdfdsf';
    var parts = req.url.split("/");

    if (parts.length == 3) {
	var id = parts[2];
	var db = req.db;
	req.db.get('images').find( { _id: id }, { fields: { img: 1, _id: 0 } }, function(err, doc) {
	    if (err || doc === null)
		throw err;
	    if (doc.length === 0) {
		console.log("image wasn't found ", id);
		res.status(404);
		res.send("Image wasn't found from the database.");
		return;
	    }
	    res.writeHead(200, {'Content-Type': 'image/gif' });
	    var bin = new Buffer(doc[0].img, 'base64').toString('binary');
	    res.end(bin, 'binary');
	});
    }
});

module.exports = router;
