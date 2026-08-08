$("#btnAddButton").on('click', addButton);
$("#btnRemoveButton").on('click', removeButton);
$('#btnAddTrail').on('click', loadFiles);

$(document).ready(function() {

});

var counter = 0;

function addButton(event) {
    if(counter >= 10){
        alert("Only 10 textboxes are allowed");
        return false;
    }
    var newTextBoxDiv = $(document.createElement('div')).attr("id", 'TextBoxDiv' + counter);

    var str = 
	'<input id="inputPicture" type="file" name="addpic">' +
	'<input id="inputPictureLon" type="text" placeholder="lon" size="8">' +
	'<input id="inputPictureLat" type="text" placeholder="lat" size="8">' +
	'<input id="inputPictureAlt" type="text" placeholder="alt" size="8">' +
	'<input id="inputPictureTimestamp" type="text" placeholder="timestamp" size="8">' + 
	'<input id="inputPictureName" type="text" placeholder="picturename" size="8">' + 
	'<input id="inputPictureDescription" type="text" placeholder="description" size="8">';

    newTextBoxDiv.after().html(str);
    newTextBoxDiv.appendTo("#TextBoxesGroup");
    counter++;
}

function removeButton(event) {
    if (counter == 0) {
	alert("No more textbox to remove");
	return false;
    }   
    counter--;
    $("#TextBoxDiv" + counter).remove();
}

var log = function(text) {
    var elem = document.getElementsByTagName('span')[1];
    elem.innerHTML = text;
    elem.style.backgroundColor="yellow";
    elem.style.color="black";
    setTimeout(function() {
	document.getElementsByTagName('span')[1].innerHTML = "";
    }, 60000);
};

var picDataArray = [];
var readers = [];
var readerInd = 0;

function loadFiles(event) {
    var elem;
    var file;

    picDataArray = [];
    readers = [];
    readerInd = 0;

    event.preventDefault();
    if (window.File && window.FileReader && window.FileList && window.Blob) {
	//alert("File API supported.!");
	for (var i=0; i<counter; i++) {
	    readers[i] = new FileReader();
	    readers[i].onload = function(e) {
		if (e.target.error) {
		    log("couldn't read file");
		    return;
		}
		picDataArray.push(btoa(e.target.result));
		readerInd++;
		if (readerInd == counter) {
		    sendTrail();
		}
	    };
	    var elem = document.getElementById('TextBoxDiv' + (i).toString());
	    var file = elem.children[0].files[0];
	    readers[i].readAsBinaryString(file);
	}
	if (counter == 0) {
	    sendTrail();
	}
    }
    else {
	alert('The File APIs are not fully supported in this browser.\nI will not send pictures');
	sendTrail();
    }
}

function sendTrail() {
    var obj;
    var locArray = [];
    var picArray = [];
    var coords = [];

    var lines = $('#textareaCoordinates').val().split('\n');

    for (var i=0; i<lines.length; i++) {
	var words = lines[i].split(',');
	if (words.length == 4) {
	    coords = [];
	    for (var j=0; j<3; j++) {
		coords.push(parseFloat(words[j]));
	    }
	    obj = { 'timestamp': words[3],
		    'loc': { 'type': 'Point', 'coordinates': coords }
		  };
	    locArray.push(obj);
	}
    }

    for (var i=0; i<counter; i++) {
	coords = [];
	coords.push($('#TextBoxDiv' + i + ' #inputPictureLon').val());
	coords.push($('#TextBoxDiv' + i + ' #inputPictureLat').val());
	coords.push($('#TextBoxDiv' + i + ' #inputPictureAlt').val());
	obj = { 'timestamp':   $('#TextBoxDiv' + i + ' #inputPictureTimestamp').val(),
		'picturename': $('#TextBoxDiv' + i + ' #inputPictureName').val(),
		'description': $('#TextBoxDiv' + i + ' #inputPictureDescription').val(),
		'filename':    $('#TextBoxDiv' + i + ' #inputPicture').val(),
		'file': picDataArray[i],
		'loc': { 'type': 'Point', 'coordinates': coords }
	      };
	picArray.push(obj);
    }

    var data = { 'newtrail' : [
	{ 'type': 'LocationCollection',
	  'locations': locArray
	},
	{ 'type': 'PictureCollection',
	  'pictures': picArray
	},
	{ 'type': 'TrailInfo',	
          'access':       $('#inputAccess').val(),
          'trailname':    $('#inputTrailName').val(),
          'description':  $('#inputDescription').val(),
          'date':         $('#inputDate').val(),
          'locationname': $('#inputLocationName').val()
	},
	{ 'type': 'UserInfo',	
          'username':     $('#inputUsername').val(),
          'password':     $('#inputPassword').val(),
	}
    ] };
    
    $.ajax({
        type: 'POST',
        data: data,
        url: '/addtrail',
        dataType: 'JSON',
	success: function(res) {
	    if (res.status == 'ok') {
		var elem = document.getElementsByTagName('span')[0];
		elem.innerHTML = res.msg;
		elem.style.backgroundColor="blue";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[0].innerHTML = "";
		}, 5000);
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
	complete: function(xhr, textstatus) {

	}
    });
}
