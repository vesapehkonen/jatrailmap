var express = require('express');
var router = express.Router();

function renderMainPage(req, res, auth) {
    req.db.get('trails').find( {}, {fields: { 'trailname':1, 'location':1 }}, function(err, doc) {
	if (err) {
	    doc = [];
	}
	res.render('main', { title: 'Just Another Trail Map', authenticated: auth, trails: doc });
    });
}

router.get('/', function(req, res) {
    var user = req.cookies.username;
    var pass = req.cookies.password;
    var auth = false;

    if (user && pass) {
	req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 0 } }, function(err, doc) {
	    if (err || doc == null) {
		throw err;
	    }
	    if (doc.length == 1 && doc[0].password == pass) {
		auth = true;	    
	    }
	    renderMainPage(req, res, auth);
	});
    }
    else {
	renderMainPage(req, res, auth);
    }
});


router.get('/trailbuilder', function(req, res) {
    res.render('trailbuilder');
});

module.exports = router;
