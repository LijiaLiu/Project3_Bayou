

import java.net.URL;
import java.util.StringTokenizer;

import ut.distcomp.framework.Config;

public class MessageParse {
	
	private static Config config;
	
	public MessageParse(Config config) {
		this.config = config;
	}
	
	public String addSenderNum(String msg) {
		
		return config.procNum + " " + msg;
		
	}
	
	public int getSenderNum(String msg) {
		
		String [] arr = msg.split(" ", 2);
		
		return Integer.parseInt(arr[0]);
		
	}
	
	public String getSenderMsg(String msg) {
		
		String [] arr = msg.split(" ", 2);
		
		return arr[1];
		
	}

}
