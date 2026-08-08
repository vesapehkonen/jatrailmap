package jatrailmap;

import java.util.List;

interface CustomLocationRepository {
    int update(String id, double lat, double lng);
    //public void insert(List<Location> locs);
}
