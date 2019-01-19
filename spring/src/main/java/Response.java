package jatrailmap;

public class Response {
    private String status;
    private String msg;
    
    public Response(String s, String m) {
	status = s;
	msg = m;
    }
    public String getStatus() {
	return status;
    }
    public String getMsg() {
	return msg;
    }
}
