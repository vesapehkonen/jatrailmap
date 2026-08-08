var express = require('express');
var router = express.Router();
const bcrypt = require('bcrypt');

function convertDate(dateStr) {
    if (dateStr) {
	var date = new Date(dateStr);
	return  (date.getMonth() + 1) + '/' + date.getDate() + '/' + date.getFullYear();
    }
    return null;
}

function randomString() {
    var count = 64;
    var chars = "abcdefghijklmopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    var len = chars.length;
    var arr = [];
    for (var i=0; i<count; i++) {
	var ch = chars[Math.floor(Math.random() * len)];
	arr.push(ch);
    }
    return arr.join("");
}

function renderMainPage(req, res) {
    req.db.get('trails').find( {access: 'public'}, {fields: { 'trailname':1, 'location':1, date:1 }},
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
	res.render('main', { title: 'Just Another Trail Map', authenticated: false, trails: trails });
    });
}

router.get('/logout', function(req, res) {
    var token = req.cookies.token;
    req.db.get('sessions').remove( { 'token': token }, function(err, doc) {
	if (err || doc == null) { throw err; }
	res.clearCookie('token');
	renderMainPage(req, res);
    });
});

router.get('/userinfo', function(req, res) {
    var token = req.cookies.token;
    req.db.get('sessions').find( { 'token': token }, {}, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 1) {
	    var userid = doc[0].userid;
	    req.db.get('users').find( { _id: userid }, {}, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		res.render('userinfo', { title: 'User infomation', info: doc[0]});
	    });
	}
	else {
	    console.log("user not logged in or session is expired");
	    renderMainPage(req, res);
	}
    });
});

router.get('/login', function(req, res) {
    var user = req.query.username;
    var pass = req.query.password;

    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 1 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 0) {
	    console.log("username wasn't found from database");
	    var msg = 
	    res.json({"status": "notok", "msg": "Wrong username or password"}); 
            return;
	}
	var hash = doc[0].password;
	var userid = doc[0]._id;
	bcrypt.compare(pass, hash, function(err, result) {
	    if (err) {
		throw err;
	    }
	    if (!result) {
		console.log("wrong password");
		res.json({"status": "notok", "msg": "Wrong username or password"}); 
		return;
	    }
	    var token = randomString();
	    req.db.get('sessions').insert({ "token": token, "userid": userid, "created": new Date() }, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		res.cookie('token', token, { maxAge: req.config.sessionMaxAge * 1000, httpOnly: true });
		res.json({"status": "ok"}); 
	    });
	});
    });
});

router.get('/newuser', function(req, res) {
    res.render('newuser', { title: 'New user', authenticated: false });
});

router.post('/adduser', function(req, res) {
    var body = req.body;
    var user = body.username;
    var pass = body.password;

    req.db.get('users').find( { username: user }, { }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length != 0) {
	    console.log("username is already existed");
	    var msg = "Username " + user + " is already existed"; 
	    res.json({"status": "notok", "msg": msg}); 
            return;
	}
	bcrypt.hash(pass, 10, function(err, hash) {
	    if (err || doc == null) {
		throw err;
	    }
	    body.password = hash;
	    req.db.get('users').insert(body, function(err, doc) {
		if (err || doc == null) {
		    throw err;
		}
		var token = randomString();
		req.db.get('sessions').insert({ "token": token, "userid": userid, "created": new Date() }, function(err, doc) {
		    if (err || doc == null) {
			throw err;
		    }
		    res.cookie('token', token, { maxAge: req.config.sessionMaxAge, httpOnly: true });
		    res.json({"status": "ok"});
		});
	    });
	});
    });
});

router.post('/updateuser', function(req, res) {
    var user = req.body.username;
    var pass = req.body.password;

    req.db.get('users').find( { username: user }, { fields: {password: 1, _id: 1 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 0) {
	    console.log("username wasn't found from database");
	    res.json({"status": "notok", "msg": "Wrong password"}); 
            return;
	}
	var hash = doc[0].password;
	bcrypt.compare(pass, hash, function(err, result) {
	    if (err) {
		throw err;
	    }
	    if (!result) {
		console.log("wrong password");
		res.json({"status": "notok", "msg": "Wrong password"}); 
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
});

router.post('/updatepassword', function(req, res) {
    var username = req.body.username;
    var oldpass = req.body.oldpassword;
    var newpass = req.body.newpassword;

    req.db.get('users').find( { username: username }, { fields: {password: 1, _id: 0 } }, function(err, doc) {
	if (err || doc == null) {
	    throw err;
	}
	if (doc.length == 0) {
	    console.log("username wasn't found from database");
	    res.json({"status": "notok", "msg": "Wrong password"}); 
            return;
	}
	var hash = doc[0].password;
	bcrypt.compare(oldpass, hash, function(err, result) {
	    if (err) {
		throw err;
	    }
	    if (!result) {
		console.log("wrong password");
		res.json({"status": "notok", "msg": "Wrong password"}); 
		return;
	    }
	    bcrypt.hash(newpass, 10, function(err, hash) {
		if (err) {
		    throw err;
		}
		req.db.get('users').update( { "username": username },
					    { "$set": { "password": hash } } , function(err, result) {
						if (err) {
						    throw err;
						}
						res.cookie('password', newpass);
						res.json({"status": "ok"}); 
					    });
	    });
	});
    });
});

module.exports = router;
