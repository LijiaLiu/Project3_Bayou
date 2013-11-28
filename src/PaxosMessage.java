

import java.util.*;

public class PaxosMessage {
	ProcessId src;
}

class P1aMessage extends PaxosMessage {
	BallotNumber ballot_number;
	ProcessId newLeader;
//	P1aMessage(ProcessId src, BallotNumber ballot_number){
//		this.src = src; this.ballot_number = ballot_number;
//	}
	P1aMessage(ProcessId src, BallotNumber ballot_number, ProcessId newLeader){
		this.src = src; this.ballot_number = ballot_number; this.newLeader = newLeader;
	}
}
class P1bMessage extends PaxosMessage {
	BallotNumber ballot_number; Set<PValue> accepted;
	ProcessId newLeader;
//	P1bMessage(ProcessId src, BallotNumber ballot_number, Set<PValue> accepted) {
//		this.src = src; this.ballot_number = ballot_number; this.accepted = accepted;
//	}
	P1bMessage(ProcessId src, BallotNumber ballot_number, Set<PValue> accepted, ProcessId newLeader) {
		this.src = src; this.ballot_number = ballot_number; this.accepted = accepted; this.newLeader = newLeader;
	}
}
class P2aMessage extends PaxosMessage {
	BallotNumber ballot_number; int slot_number; Command command;
	ProcessId newLeader;
//	P2aMessage(ProcessId src, BallotNumber ballot_number, int slot_number, Command command){
//		this.src = src; this.ballot_number = ballot_number;
//		this.slot_number = slot_number; this.command = command;
//	}
	P2aMessage(ProcessId src, BallotNumber ballot_number, int slot_number, Command command, ProcessId newLeader){
		this.src = src; this.ballot_number = ballot_number;
		this.slot_number = slot_number; this.command = command; this.newLeader = newLeader;
	}
}
class P2bMessage extends PaxosMessage {
	BallotNumber ballot_number; int slot_number;
	ProcessId newLeader;
//	P2bMessage(ProcessId src, BallotNumber ballot_number, int slot_number){
//		this.src = src; this.ballot_number = ballot_number; this.slot_number = slot_number;
//	}
	P2bMessage(ProcessId src, BallotNumber ballot_number, int slot_number, ProcessId newLeader){
		this.src = src; this.ballot_number = ballot_number; this.slot_number = slot_number; this.newLeader = newLeader;
	}
}
class PreemptedMessage extends PaxosMessage {
	BallotNumber ballot_number;
	ProcessId newLeader;
//	PreemptedMessage(ProcessId src, BallotNumber ballot_number){
//		this.src = src; this.ballot_number = ballot_number;
//	}
	PreemptedMessage(ProcessId src, BallotNumber ballot_number, ProcessId newLeader){
		this.src = src; this.ballot_number = ballot_number; this.newLeader = newLeader;
	}
}
class AdoptedMessage extends PaxosMessage {
	BallotNumber ballot_number; Set<PValue> accepted;
	AdoptedMessage(ProcessId src, BallotNumber ballot_number, Set<PValue> accepted){
		this.src = src; this.ballot_number = ballot_number; this.accepted = accepted;
}	}
class DecisionMessage extends PaxosMessage {
	ProcessId src; int slot_number; Command command;
	public DecisionMessage(ProcessId src, int slot_number, Command command){
		this.src = src; this.slot_number = slot_number; this.command = command;
}	}
class RequestMessage extends PaxosMessage {
	Command command;
	public RequestMessage(ProcessId src, Command command){
		this.src = src; this.command = command;
}	}
class ProposeMessage extends PaxosMessage {
	int slot_number; Command command;
	public ProposeMessage(ProcessId src, int slot_number, Command command){
		this.src = src; this.slot_number = slot_number; this.command = command;
}	}

class RespondMessage extends PaxosMessage {
	Command command;
	String result;
	public RespondMessage(ProcessId src, Command command, String result){
		this.src = src; this.command = command; this.result = result;
}	}

class PingRequestMessage extends PaxosMessage {
	ProcessId leader;
	public PingRequestMessage(ProcessId src, ProcessId leader){
		this.src = src;
		this.leader = leader;
}	}
class PingRespondMessage extends PaxosMessage {
	public PingRespondMessage(ProcessId src){
		this.src = src;
}	}

class ClientMessage extends PaxosMessage {
	String op;
	public ClientMessage(String op){
		this.op = op;
}	}

class ClientRequestMessage extends PaxosMessage {
	Command command;
	public ClientRequestMessage(ProcessId src, Command command){
		this.src = src; this.command = command;
}	}

class ROCClientMessage extends PaxosMessage {
	String op;
	public ROCClientMessage(String op){
		this.op = op;
}	}

class ROCClientRequestMessage extends PaxosMessage {
	Command command;
	public ROCClientRequestMessage(ProcessId src, Command command){
		this.src = src; this.command = command;
}	}

class ROCRequestMessage extends PaxosMessage {
	Command command;
	public ROCRequestMessage(ProcessId src, Command command){
		this.src = src; this.command = command;
}	}

class ROCRespondMessage extends PaxosMessage {
	Command command;
	String result;
	public ROCRespondMessage(ProcessId src, Command command, String result){
		this.src = src; this.command = command; this.result = result;
}	}