package jatrailmap;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection="images")
public class Image {
    @Id private String id;
    private String img;

    Image() { } 
    Image(String i, String data) {
	id = i;
	img = data;
    } 
    public void setImg(String img) {
	this.img = img;
    }
    public String getId() {
	return id;
    }
    public String get_id() {
	return id;
    }
    public String getImg() {
	return img;
    }
}
