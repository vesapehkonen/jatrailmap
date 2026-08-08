$('#btnAddGroup').on('click', addGroup);
$('#btnEditGroup').on('click', editGroup);
$('#btnDeleteGroup').on('click', deleteGroup);
$('#btnUpdateGroup').on('click', updateGroup);

function addGroup(event) {
    event.preventDefault();

    if ($('#inputGroupName').val().length == 0) {
        alert('Please fill in gourp name');
        return false;
    }
    var userids = [];
    var checked = false;
    var checkboxes = document.getElementsByTagName("input");
    for (var i=0; i<checkboxes.length; i++) {
	if (checkboxes[i].type == "checkbox") {
	    if (checkboxes[i].checked) {
		checked = true;
		userids.push(checkboxes[i].name);
	    }
	}
    }
    if (checked == false) {
	alert('Please select at least one checkbox');
	return false;
    }
    var newGroup = {
        'groupname': $('#inputGroupName').val(),
        'users': userids
    };

    $.ajax({
        type: 'POST',
        data: newGroup,
        url: '/addgroup',
        dataType: 'JSON',
	success: function(data) {
	    if (data.status == 'ok') {
		var elem = document.getElementsByTagName('span')[0];
		elem.innerHTML = "The new group was added.";
		elem.style.backgroundColor="blue";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[0].innerHTML = "";
		    window.location = '/groups';
		}, 3000);
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

function editGroup(event) {
    event.preventDefault();
    window.location = '/editgroup/' + $("#selectEditGroup option:selected").val();
}

function updateGroup(event) {
    event.preventDefault();

    if ($('#inputGroupName').val().length == 0) {
        alert('Please fill in gourp name');
        return false;
    }
    var userids = [];
    var checked = false;
    var checkboxes = document.getElementsByTagName("input");
    for (var i=0; i<checkboxes.length; i++) {
	if (checkboxes[i].type == "checkbox") {
	    if (checkboxes[i].checked) {
		checked = true;
		userids.push(checkboxes[i].name);
	    }
	}
    }
    if (checked == false) {
	//alert('Please select at least one checkbox');
	//return false;
    }
    var data = {
        'groupname': $('#inputGroupName').val(),
        'members': userids,
	'groupid': $('#inputGroupId').val()
    };

    $.ajax({
        type: 'POST',
        data: data,
        url: '/updategroup',
        dataType: 'JSON',
	success: function(data) {
	    if (data.status == 'ok') {
		var elem = document.getElementsByTagName('span')[0];
		elem.innerHTML = "The group was updated.";
		elem.style.backgroundColor="blue";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[0].innerHTML = "";
		    window.location = '/groups';
		}, 3000);
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

function deleteGroup(event) {
    event.preventDefault();
    $.ajax({
        type: 'DELETE',
        data: '',
        url: '/group/' + $("#selectDelGroup option:selected").val(),
        dataType: 'JSON',
	success: function(data) {
	    if (data.status == 'ok') {
		var elem = document.getElementsByTagName('span')[0];
		elem.innerHTML = "The group was deleted.";
		elem.style.backgroundColor="blue";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[0].innerHTML = "";
		    window.location = '/groups';
		}, 3000);
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
