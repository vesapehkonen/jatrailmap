var express = require('express');
var path = require('path');
var favicon = require('static-favicon');
var logger = require('morgan');
var cookieParser = require('cookie-parser');
var bodyParser = require('body-parser');

// Database
var mongo = require('mongodb');
var monk = require('monk');
var db = monk('localhost:27017/jatrailmap');

var main =   require('./routes/main');
var trails =   require('./routes/trails');
var users =   require('./routes/users');
var groups =   require('./routes/groups');

var geo = require('./geo');
var config = require('./config');

var app = express();

// view engine setup
app.set('views', path.join(__dirname, 'views'));
app.set('view engine', 'jade');

app.use(favicon());
app.use(logger('dev'));
app.use(bodyParser.json());
//app.use(bodyParser.urlencoded());
app.use(bodyParser.urlencoded({ extended: true }));
app.use(cookieParser());
app.use(express.static(path.join(__dirname, 'public')));

// set size limit for received data, doesn't work
// app.use(bodyParser.json({limit: '50mb'}));
// app.use(bodyParser.urlencoded({limit: '50mb', extended: true}));

app.use(function(req, res, next) {
    console.log("--------------------------------------------");
    console.log((new Date()).toString());
    console.log("url: ", req.url);
    console.log("cookies: ", req.cookies);
    console.log("query: ", req.query);
    console.log("body: ", req.body);

    req.db = db;
    req.geo = geo;
    req.config = config;
    next();
});

app.use('/', main);
app.use('/', trails);
app.use('/', users);
app.use('/', groups);

// catch 404 and forwarding to error handler
app.use(function(req, res, next) {
    var err = new Error('Not Found');
    err.status = 404;
    next(err);
});

// error handlers

// development error handler
// will print stacktrace
if (app.get('env') === 'development') {
    app.use(function(err, req, res, next) {
        res.status(err.status || 500);
        res.render('error', {
            message: err.message,
            error: err
        });
    });
}

// production error handler
// no stacktraces leaked to user
app.use(function(err, req, res, next) {
    res.status(err.status || 500);
    res.render('error', {
        message: err.message,
        error: {}
    });
});

module.exports = app;

// remove current TTL from sessions and set a new one
db.get('sessions').dropIndex({"created": 1 });
db.get('sessions').createIndex({"created": 1 }, { expireAfterSeconds: config.sessionMaxAge } );
