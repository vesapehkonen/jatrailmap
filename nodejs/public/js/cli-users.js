$('#btnLogin').on('click', login);
$('#btnAddUser').on('click', addUser);
$('#btnUpdateUser').on('click', updateUser);
$('#btnUpdatePassword').on('click', updatePassword);

function addUser(event) {
    event.preventDefault();

    if ($('#inputUsername').val().length == 0) {
        alert('Please fill in username');
        return false;
    }
    if ($('#inputPassword').val().length == 0) {
        alert('Please fill in password');
        return false;
    }

    var newUser = {
        'username': $('#inputUsername').val(),
        'password': $('#inputPassword').val(),
        'fullname': $('#inputFullname').val(),
         'country': $('#inputCountry').val(),
         'state': $('#inputState').val(),
         'city': $('#inputCity').val(),
         'email': $('#inputEmail').val()
    };

    $.ajax({
        type: 'POST',
        data: newUser,
        url: '/adduser',
        dataType: 'JSON',
	success: function(data) {
	    if (data.status == 'ok') {
		window.location = '/';
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

function updateUser(event) {
    event.preventDefault();

    if ($('#inputPassword').val().length == 0) {
        alert('Please fill in your current password');
        return false;
    }

    var newUser = {
        'username': $('#inputUsername').val(),
        'password': $('#inputPassword').val(),
        'fullname': $('#inputFullname').val(),
         'country': $('#inputCountry').val(),
         'state': $('#inputState').val(),
         'city': $('#inputCity').val(),
         'email': $('#inputEmail').val()
    };

    $.ajax({
        type: 'POST',
        data: newUser,
        url: '/updateuser',
        dataType: 'JSON',
	success: function(data) {
	    if (data.status == 'ok') {
		var elem = document.getElementsByTagName('span')[0];
		elem.innerHTML = "User information has been updated";
		elem.style.backgroundColor="blue";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[0].innerHTML = "";
		}, 5000);
	    }
	    else {
		if (data.msg == "cookies.username and req.body.username are different") {
		    window.location = '/';
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
	    }
	},
	error: function(xhr, textStatus) {
	    alert("HTTP error code: " + xhr.status + " response:\n" + xhr.responseText);
	},
	complete: function(xhr, status) {

	}
    });
}

function updatePassword(event) {
    event.preventDefault();

    if ($('#inputOldPassword').val().length == 0) {
        alert('Please fill in your current password');
        return false;
    }
    if ($('#inputNewPassword').val().length == 0) {
        alert('Please fill in new password');
        return false;
    }

    var data = {
        'oldpassword': $('#inputOldPassword').val(),
        'newpassword': $('#inputNewPassword').val(),
    };

    $.ajax({
        type: 'POST',
        data: data,
        url: '/updatepassword',
        dataType: 'JSON',
	success: function(data) {
	    if (data.status == 'ok') {
		var elem = document.getElementsByTagName('span')[1];
		elem.innerHTML = "The password has been changed";
		elem.style.backgroundColor="blue";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[1].innerHTML = "";
		}, 5000);
	    }
	    else {
		var elem = document.getElementsByTagName('span')[1];
		elem.innerHTML = data.msg;
		elem.style.backgroundColor="red";
		elem.style.color="white";
		setTimeout(function() {
		    document.getElementsByTagName('span')[1].innerHTML = "";
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

function login(event) {
    event.preventDefault();

    if ($('#inputUserName').val().length == 0) {
        alert('Please fill in username');
        return false;
    }
    if ($('#inputUserPassword').val().length == 0) {
        alert('Please fill in password');
        return false;
    }
    
    $.ajax({
	type: 'GET',
	url: '/login',
	data: { username: $('#inputUserName').val(), password: $('#inputUserPassword').val() },
	success: function(data) {
	    if (data.status == 'ok') {
		window.location = '/';
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
