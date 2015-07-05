
Just Another Trail Map
======================

What is it?
-----------

The Just Another Trail Map is a service, which allows users to track their
trails using a mobile phone and upload them to a web-service, where the trails
can be watch on a map. It also allows users take pictures using their mobile
phone, which are also showed on the map.

A web application consists a server and client. The server is implemented using
JavaScript, Node.js, Experss, Jade and Mongo database, and it provides JSON REST
Api. The client uses a web browser and it is implemented using JavaScript and
jQuery. It uses the Google Maps Api to show routes and picture points on the
map. The web application is located in nodejs directory which includes folders:

- routes, contains combinations of models and controllers
- views, contains application views (Jade files)
- public, contains client's JavaScript and style-sheet files

Mobile application is implemented for a Android phone using Java and Android
Studio. It records GPS coordinates of trails, and saves GPS coordinates for
pictures, which have taken at that time, and uploads them to the server. The
applications files are located in android folder.


Downloading
-----------

The latest version can be found on the Github server project page under

https://github.com/vesapehkonen/jatrailmap.

Download the project repository
 
git clone https://github.com/vesapehkonen/jatrailmap 


Documentation
-------------

The documentation is available on the project wiki page

https://github.com/vesapehkonen/jatrailmap/wiki. 


Installation
------------

The web-application files are located in the directory jatrailmap/nodejs. Please
see the README file in there. The Android application is built using the Android
Studio and the project files are located in jatrailmap/android directory. 


Licensing
---------

This project is licensed under the terms of the MIT license. Please see the file
called LICENSE.txt.

  
Contributing
------------

Contributions are welcomed. You can contribute to the project by reporting
issues, suggesting new features, or submitting pull requests.


Contacts
--------

vesa.pehkonen@gmail.com

