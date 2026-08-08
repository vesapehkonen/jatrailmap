package jatrailmap;

import java.util.List;

interface CustomTrailRepository {
    int update(String trailid, String trailname, String location, String desc);
    int updateAccess(String trailid, String access, String[] groups);
}
