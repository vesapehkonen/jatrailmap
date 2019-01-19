#!/bin/bash

# Change trail pictures' permissions

#url="http://localhost:8080"
url="http://localhost:3000"

if [ $# -lt 3 ]; then
  echo "usage: $0 username password trailid"
  exit 1
fi

user=$1
pass=$2
trailid=$3
cookie_file=cookie

get_cookie() {
    token=""
    while IFS='' read -r line || [[ -n "$line" ]]; do
	column6=`echo $line | awk -F ' ' '{print $6}'`
	column7=`echo $line | awk -F ' ' '{print $7}'`
	if [ "$column6" == "token" ]; then
	    token=$column7
	fi
    done < "$1"
    echo $token
}

echo "curl -s -X GET -c $cookie_file $url/login?username=${user}&password=${pass}"
curl -s -X GET -c $cookie_file "$url/login?username=${user}&password=${pass}"
echo ""

token=$(get_cookie $cookie_file)

echo "curl -s -X GET --cookie token=$token $url/trail/${trailid}/track"
json=`curl -s -X GET --cookie token="$token" "$url/trail/${trailid}/track"`

ids=`echo $json | jq -r '. | .pics[] | ._id, .imageid, .access, .picturename'`
#echo $ids
#exit
# above returns picture id and image id list
# first line is picture id
# and second line is image id

num=1
for line in $ids; do
    groups=()
    access=""
    if [ $num -eq 1 ]; then 
	num=2
	picid=$line
    elif [ $num -eq 2 ]; then 
	num=3
	imgid=$line
    else
	num=1
	current_access=$line

	read -p "Modify permissions of this image $imgid (current access=${current_access}) [y/n]? " resp
	if [ $resp == "y" ]; then
	    read -p "Set access: Private[p], Group[g] or Trail permissions[t]? " resp
	    if [ $resp == "p" ]; then
		access="private"
	    elif [ $resp == "g" ]; then
		access="group"
		read -p "Give a group list, space separated: " -a groups
	    elif [ $resp == "t" ]; then
		access="public"
	    else
		echo "Nothing selected"
	    fi
	    groups_str=""
	    first_group=1
	    for i in ${groups[@]}; do
		if [ $first_group -eq 1 ]; then
		    first_group=0
		else
		    groups_str="${groups_str}&" 
		fi
		groups_str="${groups_str}groups[]=$i"
	    done

	    data="access=${access}"
	    if [ "$groups_str" != "" ]; then
		data="${data}&${groups_str}"
	    fi
	    echo "Data: $data"
	    read -p "Send request to update picture permissions [y/n]? " resp
	    if [ $resp == "y" ]; then
		echo "Yes"
		echo "curl --globoff -d $data -X PUT --cookie token=$token $url/trail/${trailid}/picture/${picid}/permissions"
		curl --globoff -d $data -X PUT --cookie token="$token" "$url/trail/${trailid}/picture/${picid}/permissions"
	    else
		echo "No"
	    fi
	fi
	echo ""
	echo "----------------------------------------------------------------------"
    fi
done
echo "done"

