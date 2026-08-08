package jatrailmap;

//import org.springframework.data.annotation.Id;
//import org.springframework.data.mongodb.core.mapping.Document;

public class Loc {
    private String type;
    private double[] coordinates;

    public Loc() { }
    Loc(String t, double[] coords) {
	type = t;
	coordinates = new double[3];
	coordinates[0] = coords[0];
	coordinates[1] = coords[1];
	coordinates[2] = coords[2];
    }

    void setType(String t) {
	type = t;
    }
    public void setCoordinates(double[] coords) {
	coordinates = new double[3];
	coordinates[0] = coords[0];
	coordinates[1] = coords[1];
	coordinates[2] = coords[2];
    }
    String getType() {
	return type;
    }
    public double[] getCoordinates() {
	return coordinates;
    }
}
