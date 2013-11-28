

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class Command extends Thread {
	
	Env env;
	ProcessId[] leaders;
	String file;
	
	Command(Env env, ProcessId[] leaders) {
		this.env = env;
		this.leaders = leaders;
		this.file = "leaderState.dat";
		
		try {
			BufferedWriter buffer = new BufferedWriter(new FileWriter(file));
			buffer.append("");
			buffer.flush();
			buffer.close();
		} catch (IOException e) {
			System.out.println(e);
		}
	}

	public void run() {
		for (;;) {
			
			String status = "";
			try {
				BufferedWriter buffer = new BufferedWriter(new FileWriter(file, true));
				for (int i = 0; i < leaders.length; i ++) {
					Leader p = (Leader) env.procs.get(leaders[i]);
					status += p.ballot_number + "-" + Boolean.toString(p.active) + " ";
				}
				buffer.append(status);
				//System.out.println(status);
				buffer.append("\n");
				buffer.flush();
				buffer.close();
			} catch (IOException e) {
				System.out.println(e);
			}
		}
		
	}

}
