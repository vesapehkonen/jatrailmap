var mongo = require('mongodb');
var monk = require('monk');
var db = monk('localhost:27017/jatrailmap');

var j = 0;
var count;
db.get('locations').find( { }, function(err, doc) { 
    console.log(doc.length);
    count = doc.length;
    for (var i=0; i<doc.length; i++) {
	if ((typeof doc[i].loc.coordinates[0] == 'string') &&
	    (typeof doc[i].loc.coordinates[1] == 'string') &&
	    (typeof doc[i].loc.coordinates[2]  == 'string')) {
	    console.log(doc[i]._id, doc[i].loc.coordinates[0], doc[i].loc.coordinates[1], doc[i].loc.coordinates[2]); 

	    db.get('locations').update(
		{ _id: doc[i]._id }, 
		{ $set : { 'loc.coordinates': [parseFloat(doc[i].loc.coordinates[0]),
					       parseFloat(doc[i].loc.coordinates[1]),
					       parseFloat(doc[i].loc.coordinates[2])
					      ] 
			 }
		},
		function(err, doc2) {
		    if (err) {
			exit(err);
		    }
		    console.log("ok");
		    j++;
		    if (j == count) {
			process.exit(0);
		    }
		}
	    );


	    
	}
	else {
	    count--;
	}
    }
});


