var express = require('express');
var router = express.Router();

function renderMainPage(req, res, auth, userid) {
    req.db.get('groups').find( {members: { $all: [userid.toString()]}}, {fields: {_id:1}}, function(err, doc) {
	if (err || doc == null) { throw err; }
	var groupids = [];
	groupids[0] = '0';
	for (var i=0; i<doc.length; i++) {
	    groupids[i] = doc[i]._id.toString();
	}
	req.db.get('trails').find( { $or: [ {groups: {$in: groupids}, access: 'group'},
            {access: 'public'}, {userid: userid} ] }, {fields: { 'trailname':1, 'location':1 }},
	    function(err, doc) {
		if (err || doc == null) { throw err; }
		res.render('main', { title: 'Just Another Trail Map', authenticated: auth, trails: doc });
	    });
    });
}

router.get('/', function(req, res) {
    var user = req.cookies.username;
    var pass = req.cookies.password;
    var auth = false;

    if (user && pass) {
	//console.log("user and pass ok");
	req.db.get('users').find( { username: user }, { fields: {password: 1} }, function(err, doc) {
	    if (err || doc == null) {
		throw err;
	    }
	    if (doc.length == 1 && doc[0].password == pass) {
		auth = true;	    
	    }
	    renderMainPage(req, res, auth, doc[0]._id);
	});
    }
    else {
	renderMainPage(req, res, auth, 0);
    }
});


router.get('/trailbuilder', function(req, res) {
    res.render('trailbuilder');
});

module.exports = router;
