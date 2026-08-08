#!/bin/sh
ps aux | grep nodejs | grep -v grep > /dev/null
if [ $? -ne 0 ]; then
    echo "**" `date` " Node.js is not running, starting it." >> start.log
#    ( cd ~/jatrailmap/nodejs ; nohup npm start >> jatrailmap.log 2>&1 & ) 
fi
