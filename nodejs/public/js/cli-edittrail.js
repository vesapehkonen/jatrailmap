$('#btnUpdateTrail').on('click', updateTrail);

var id = 0;
var locs = [];
var pics = [];
var markers = [];
var windows = [];
var updateCommands = [];

$(document).ready(function() {
    var url = window.location.pathname;
    var parts = url.split("/");
    if (parts.length == 4) {
	id = parts[2];

	$.ajax({
	    type: 'GET',
	    url: '/trail/' + id + '/track',
	    data: { },
	    success: function(res) {
		if (res.status == 'ok') {
		    /*
		    var elem = document.getElementsByTagName('span')[0];
		    elem.innerHTML = "Ok, data received";
		    elem.style.backgroundColor="blue";
		    elem.style.color="white";
		    setTimeout(function() {
			document.getElementsByTagName('span')[0].innerHTML = "";
		    }, 5000);
		    */
		    initializeMap(res);
		}
		else {
		    var elem = document.getElementsByTagName('span')[0];
		    elem.innerHTML = res.msg;
		    elem.style.backgroundColor="red";
		    elem.style.color="white";
		    setTimeout(function() {
			document.getElementsByTagName('span')[0].innerHTML = "";
		    }, 5000);
		}
	    },
	    error: function(xhr, textStatus) {
		alert("HTTP error code: " + xhr.status + " response:\n" + xhr.responseText);
	    },
	    complete: function(xhr, status) {

	    }
	});
    }
});

function initializeMap(data) {
    //var array = data.GetTrailResponse;
    var coords = [];
    var infoWindowPos;
    var infoWindow;
    var marker;
    var height;
    
    height = Math.max($(document).height(), $(window).height()) - 150;
    $('#canvas').css({'width': 'auto', 'height': height});

    /*
    for (var i=0; i<array.length; i++) {
	if (array[i].type == 'LocationCollection') {
	    locs = array[i].locations;
	}
	else if (array[i].type == 'PictureCollection') {
	    pics = array[i].pictures;
	}
    }
    */
    locs = data.locs;
    pics = data.pics;
    
    var bounds = new google.maps.LatLngBounds();

    for (j=0; j<locs.length; j++) {
	coords[j] =new google.maps.LatLng(locs[j].loc.coordinates[1], locs[j].loc.coordinates[0]); 
	bounds.extend(coords[j]);
    }
    
    var mapProp = {
        mapTypeId: google.maps.MapTypeId.ROADMAP
    };
    var map = new google.maps.Map(document.getElementById("canvas"), mapProp);
    map.fitBounds(bounds);

    initializePolyline(map, coords);
    initializeMarkers(map);

    google.maps.event.addListenerOnce(map, 'tilesloaded', function(){
	document.getElementById("loading").style.visibility = 'hidden';
	document.getElementById("loading").style.display = 'none';
    });
}

function initializePolyline(map, coords) {
    var mvccoords = new google.maps.MVCArray(coords);
    
    var path = new google.maps.Polyline({
	editable: true,
        path: mvccoords,
        strokeColor: "#0000FF",
        stroreOpacity: 0.8,
        strokeWeight: 2
    });
    path.setMap(map);

    google.maps.event.addListener(mvccoords, 'set_at', function(vertex) {
	//alert("locs: " + locs + " vertex: " + vertex);
	var id = locs[vertex].id;
	var lat = mvccoords.getArray()[vertex].lat();
	var lng = mvccoords.getArray()[vertex].lng();
	updateCommands.push( { 'action' : 'updateLocation', 'id': id, 'lat' : lat, 'lng' : lng } );
    });

    google.maps.event.addListener(mvccoords, 'insert_at', function(vertex) {
	alert('Adding a new points to the path is not supported!');
    });

    google.maps.event.addListener(mvccoords, 'remove_at', function(vertex) {
	var id = locs[vertex].id;
	updateCommands.push( { 'action' : 'removeLocation', 'id': id} );
	locs.splice(vertex, 1);
    });

    google.maps.event.addListener(path, 'rightclick', function(event) {
	if (event.vertex == undefined) {
	    return;
	}
	if (mvccoords.length <= 2) {
	    return;
	}
	mvccoords.removeAt(event.vertex);
    });
}

function updatePicTitle(id) {
    var text = document.getElementById(id).value;
    for (var i=0; i<pics.length; i++) {
	if (pics[i].id == id) {
	    //pics[i].picturename = text;
	    markers[i].setTitle(text);
	    updateCommands.push( { 'action' : 'updatePicturename', 'id': pics[i].id, 'name' : text } );
	    break;
	}
    }
}

function initializeMarkers(map) {
    for (var i=0; i<pics.length; i++) {
	var id = pics[i].id;
	var imageid = pics[i].imageid;
	var infoWindowPos = new google.maps.LatLng(pics[i].loc.coordinates[1], pics[i].loc.coordinates[0]); 
	var title = pics[i].picturename;

	var contentString = '<div><a href="/image/' + imageid + '"><img src="/image/' +
	    imageid + '" width="200"></a><p>Title: <input type="text" value="' +
	    title + '" id="' + id + '" onblur="updatePicTitle(' + "'" + id + "'" + ');"></p></div>';

	infoWindow = new google.maps.InfoWindow({
	    content: contentString
	});
	marker = new google.maps.Marker({
	    position: infoWindowPos,
	    map: map,			     
            title: pics[i].picturename,
	    draggable:true
	});

	windows.push(infoWindow);
	markers.push(marker);

	google.maps.event.addListener(infoWindow, 'closeclick', (function(i) {
	    return function() {
		;
	    }
	})(i));

	google.maps.event.addListener(marker, 'click', (function(marker, infoWindow) {
	    return function() { 
		infoWindow.open(map, marker);
	    }
	})(marker, infoWindow));

	google.maps.event.addListener(marker, 'rightclick',   (function(i) {
	    return function() { 
		markers[i].setMap(null);
		var id = pics[i].id;
		updateCommands.push( { 'action': 'removePicture', 'id': id } );
	    }
	})(i));

	google.maps.event.addListener(marker, 'dragend', (function(i) {
	    return function(param) {
		var id = pics[i].id;
		lat = param.latLng.lat();
		lng = param.latLng.lng();
		updateCommands.push( { 'action': 'updatePictureLocation', 'id': id, 'lat' : lat, 'lng' : lng } );
	    }
	})(i));
    }
}

function updateTrail(event) {
    event.preventDefault();

    var trail = {
        'id':$('#inputTrailId').val(),
        'trailname': $('#inputTrailname').val(),
        'location': $('#inputLocation').val(),
        'date': $('#inputDate').val(),
        'time': $('#inputTime').val(),
        'distance': $('#inputDistance').val(),
        'description': $('#inputDescription').val(),
	'updates': updateCommands
    };

    $.ajax({
        type: 'PUT',
        /*data: trail,*/
        data: JSON.stringify(trail),
        url: '/trail/' + trail.id,
        /*dataType: 'JSON',*/
        contentType: "application/json",
	success: function(data) {
	    if (data.status == 'ok') {
		var elem = document.getElementsByTagName('span')[0];
		elem.innerHTML = "Trail information has been updated";
		elem.style.backgroundColor="blue";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[0].innerHTML = "";
		    window.location = '/trail/' + id;
		}, 2000);
		return;
	    }
	    else {
		var elem = document.getElementsByTagName('span')[0];
		elem.innerHTML = data.msg;
		elem.style.backgroundColor="red";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[0].innerHTML = "";
		}, 5000);
	    }
	},
	error: function(xhr, textStatus) {
	    alert("HTTP error code: " + xhr.status + " response:\n" + xhr.responseText);
	},
	complete: function(xhr, status) {

	}
    });
}
