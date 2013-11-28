

import java.io.BufferedReader;
import java.io.Console;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;

import ut.distcomp.framework.*;

public class PlayList {

	private static Config config;
	private static NetController controller;
	private static DTLog dtLog;
	private static MessageParse parse;
	private static boolean[] pYes;
	private static boolean[] pFailure;
	private static String state;
	
	private static int coordinatorNum = 0;
	
	private final static long time_out_short = 6000;
	private final static long time_out_long = time_out_short + 1000;
	private final static long time_out_newCoordinator = time_out_long + 5000;
	private final static long time_out_newProcess = time_out_newCoordinator + 1000;
	
	private static Map<String, Song> playList = new HashMap<String, Song>();
	private static String type;
	private static String songName;
	private static URL url;
	
	private static final boolean DEBUG = false;
	private static final boolean DEBUG2 = false;
	private static final boolean DEBUG3 = false;
	private static final boolean DEBUG4 = false;
	private static final boolean DEBUG5 = false;
	public PlayList(){
		
	}
	
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) {
		
		System.out.println("Started....");
		
		// initialize config and controller
		try {
			config = new Config(args[0]);
		} catch (FileNotFoundException e) {
			System.out.print("File not found: " + e);
		} catch (IOException e) {
			System.out.print("IOException when reading main parameters: " + e);
		}
		controller = new NetController(config);	
		
		dtLog = new DTLog("DTLog" + config.procNum);
		//dtLog.clearDTLog();
		
		parse = new MessageParse(config);
		
		pYes = new boolean[config.numProcesses];
		pFailure = new boolean[config.numProcesses];
		
		System.out.println("Initialized:\nprocNum = " + config.procNum);
		
		for (int i = 0; i < config.numProcesses; i ++) {
			System.out.println("Address: " + config.addresses[i] + ", Port: " + config.ports[i]);
		}
		
		
		if(config.procNum == coordinatorNum) {
			
			runCoordinator();
			
		} else {
			
			runProcess();
			
		}

	}

	public void addSong(String songName, URL url) {
		Song s = new Song(songName, url);
		playList.put(songName, s);
	}
	
	public void deleteSong(String songName) {
		playList.remove(songName);
	}
	
	public void editSong(String songName, URL url) {
		Song s= playList.get(songName);
		s.setURL(url);
	}
	
	public static void resetCoordinatorNum() {
		PlayList.coordinatorNum = 0;
	}
	
	private static void commit(String type, String songName, URL url) {
		if (type.equals("ADD")) {
			playList.put(songName, new Song(songName, url));
			System.out.println("Commited: ADD " + songName + " URL= " + url);
		} else if (type.equals("DELETE")) {
			playList.remove(songName);
			System.out.println("Command: DELETE " + songName);
		} else if (type.equals("EDIT")) {
			playList.put(songName, new Song(songName, url));
			System.out.println("Command: EDIT " + songName + " URL= " + url);
		}		
	}
	
	
	private static void runCoordinator() {
		
		Reader r = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(r);
		
		String command;
		type = null;
		songName = null;
		url = null;
		
		long start_time;
		
		System.out.println("This is the coordinator p" + coordinatorNum);
		
		process_recovery();
		
		coorditator_iteration:
		while (true) {
			
			resetCoordinatorNum();
			
			// initiate pYes with false
			Arrays.fill(pYes, Boolean.FALSE);
			Arrays.fill(pFailure, Boolean.FALSE);
			
			// read the user command through console line
			System.out.println("Please enter a command:");

			while (true) {
				try {
					command = br.readLine();
					break;
				} catch (IOException e1) {
					System.out.println("Readline error: " + e1);
				}
			}
			
			try {
				StringTokenizer t = new StringTokenizer(command, " ");
				type = t.nextToken();
				
				if (type.equals("ADD")) {
					songName = t.nextToken();
				    url = new URL(t.nextToken());
					System.out.println("Command: ADD " + songName + " URL= " + url);
				} else if (type.equals("DELETE")) {
					songName = t.nextToken();
					System.out.println("Command: DELETE " + songName);
				} else if (type.equals("EDIT")) {
				    songName = t.nextToken();
				    url = new URL(t.nextToken());
					System.out.println("Command: EDIT " + songName + " URL= " + url);
				}
			} catch (Exception e) {
				System.out.println("Invalid command: " + e.toString());
				// jump to the next iteration
				continue;
			}
			
			// send VOTE-REQ to processes
			for (int i = 0; i < config.numProcesses; i ++) {
				if (i == coordinatorNum) {
					continue;
				}
				controller.sendMsg(i, parse.addSenderNum("VOTE-REQ " + command));
				
				// write to DT log
				dtLog.writeDTLog("start-3PC " + command);
			}
			
			// receive vote from processes
			System.out.println("Receiving votes....");
			
			List<String> objs = new ArrayList<String>();
			List<String> read = new ArrayList<String>();
			int numRead = 0;
			
			start_time = System.currentTimeMillis();
			
			while (numRead < config.numProcesses - 1) {
				while ((objs = controller.getReceivedMsgs()).size() == 0) {
					
					// test whether time-out
					long duration = System.currentTimeMillis() - start_time;
					
					if (duration > time_out_short) {
						
						coordinatorAbort();
						
						continue coorditator_iteration;
					}
					
				}
				for (int i = 0; i < objs.size(); i ++) {
					System.out.println("Votes received from p" + parse.getSenderNum(objs.get(i)) + 
							": " + parse.getSenderMsg(objs.get(i)));
					read.add(parse.getSenderMsg(objs.get(i)));
					
					// mark the participants that voted YES
					pYes[parse.getSenderNum(objs.get(i))] = (parse.getSenderMsg(objs.get(i)).equals("YES"));
					
					numRead ++;
				}
				
			}
			
			boolean precommit = true;
			
			for (String s : read) {
				if (s.equals("NO")) {
					precommit = false;
				}
			}
			

			// before sending precommit
			if (DEBUG5) {
				while (true) {
					try {

						System.out.println("make a failure.....");
						String vote = br.readLine();
						break;
					} catch (IOException e1) {
						System.out.println("Readline error: " + e1);
					}
				}
			}
			
			if (DEBUG) {
				while (true) {
					try {
						command = br.readLine();
						break;
					} catch (IOException e1) {
						System.out.println("Readline error: " + e1);
					}
				}
			}
			
			// decide PRECOMMIT or ABORT and send the result to processes
			if (precommit) {
				
				System.out.println("PRECOMMIT");
				dtLog.writeDTLog("precommit " + type + " " + songName + " " + url);
				
				if (DEBUG2) {
				System.out.println("wait for the failure notificaion");
				
					while (true) {
						try {
							command = br.readLine();
							break;
						} catch (IOException e1) {
							System.out.println("Readline error: " + e1);
						}
					}
				}

				// precommit
				if (DEBUG3) { 
					while (true) {
						try {
							command = br.readLine();
							break;
						} catch (IOException e1) {
							System.out.println("Readline error: " + e1);
						}
					}
				}
				
				for (int i = 0; i < config.numProcesses; i ++) {
					if (i == coordinatorNum) {
						continue;
					}
					controller.sendMsg(i, parse.addSenderNum("PRECOMMIT"));
				}
			} else {
				coordinatorAbort();
				dtLog.clearDTLog();
				continue coorditator_iteration;
			}
			
			// receive ACKs from processes
			System.out.println("Receiving ACKs....");
			
			objs = new ArrayList<String>();
			read = new ArrayList<String>();
			numRead = 0;
			
			start_time = System.currentTimeMillis();
			
			outerloop:
			while (numRead < config.numProcesses - 1) {
				while ((objs = controller.getReceivedMsgs()).size() == 0) {
					
					long duration = System.currentTimeMillis() - start_time;
					
					if (duration > time_out_short) {				
						break outerloop;
					}
					
				}
				for (int i = 0; i < objs.size(); i ++) {
					if (parse.getSenderMsg(objs.get(i)).contains("ASK FOR FINAL DETERMINATION")) {

						controller.sendMsg(parse.getSenderNum(objs.get(i)),	parse.addSenderNum("COMMIT"));

					} else if (parse.getSenderMsg(objs.get(i)).contains("ACK")) {
						System.out.println("ACK received from p"
								+ parse.getSenderNum(objs.get(i)) + ": "
								+ parse.getSenderMsg(objs.get(i)));
						//read.add(parse.getSenderMsg(objs.get(i)));
						numRead++;
					}
				}
				
			}
			
			
			// decide COMMIT and send the result to processes
			System.out.println("COMMIT");

			// write to DT log
			dtLog.writeDTLog("commit " + type + " " + songName + " " + url);

			if (DEBUG4) { // test commit
				while (true) {
					try {
						command = br.readLine();
						break;
					} catch (IOException e1) {
						System.out.println("Readline error: " + e1);
					}
				}
			}

			for (int i = 0; i < config.numProcesses; i++) {
				if (i == coordinatorNum) {
					continue;
				}
				
				if (DEBUG) {
					while (true) {
						try {
							command = br.readLine();
							break;
						} catch (IOException e1) {
							System.out.println("Readline error: " + e1);
						}
					}
				}
				
				controller.sendMsg(i, parse.addSenderNum("COMMIT"));
			}
			
			commit(type, songName, url);
			dtLog.clearDTLog();
			// decide = COMMIT

		}
	}
	
	private static void coordinatorAbort() {
		// decide = ABORT
		System.out.println("ABORT");
		
		// write to DT log
		dtLog.writeDTLog("abort");
		
		for (int i = 0; i < config.numProcesses; i ++) {
			//if ((i == coordinatorNum) || (! pYes[i])) {
			if ((i == coordinatorNum)) {
				continue;
			}
			
			System.out.println("Send ABORT to p" + i);
			controller.sendMsg(i, parse.addSenderNum("ABORT"));
		}
	}

	
	private static void runProcess() {
		
		Reader r = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(r);
		
		String command;
		type = null;
		songName = null;
		url = null;
		
		long start_time;
		
		System.out.println("This is the processor p" + config.procNum);
		
		process_recovery();
		
		state = "Aborted";
		
		coorditator_iteration:	
		while (true) {
			
			resetCoordinatorNum();
			
			Arrays.fill(pFailure, Boolean.FALSE);
			
			// wait for the VOTE-REQ from the coordinator
			List<String> objs;
			
			start_time = System.currentTimeMillis();
			
			while (true) {

				while ((objs = controller.getReceivedMsgs()).size() == 0) {

					// test whether time-out
					long duration = System.currentTimeMillis() - start_time;

					if (duration > time_out_short) {

						// state = "Aborted";

						// write to DT log
						dtLog.writeDTLog("abort");
						dtLog.clearDTLog();

						// ABORT and jump to the next iteration
						System.out.println("Abort");
						sendDetermination("ABORT");
						
						continue coorditator_iteration;
					}

				}

				command = parse.getSenderMsg(objs.get(0));

				System.out.println("Received VOTE-REQ from p"
						+ parse.getSenderNum(objs.get(0)) + ": " + command);

				try {
					StringTokenizer t = new StringTokenizer(command, " ");

					String vote_req = t.nextToken();

					if (vote_req.equals("STATE-REQ")) {
						// here is to avoid after voting NO, the coordinator
						// dies
						// and other participants start election and termination
						// protocal

						// STATE-REQ received
						System.out.println("STATE-REQ received from p"
								+ parse.getSenderNum(objs.get(0)) + ": "
								+ parse.getSenderMsg(objs.get(0)));

						// send state (abort) to coordinator;
						System.out.println("Send " + state + " to coordinator");
						controller.sendMsg(parse.getSenderNum(objs.get(0)),
								parse.addSenderNum(state));
						
					} else if(command.contains("ASK FOR FINAL DETERMINATION")){
						
        				pFailure[parse.getSenderNum(objs.get(0))] = true;
        				controller.sendMsg(parse.getSenderNum(objs.get(0)), parse.addSenderNum("GETIT"));
        				System.out.println("Send GETIT");
        				continue;
        				
        			} else if (!vote_req.equals("VOTE-REQ")) {
						System.out.println("Invalid VOTE-REQ: " + command);
						continue;
					}

					state = "Aborted";

					type = t.nextToken();

					if (type.equals("ADD")) {
						songName = t.nextToken();
						url = new URL(t.nextToken());
						System.out.println("Command: ADD " + songName
								+ " URL= " + url);
					} else if (type.equals("DELETE")) {
						songName = t.nextToken();
						System.out.println("Command: DELETE " + songName);
					} else if (type.equals("EDIT")) {
						songName = t.nextToken();
						url = new URL(t.nextToken());
						System.out.println("Command: EDIT " + songName
								+ " URL= " + url);
					}
				} catch (Exception e) {
					System.out.println("Invalid VOTE-REQ: " + e.toString());
					continue;
				}

				dtLog.writeDTLog("command " + command);
				break;
			}
			
			String vote;
			
			if (DEBUG) {
				// read the vote through console line
				System.out.println("Please enter a vote: YES/NO");
				vote = null;
				while (true) {
					try {
						vote = br.readLine();
					} catch (IOException e) {
						e.printStackTrace();
					}

					if (!(vote.equals("YES") || vote.equals("NO"))) {
						System.out
								.println("Invalid input. Please enter a vote: YES/NO");
					} else {
						break;
					}
				}
			} else {
			
				vote = "YES";
			
			}
			
			
			if (vote.equals("NO")) {
				// send NO to the coordinator
				System.out.println("Send NO to coordinator");
				controller.sendMsg(coordinatorNum, parse.addSenderNum("NO"));
				
				state = "Aborted";
				
				// write to DT log
				dtLog.writeDTLog("abort");
				
				// ABORT and jump to the next iteration
				System.out.println("Abort");
				
				sendDetermination("ABORT");
				dtLog.clearDTLog();
				
				// disable continue to avoid receiving STATE-REQ
				//continue;
			} else {
			
				state = "Uncertain";

				// write to DT log
				dtLog.writeDTLog("yes");

				// else, vote.equals("YES") (Because not continued)
				// send NO to the coordinator
				System.out.println("Send YES to coordinator");
				controller.sendMsg(coordinatorNum, parse.addSenderNum("YES"));
			
			}
			
			
			// coordinator failure before sending precommit
			if (DEBUG5) {
				while (true) {
					try {

						System.out
								.println("wait for failure notification.....");
						command = br.readLine();
						break;
					} catch (IOException e1) {
						System.out.println("Readline error: " + e1);
					}
				}
			}
			// waiting for PRECOMMIT/ABORT
			System.out.println("Waiting for coordinator's PRECOMMIT/ABORT");
			
			if (DEBUG2) {
				if(config.procNum == 1){	
					System.out.println("make a failure");
				    while (true) {
					   try {
						   vote = br.readLine();
					   } catch (IOException e) {
						   e.printStackTrace();
					    }
				    }
				}
				
            
				if(config.procNum == 2){	
					// read the vote through console line
					System.out.println("wait for the failure notificaion");
				    while (true) {
					   try {
						   vote = br.readLine();
					   } catch (IOException e) {
						   e.printStackTrace();
					    }
					   
					   if(vote != null){
						   break;
					   }
				    }
				}
			}
			
			start_time = System.currentTimeMillis();
			
			while (true) {
				while ((objs = controller.getReceivedMsgs()).size() == 0
						|| (parse.getSenderNum(objs.get(0)) != coordinatorNum)) {

					// check whether it's from current coordinator
					// to avoid the case that other participant enters election
					if (objs.size() != 0) {
						if (parse.getSenderMsg(objs.get(0)).equals("STATE-REQ")) {
							controller.sendMsg(parse.getSenderNum(objs.get(0)),
									parse.addSenderNum("WAIT"));
						}
						continue;
					}

					// test whether time-out
					long duration = System.currentTimeMillis() - start_time;

					if (duration > time_out_long) {

						processorTimeoutRunElection();

						continue coorditator_iteration;
					}

				}

				command = parse.getSenderMsg(objs.get(0));

				System.out.println("Received from coordinator: " + command);

				if (command.equals("ABORT")) {
					// ABORT
					System.out.println("Abort");

					state = "Aborted";

					// write to DT log
					dtLog.writeDTLog("abort");
					sendDetermination("ABORT");
					dtLog.clearDTLog();

					continue coorditator_iteration;
					
				} else if (vote.contains("ASK FOR FINAL DETERMINATION")){

					pFailure[parse.getSenderNum("ASK FOR FINAL DETERMINATION")] = true;
					controller
							.sendMsg(parse.getSenderNum(objs.get(0)), "GETIT");
					System.out.println("Send GETIT");
					continue;

				} else if (!command.equals("PRECOMMIT")) {
					// ABORT
					System.out.println("Wrong message from coordinator: " + command);
					continue;
				}
				
				break;
			}
			
			state = "Committable";
			
			// otherwise must received PRECOMMIT, since not continued
			// send ACK to the coordinator
			System.out.println("Send ACK to coordinator");
			controller.sendMsg(coordinatorNum, parse.addSenderNum("ACK"));
			
			// waiting for PRECOMMIT/ABORT
			System.out.println("Waiting for coordinator's COMMIT");
			
			start_time = System.currentTimeMillis();
			
			while (true) {
				while (((objs = controller.getReceivedMsgs()).size() == 0)
						|| (parse.getSenderNum(objs.get(0)) != coordinatorNum)) {

					// check whether it's from current coordinator
					// to avoid the case that other participant enters election
					if (objs.size() != 0) {
						if (parse.getSenderMsg(objs.get(0)).equals("STATE-REQ")) {
							controller.sendMsg(parse.getSenderNum(objs.get(0)),
									parse.addSenderNum("WAIT"));
						}
						continue;
					}

					// test whether time-out
					long duration = System.currentTimeMillis() - start_time;

					if (duration > time_out_long) {

						processorTimeoutRunElection();

						continue coorditator_iteration;
					}

				}
				command = parse.getSenderMsg(objs.get(0));

				System.out.println("Received from coordinator: " + command);

				if (command.equals("ABORT")) {

					// decide = ABORT
					state = "Aborted";

					// write to DT log
					dtLog.writeDTLog("abort");
					System.out.println("Abort");
					sendDetermination("ABORT");

					dtLog.clearDTLog();
					continue coorditator_iteration;

				} else if (command.equals("COMMIT")) {

					// decide = COMMIT
					state = "Committed";

					// write to DT log
					dtLog.writeDTLog("commit");
					sendDetermination("COMMIT");
					commit(type, songName, url);

					dtLog.clearDTLog();
					continue coorditator_iteration;

				} else if (command.contains("ASK FOR FINAL DETERMINATION")) {

					System.out.println(parse.getSenderNum(objs.get(0)));
					pFailure[parse.getSenderNum(objs.get(0))] = true;

					controller
							.sendMsg(parse.getSenderNum(objs.get(0)), "GETIT");
					System.out.println("Send GETIT");
				}
			}
		}
	}

	private static void processorTimeoutRunElection() {
		
		if (election()) {
			System.out.println("Elected");
			coordinatorTerminate();
		} else {
			System.out.println("Not elected");
			processTerminate();
		}
	}

	
	private static boolean election() {
		
		// run election protocol
		System.out.println("Run election protocol....");
		
		coordinatorNum = (coordinatorNum + 1) % config.numProcesses;
		
		return ( coordinatorNum == config.procNum);
		
	}
	
	
	private static void coordinatorTerminate() {

		// send STATE-REQ to processes
		for (int i = 0; i < config.numProcesses; i++) {
			if (i == coordinatorNum) {
				continue;
			}
			controller.sendMsg(i, parse.addSenderNum("STATE-REQ "));
		}

		// receive vote from processes
		System.out.println("Receiving states....");

		List<String> objs = new ArrayList<String>();
		List<String> read = new ArrayList<String>();
		int numRead = 0;

		long start_time = System.currentTimeMillis();

		boolean uncertain = (state.equals("Uncertain"));
		boolean[] pUncertain = new boolean[config.numProcesses];
		
		// initiate pYes with false
		Arrays.fill(pUncertain, Boolean.FALSE);
		
		outerloop:
		while (numRead < config.numProcesses - 1) {
			while ((objs = controller.getReceivedMsgs()).size() == 0) {

				// test whether time-out
				long duration = System.currentTimeMillis() - start_time;

				// if time-out, skip
				if (duration > time_out_newCoordinator) {
					break outerloop;
				}

			}
			for (int i = 0; i < objs.size(); i++) {

				if (parse.getSenderMsg(objs.get(i)).equals(
						"ASK FOR FINAL DETERMINATION")) {

					pFailure[parse.getSenderNum(objs.get(i))] = true;
					controller.sendMsg(parse.getSenderNum(objs.get(i)),
							parse.addSenderNum("GETIT"));
					System.out.println("Send GETIT");
				} else {

					System.out.println("State received from p"
							+ parse.getSenderNum(objs.get(i)) + ": "
							+ parse.getSenderMsg(objs.get(i)));

					// mark the participants that reported Uncertain state
					pUncertain[parse.getSenderNum(objs.get(i))] = (parse
							.getSenderMsg(objs.get(i)).equals("Uncertain"));

					// check if there is a participant hasn't started election
					if (parse.getSenderMsg(objs.get(i)).equals("WAIT")) {
						// if so, it requests a wait, send again STATE-REQ

						controller.sendMsg(parse.getSenderNum(objs.get(i)),
								parse.addSenderNum("STATE-REQ "));
					} else {
						read.add(parse.getSenderMsg(objs.get(i)));
						numRead++;
					}

				}
			}

		}
			
		for (String s : read) {
			if (s.equals("Aborted") || state.equals("Aborted")) {
				// TR1: if some process is Aborted
				System.out.println("TR1: some process is Aborted, ABORT");
				
				if (! dtLog.readDTLogLastLine().equals("abort")) {
					dtLog.writeDTLog("abort");
				}
				
				// send ABORT to processes
				for (int i = 0; i < config.numProcesses; i++) {
					if (i == coordinatorNum) {
						continue;
					}
					controller.sendMsg(i, parse.addSenderNum("ABORT"));
				}
				sendDetermination("ABORT");
				dtLog.clearDTLog();
				return;
				
			} else if (s.equals("Committed") || state.equals("Committed")) {
				// TR2: if some process is Committed
				System.out.println("TR2: some process is Committed, COMMIT");
				
				if (! dtLog.readDTLogLastLine().equals("commit")) {
					dtLog.writeDTLog("commit");
				}
				
				// send COMMIT to processes
				for (int i = 0; i < config.numProcesses; i++) {
					if (i == coordinatorNum) {
						continue;
					}
					controller.sendMsg(i, parse.addSenderNum("COMMIT"));
				}
				sendDetermination("COMMIT");
				dtLog.clearDTLog();
				return;
				
			} else if (! s.equals("Uncertain")) {
				uncertain = false;
			}
			
		}
		
		// TR3: if all processes are Uncertain
		if (uncertain) {
			
			System.out.println("TR3: all processes are Uncertain, ABORT");
			
			dtLog.writeDTLog("abort");
			
			// send ABORT to processes
			for (int i = 0; i < config.numProcesses; i++) {
				if (i == coordinatorNum) {
					continue;
				}
				controller.sendMsg(i, parse.addSenderNum("ABORT"));
			}
			sendDetermination("ABORT");
			dtLog.clearDTLog();
			return;
		}
		
		int numUncertain = 0;
		
		// TR4: if some process is Committable but none is Committed
		// send PRECOMMIT to processes
		System.out.println("TR4: some process is Committable but none is Committed, PRECOMMIT");
		
		for (int i = 0; i < config.numProcesses; i++) {
			if (i == coordinatorNum  || (! pUncertain[i])) {
				continue;
			}
			controller.sendMsg(i, parse.addSenderNum("PRECOMMIT"));
			numUncertain ++;
		}
		
		// wait for ACK
		// receive ACKs from processes
		System.out.println("Receiving ACKs....");

		objs = new ArrayList<String>();
		read = new ArrayList<String>();
		numRead = 0;

		start_time = System.currentTimeMillis();

		outerloop:
		while (numRead < numUncertain) {
			while ((objs = controller.getReceivedMsgs()).size() == 0) {

				long duration = System.currentTimeMillis() - start_time;

				if (duration > time_out_short) {
					break outerloop;
				}

			}
			for (int i = 0; i < objs.size(); i++) {

				if (parse.getSenderMsg(objs.get(i)).equals(
						"ASK FOR FINAL DETERMINATION")) {

					pFailure[parse.getSenderNum(objs.get(i))] = true;
					controller.sendMsg(parse.getSenderNum(objs.get(i)),
							parse.addSenderNum("GETIT"));
					System.out.println("Send GETIT");
				} else {

					System.out.println("ACK received from p"
							+ parse.getSenderNum(objs.get(i)) + ": "
							+ parse.getSenderMsg(objs.get(i)));
					read.add(parse.getSenderMsg(objs.get(i)));
					numRead++;
				}
			}
		}
		
		// decide COMMIT and send the result to processes
		System.out.println("COMMIT");

		// write to DT log
		dtLog.writeDTLog("commit " + type + " " + songName + " " + url);
		
		// send COMMIT to all the participants
		for (int i = 0; i < config.numProcesses; i++) {
			if (i == coordinatorNum) {
				continue;
			}
			controller.sendMsg(i, parse.addSenderNum("COMMIT"));
		}
		sendDetermination("COMMIT");
		commit(type, songName, url);
		dtLog.clearDTLog();
		// decide = COMMIT

	}

	private static void processTerminate() {
		
		// waiting for STATE-REQ from processes
		System.out.println("Waiting for state-req....");
		
		// send WAIT to avoid starting election later than the coordinator
		controller.sendMsg(coordinatorNum, parse.addSenderNum("WAIT"));
		
		// wait for the STATE-REQ from the coordinator
		List<String> objs;

		long start_time = System.currentTimeMillis();

		loop1:
		while(true){
		
			while ((objs = controller.getReceivedMsgs()).size() == 0) {

				// test whether time-out
				long duration = System.currentTimeMillis() - start_time;

				if (duration > time_out_newProcess) {
					processorTimeoutRunElection();
					return;
				}
			}
		
			if (parse.getSenderMsg(objs.get(0)).equals(
					"ASK FOR FINAL DETERMINATION")) {

				pFailure[parse.getSenderNum(objs.get(0))] = true;
				controller.sendMsg(parse.getSenderNum(objs.get(0)),
						parse.addSenderNum("GETIT"));
				System.out.println("Send GETIT");
				
			}else{
			
				// STATE-REQ received
				System.out.println("STATE-REQ received from new coordinator p" + 
						parse.getSenderNum(objs.get(0)) + ": " + parse.getSenderMsg(objs.get(0)));
		
				// send state to coordinator;
				System.out.println("Send " + state + " to coordinator");
				controller.sendMsg(coordinatorNum, parse.addSenderNum(state));
		
				// waiting for response from the coordinator
				System.out.println("Waiting for coordinator's response");
				break loop1;
			}
		}
		
		start_time = System.currentTimeMillis();
		String command = null;
		loop2:
		while(true){
		
			while ((objs = controller.getReceivedMsgs()).size() == 0) {
			
				// test whether time-out
				long duration = System.currentTimeMillis() - start_time;
			
				if (duration > time_out_newProcess) {
				
					processorTimeoutRunElection();
				
					return;
				
				}
			
			}
		
			command = parse.getSenderMsg(objs.get(0));
		
			if (parse.getSenderMsg(objs.get(0)).equals(
					"ASK FOR FINAL DETERMINATION")) {

				pFailure[parse.getSenderNum(objs.get(0))] = true;
				controller.sendMsg(parse.getSenderNum(objs.get(0)),
						parse.addSenderNum("GETIT"));
				System.out.println("Send GETIT");
				
			}else{ 
		
		
				System.out.println("Received from coordinator: " + command);
		
				if (command.equals("ABORT")) {
					// ABORT
					System.out.println("Abort");
			
					state = "Aborted";
			
					// write to DT log
					if ((dtLog.readDTLogLastLine() == null) || (! dtLog.readDTLogLastLine().equals("abort"))) {
						dtLog.writeDTLog("abort");
						sendDetermination("ABORT");
					}
					dtLog.clearDTLog();
					return;
				} else if (command.equals("COMMIT")) {
					// COMMIT
					System.out.println("Commit");
			
					state = "Commited";
			
					// write to DT log
					if ((dtLog.readDTLogLastLine() == null) || (! dtLog.readDTLogLastLine().equals("Commit"))) {
						dtLog.writeDTLog("Commit");
						sendDetermination("COMMIT");
						commit(type, songName, url);
					}
					dtLog.clearDTLog();
					return;
				}
			
				state = "Committable";
				break loop2;
			}
		
		}
			// send ACK to the coordinator
		System.out.println("Send ACK to coordinator");
		controller.sendMsg(coordinatorNum, parse.addSenderNum("ACK"));
		
		// waiting for PRECOMMIT/ABORT
		System.out.println("Waiting for coordinator's COMMIT");
		
		start_time = System.currentTimeMillis();
		
		
		while(true){
			while ((objs = controller.getReceivedMsgs()).size() == 0) {
			
				// test whether time-out
				long duration = System.currentTimeMillis() - start_time;
			
				if (duration > time_out_long) {
				
					processorTimeoutRunElection();
				
					return;
				}
			}
			
			command = parse.getSenderMsg(objs.get(0));
			
			if (parse.getSenderMsg(objs.get(0)).equals(
					"ASK FOR FINAL DETERMINATION")) {

				pFailure[parse.getSenderNum(objs.get(0))] = true;
				controller.sendMsg(parse.getSenderNum(objs.get(0)),
						parse.addSenderNum("GETIT"));
				System.out.println("Send GETIT");
				
			}else{ 
				System.out.println("Received from coordinator: " + command);
		
				state = "Committed";
		
				// write to DT log
				dtLog.writeDTLog("commit");
				commit(type, songName, url);
				sendDetermination("COMMIT");
				// decide = COMMIT
				dtLog.clearDTLog();
				return;
			}
		}
	}
	
	private static void sendDetermination(String determin) {
		// decide = ABORT
		System.out.println("Sending determination to the failed process");
		
		// write to DT log
		
		for (int i = 0; i < config.numProcesses; i ++) {
			System.out.println(pFailure[i]);
			//if ((i == coordinatorNum) || (!pFailure[i])) {
			if (!pFailure[i]) {
				continue;
			}
			
			System.out.println("Send "+determin+" to p" + i);
			controller.sendMsg(i, parse.addSenderNum(determin));
		}
	}
	
	private static void process_recovery() {
		
		if(dtLog.existFile()){
			
			String determin = dtLog.readDTLogLastNumLine(1);
			System.out.println(dtLog.getFileName()+" "+"determin:"+determin);
			
			if(determin == null){
				
				System.out.println("null:recovery: failure before voting, abort");
				return;
				
			}else if(determin == ""){
				
				System.out.println("empty:recovery: failure before voting, abort");
				return;
				
			}else if (determin.contains("yes") || (determin.contains("start-3PC") && !determin.contains("precommit"))){ 
				
				System.out.println("recovery: failure after voting and before decision");
				
				System.out.println("Sending ask for determination");
				
				List<String> objs2 = new ArrayList<String>();
				
				out:
				while(true){
					while((objs2 = controller.getReceivedMsgs()).size() == 0){
					
						for(int i=0; i < config.numProcesses; i++){

							controller.sendMsg(i, parse.addSenderNum("ASK FOR FINAL DETERMINATION"));
						}
					}
					
					for(int i =0; i < objs2.size(); i++){
						if(objs2.get(i).contains("GETIT")){		
							System.out.println("be wared of ");
							break out;
						}
					}
				}
			
				List<String> objs = new ArrayList<String>();
				
				System.out.println("wait for detemin");
				while(true){
					while ((objs = controller.getReceivedMsgs()).size() == 0) {}
				
					if (objs.get(0).contains("COMMIT")){
					
						System.out.println("detemin: commit");
						String command = dtLog.readDTLogLastNumLine(2);
						String type = null;
						String songName = null;
						URL url = null;
					
						try {
							StringTokenizer t = new StringTokenizer(command, " ");
							type = t.nextToken();
							type = t.nextToken();
							if (type.equals("ADD")) {
								songName = t.nextToken();
								url = new URL(t.nextToken());
								System.out.println("Command: ADD " + songName + " URL= " + url);
							} else if (type.equals("DELETE")) {
								songName = t.nextToken();
								System.out.println("Command: DELETE " + songName);
							} else if (type.equals("EDIT")) {
								songName = t.nextToken();
								url = new URL(t.nextToken());
								System.out.println("Command: EDIT " + songName + " URL= " + url);
							}
						} catch (Exception e) {
							System.out.println("Invalid command: " + e.toString());
							// jump to the next iteration
						}
					
						commit(type, songName, url);
						System.out.println("recovery: failure after voting and before decision, commit");
						dtLog.clearDTLog();
						return;
					
					}else if (objs.get(0).contains("ABORT")){
						System.out.println("recovery: failure after voting and before decision, abort");
						dtLog.clearDTLog();
						return;
					}
				}
			
			}else if (determin.contains("commit")){
				
				System.out.println("recovery: failure after determine 'COMMIT' ");
				
					String command = dtLog.readDTLogLastNumLine(3);
				System.out.println("command: " + command);
					String type = null;
					String songName = null;
					URL url = null;
					
					try {
						StringTokenizer t = new StringTokenizer(command, " ");
						type = t.nextToken();
						type = t.nextToken();
						if (type.equals("ADD")) {
							songName = t.nextToken();
						    url = new URL(t.nextToken());
							System.out.println("Command: ADD " + songName + " URL= " + url);
						} else if (type.equals("DELETE")) {
							songName = t.nextToken();
							System.out.println("Command: DELETE " + songName);
						} else if (type.equals("EDIT")) {
						    songName = t.nextToken();
						    url = new URL(t.nextToken());
							System.out.println("Command: EDIT " + songName + " URL= " + url);
						}
					} catch (Exception e) {
						System.out.println("Invalid command: " + e.toString());
						// jump to the next iteration
					}
					
					commit(type, songName, url);
					System.out.println("recovery command successfully");
					dtLog.clearDTLog();
					return;
					
			}else if (determin.contains("abort")){
				
				System.out.println("recovery: failure after determine 'ABORT' ");
				dtLog.clearDTLog();
				return;
				
			}else if (determin.contains("precommit")){
				
				System.out.println("recovery: failure after determine 'precommit'");
				
					String command = dtLog.readDTLogLastNumLine(1);
				System.out.println("command: " + command);
					String type = null;
					String songName = null;
					URL url = null;
					
					try {
						StringTokenizer t = new StringTokenizer(command, " ");
						type = t.nextToken();
						type = t.nextToken();
						if (type.equals("ADD")) {
							songName = t.nextToken();
						    url = new URL(t.nextToken());
							System.out.println("Command: ADD " + songName + " URL= " + url);
						} else if (type.equals("DELETE")) {
							songName = t.nextToken();
							System.out.println("Command: DELETE " + songName);
						} else if (type.equals("EDIT")) {
						    songName = t.nextToken();
						    url = new URL(t.nextToken());
							System.out.println("Command: EDIT " + songName + " URL= " + url);
						}
					} catch (Exception e) {
						System.out.println("Invalid command: " + e.toString());
						// jump to the next iteration
					}
					
					commit(type, songName, url);
					System.out.println("recovery command successfully");
					dtLog.clearDTLog();
					return;		
			}
			
		}else{
			
			System.out.println("This is the first iteration");
			return;
		}
		return;
		
	}
}