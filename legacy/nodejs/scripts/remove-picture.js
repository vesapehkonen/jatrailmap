var mongo = require('mongodb');
var monk = require('monk');
var db = monk('localhost:27017/jatrailmap');

if (typeof process.argv[2] == "undefined") {
    console.log("Usege: nodejs " + process.argv[1] + " picture_id");
}
else {
    removePicture(process.argv[2]);
}

function exit(msg) {
    console.log(msg);
    db.close();
    process.exit(0);
}

function removePicture(id) {
    var imgid = new mongo.ObjectID(id);
    var filename;
    var picid;
    db.get('pictures').find( { 'imageid' : imgid }, { filename: 1, _id: 1 }, function(err, doc) {
	if (err) {
	    exit(err);
	    return;
	}
	if (doc.length != 1) {
	    exit("Picture wasn't found");
	    return;
	}
	filename = doc[0].filename;
	picid = doc[0]._id;
	db.get('images').remove( { _id : imgid }, function(err, doc) {
            if (err) {
		exit(err);
		return;
	    }
	    console.log(filename + " / " + imgid + "  removed from image collection");
	    db.get('pictures').remove( { _id : picid }, function(err, doc) {		    
		if (err) {
		    exit(err);
		    return;
		}
		console.log(filename + " / " + picid + "  removed from picture collection");
		exit("ok");
	    });
	});
    });
}
