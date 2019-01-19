var express = require('express');
var router = express.Router();

function convertDate(dateStr) {
    if (dateStr) {
	var date = new Date(dateStr);
	return  (date.getMonth() + 1) + '/' + date.getDate() + '/' + date.getFullYear();
    }
    return null;
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
	    //console.log("session token '" + token + "' not found from db");
	    callback(req, res, "Session is expired", null);
	}
    });
}

router.get('/', function(req, res) {
    authenticateUser(req, res, function(req, res, err, userid) {
	var auth = true;
	if (err) {
	    auth = false;
	    userid = "";
	}

	// find all groups where the user is a member
	req.db.get('groups').find( {members: userid}, {fields: {_id:1}}, function(err, groups) {
	    if (err || groups == null) {
		throw err;
	    }
	    var ids = [];
	    for (var i=0; i<groups.length; i++) {
		ids[i] = groups[i]._id;
	    }

	    // find all trails where
	    //   - the user is owner,
	    //   - where access is public or
	    //   - where the user is in the group, that is in the trail 

	    req.db.get('trails').find( { $or: [ {'groups': {$in: ids}, 'access': 'group'},
						{'access': 'public'}, {'userid': userid} ] }, {fields: { 'trailname':1, 'location':1, 'date':1 }},
	      function(err, trails) {
		if (err || trails == null) {
		    throw err;
		}
		  trails.sort(function(a, b) {
		      if (a.date && b.date) { 
			  return new Date(b.date) - new Date(a.date);
		      }
		      else if (a.date && !b.date) {
			  return -1;
		      }
		      else if (b.date && !a.date) { 
			  return 1;
		      }
		      return 0;
		  });	    
		  for (var i=0; i<trails.length; i++) {
		      trails[i].date = convertDate(trails[i].date);
		  }
		  res.render('main', { title: 'Just Another Trail Map', authenticated: auth, trails: trails });
	    });
	});
    });
});

router.get('/trailbuilder', function(req, res) {
    res.render('trailbuilder');
});

module.exports = router;
