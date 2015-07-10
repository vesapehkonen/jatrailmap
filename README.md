
Just Another Trail Map
======================

What is it?
-----------

Just Another Trail Map is a service which allows users to track their trails
using a mobile phone, then upload the trails to a web-service. The user can then
view their trails on a map. The service also allows users to take photos using
their mobile phone, these are also showed on the map.

A web application consists of a server and a client. The server is implemented
using: JavaScript, Node.js, Express, Jade and Mongo database, and it provides a
JSON REST Api. The client uses a web browser and it is implemented using
JavaScript and jQuery. It uses the Google Maps Api to show routes and picture
points on the map. The web application is located in nodejs directory which
includes the following folders:

- routes: contains combinations of models and controllers
- views: contains application views (Jade files)
- public: contains the client's JavaScript and style-sheet files

The mobile application is implemented on an Android phone using Java and Android
Studio. It records the GPS coordinates of trails, and saves the GPS coordinates
for photos, which have been taken at that time, then uploads them to the server.
The mobile application files are located in an android folder.


Downloading
-----------

The latest version can be found on the Github server project page under

https://github.com/vesapehkonen/jatrailmap.

Download the project repository
 
git clone https://github.com/vesapehkonen/jatrailmap 


Documentation
-------------

Documentation is available on the project wiki page

https://github.com/vesapehkonen/jatrailmap/wiki. 


Installation
------------

The web-application files are located in the directory jatrailmap/nodejs. Please
see the README file there also. The Android application is built using Android
Studio and the project files are located in jatrailmap/android directory.


Licensing
---------

This project is licensed under the terms of the MIT license. Please see the file
called LICENSE.txt.

  
Contributing
------------

Contributions are welcome. You can contribute to the project by reporting
issues, suggesting new features, or submitting pull requests.


Contacts
--------

vesa.pehkonen@gmail.com

