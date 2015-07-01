var express = require('express');
var router = express.Router();

function renderMainPage(req, res, auth) {
    req.db.get('trails').find( {}, {fields: { 'trailname':1 }}, function(err, doc) {
	if (err) {
	    doc = [];
	}
	res.render('main', { title: 'Just Another Trail Map', authenticated: auth, trails: doc });
    });
}

router.get('/logout', function(req, res) {
    res.clearCookie('username');
    res.clearCookie('password');
    renderMainPage(req, res, false);
});

router.get('/userinfo', function(req, res) {
    var user = req.cookies.username;
    var pass = req.cookies.password;

    if (user && pass) {
	req.db.get('users').find( { username: user }, {}, function(err, doc) {
	    if (err || doc == null) {
		throw err;
	    }
	    if (doc.length == 1 && doc[0].password == pass) {
		res.render('userinfo', { title: 'User infomation', info: doc[0]});
	    }
	    else {
		console.log("username and password don't match");
		renderMainPage(req, res, false);
	    }
	});
    }
    else {
	console.log("username or password didn't find from cookies");
	renderMainPage(req, res, false);
    }
});

router.get('/login', function(req, res) {
    var user = req.query.username;
    var pass = req.query.password;

    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 0 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 0) {
	    console.log("username wasn't found from database");
	    var msg = "Username " + user + " wasn't found from database"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	console.log(doc[0].password + " == " + pass);
	if (doc[0].password != req.query.password) {
	    console.log("wrong password");
	    var msg = "Password and username didn't match"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	res.cookie('username', user);
	res.cookie('password', pass);
	res.json({"status": "ok"}); 
    });
});

router.get('/newuser', function(req, res) {
    res.render('newuser', { title: 'New user', authenticated: false });
});

router.post('/adduser', function(req, res) {
    var user = req.body.username;
    var pass = req.body.password;

    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 0 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length != 0) {
	    console.log("username is already existed");
	    var msg = "Username " + user + " is already existed"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	req.db.get('users').insert(req.body, function(err, doc) {
	    if (err || doc == null) {
		throw err;
	    }
	    res.cookie('username', user);
	    res.cookie('password', pass);
	    res.json({"status": "ok"}); 
	});
    });
});

router.post('/updateuser', function(req, res) {
    var user = req.body.username;
    var pass = req.body.password;

    if (user != req.cookies.username) {
	console.log("cookies.username and req.body.username are different");
	console.log("user=" + user + " cookies.user=" + req.cookies.username);
	res.json({"status": "notok", "msg": "cookies.username and req.body.username are different"}); 
	return;
    }
    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 0 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 0) {
	    console.log("username wasn't found from database");
	    var msg = "Username " + user + " wasn't found from database"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	if (doc[0].password != pass) {
	    console.log("wrong password");
	    var msg = "Wrong password"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	var d = req.body;
	req.db.get('users').update( { "username": user },
			   { "$set": { "fullname": d.fullname, "country": d.country,
				       "state": d.state, "city": d.city } },
			   function(err, result) {
			       if (err) {
				   throw err;
			       }
			       res.cookie('password', d.password);
			       res.json({"status": "ok"}); 
			   });
    });
});

router.post('/updatepassword', function(req, res) {
    var user = req.cookies.username;
    var oldpass = req.body.oldpassword;
    var newpass = req.body.newpassword;

    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 0 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 0) {
	    console.log("username wasn't found from database");
	    var msg = "Username " + user + " wasn't found from database"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	if (doc[0].password != oldpass) {
	    console.log("wrong password");
	    var msg = "Wrong password"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	req.db.get('users').update( { "username": user },
			   { "$set": { "password": newpass } } , function(err, result) {
			       if (err) {
				   throw err;
			       }
			       res.cookie('password', newpass);
			       res.json({"status": "ok"}); 
			   });
    });
});

module.exports = router;
