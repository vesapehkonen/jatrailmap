package jatrailmap;

import java.util.List;

public class GetTrailResponse {
    private String status;
    private String date;
    private String trailname;
    private String location;
    private String description;
    private List<Location> locs;
    private List<Picture> pics;

    public GetTrailResponse(String status, String date, String trailname,
			    String desc, String location,
			    List<Location> locs, List<Picture> pics) {
	this.status = status;
	this.date = date;
	this.trailname = trailname;
	this.location = location;
	this.description = description;
	this.locs = locs;
	this.pics = pics;
    }
    public String getStatus() {
	return status;
    }
    public String getDate() {
	return date;
    }
    public String getTrailname() {
	return trailname;
    }
    public String getLocation() {
	return location;
    }
    public String getDescription() {
	return description;
    }
    public List<Location> getLocs() {
	return locs;
    }
    public List<Picture> getPics() {
	return pics;
    }
}
