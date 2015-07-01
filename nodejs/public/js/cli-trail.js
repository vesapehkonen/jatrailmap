$(document).ready(function() {
    var url = window.location.pathname;
    var parts = url.split("/");
    if (parts.length == 3) {
	var id = parts[2];

	$.ajax({
	    type: 'GET',
	    url: '/gettrail',
	    data: { 'id': id },
	    success: function(res) {
		if (res.status == 'ok') {
		    var elem = document.getElementsByTagName('span')[0];
		    elem.innerHTML = "Ok, data received";
		    elem.style.backgroundColor="blue";
		    elem.style.color="white";
		    setTimeout(function() {
			document.getElementsByTagName('span')[0].innerHTML = "";
		    }, 5000);
		    initializeMap(res.data);
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
    var array = data.GetTrailResponse;
    var coords = [];
    var locs = [];
    var infoWindowPos;
    var infoWindow;
    var marker;
    var height;

    height = Math.max($(document).height(), $(window).height()) - 150;
    $('#canvas').css({'width': 'auto', 'height': height});

    for (var i=0; i<array.length; i++) {
	if (array[i].type == 'LocationCollection') {
	    locs = array[i].locations;
	}
	else if (array[i].type == 'PictureCollection') {
	    pics = array[i].pictures;
	}
    }
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
    
    var path = new google.maps.Polyline({
        path: coords,
        strokeColor: "#0000FF",
        stroreOpacity: 0.8,
        strokeWeight: 2
    });
    path.setMap(map);

    for (j=0; j<pics.length; j++) {
	infoWindowPos = new google.maps.LatLng(pics[j].loc.coordinates[1], pics[j].loc.coordinates[0]); 
	var contentString = '<div><a href="/images/' + pics[j].imageid +
	    '"><img src="/images/' + pics[j].imageid + '" width="200"></a></div>';

	infoWindow = new google.maps.InfoWindow({
	    content: contentString
	});
	marker = new google.maps.Marker({
	    position: infoWindowPos,
	    map: map,			     
            title: pics[j].picturename
	});     
	google.maps.event.addListener(marker, 'click', (function(marker, infoWindow) {
	    return function() { 
		infoWindow.open(map, marker);
	    }
	})(marker, infoWindow));
    }
}
