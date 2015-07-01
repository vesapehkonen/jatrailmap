var mongo = require('mongodb');
var monk = require('monk');
var db = monk('localhost:27017/jatrailmap');

var trailid;
var trailname = process.argv[2];


if (typeof process.argv[2] == "undefined") {
    console.log("Usege: nodejs " + process.argv[1] + " trailname");
}
else {
    remove();
}

function exit(msg) {
    console.log(msg);
    db.close();
    process.exit(0);
}

function removeTrail() {
    db.get('trails').remove( { _id : trailid }, function(err, doc) {		    
        if (err) {
            exit(err);
	    return;
	}
	exit(trailname + " trail removed, _id: " + trailid);
    });
}

function removeLocations() {
    db.get('locations').remove( { trailid : trailid }, function(err, doc) {		    
        if (err) {
            exit(err);
	    return;
	}
	console.log("Number of location points removed: " + doc);
	removeTrail();
    });
}

function removePictures(array, i) {
    if (i < array.length) {
	db.get('pictures').remove( { _id : array[i]._id }, function(err, doc) {		    
            if (err) {
		exit(err);
		return;
	    }
	    console.log(array[i].filename + "  removed from picture collection");
	    removePictures(doc, i + 1);
	});
    }
    else {
	removeLocations();
    }
}

function removeImages(array, i) {
    if (i < array.length) {
	db.get('images').remove( { _id : array[i].imageid }, function(err, doc) {
            if (err) {
		exit(err);
		return;
	    }
	    console.log(array[i].filename + "  removed from image collection");
	    removeImages(array, i + 1);

	});
    }
    else {
	removePictures(array, 0);
    }
}

function remove() {
    db.get('trails').find( { "trailname" : trailname }, { "_id" : 1 }, function(err, doc) {
        if (err) {
            exit(err);
	    return;
	}
	if (doc.length === 0) {
            exit("Nothing was removed");
	}
	trailid = doc[0]._id;
	db.get('pictures').find( { 'trailid' : trailid },
				 { trailid: 1, _id: 1,
				   imagid: 1, filename: 1 },
				 function(err, doc) {
				     if (err) {
					 exit(err);
					 return;
				     }
				     removeImages(doc, 0);
				 });
    });
}

