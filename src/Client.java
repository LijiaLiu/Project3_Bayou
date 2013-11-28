

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class Client extends Process {

	Env env;
	ProcessId me;
	ProcessId[] replicas;
	Integer num;
	private int req_id;
	private int req_id_roc;
	
	int waitForResponse = 0;
	int waitForROCResponse = 0;
	ProcessId lastRespondedReplica;
	Object syncObj = new Object();
	ProcessId requester;
	List<ProcessId> responded_replicas = new LinkedList<ProcessId>();
	CountDownLatch latch = new CountDownLatch(1);
	CountDownLatch latch_finish = new CountDownLatch(1);
	
	public Client(Env env, ProcessId me, ProcessId[] replicas, int num) {
		this.env = env;
		this.me = me;
		this.replicas = replicas;
		this.num = num;
		
		this.req_id = 0;
		this.req_id_roc = 0;
		this.requester = new ProcessId(me + "requester");
		
		env.addProc(me, this);
	}

	void request(String op){
		env.sendMessage(requester, new ClientRequestMessage(me, new Command(me, req_id, num.toString() + " " + op)));
		req_id ++;
	}
	
	void requestROC(String op) {
		env.sendMessage(requester, new ROCClientRequestMessage(me, new Command(me, req_id_roc, num.toString() + " " + op)));
		req_id_roc ++;
	}

	@Override
	void body() {
		ClientRequester clientRequester = new ClientRequester(env, requester, me, replicas, 
				syncObj, responded_replicas, latch, latch_finish);
		
		System.out.println("Here I am: " + me);
		for (;;) {
			PaxosMessage msg = getNextMessage();

			if (msg instanceof ClientMessage) {
				ClientMessage m = (ClientMessage) msg;
				request(m.op);
				
				responded_replicas.clear();
				
			} else if (msg instanceof RespondMessage) {
				RespondMessage m = (RespondMessage) msg;
				if (waitForResponse == m.command.req_id) {
					System.out.println("Get response: " + m.command + m.result);
					synchronized(syncObj) { syncObj.notify(); }
					waitForResponse ++;
					lastRespondedReplica = m.src;
				}
				if (m.src == lastRespondedReplica) {
					responded_replicas.add(m.src);
				}
				
			} else if (msg instanceof ROCClientMessage) {
				ROCClientMessage m = (ROCClientMessage) msg;
				requestROC(m.op);
				
			} else if (msg instanceof ROCRespondMessage) {
				ROCRespondMessage m = (ROCRespondMessage) msg;
				if (waitForROCResponse == m.command.req_id) {
					System.out.println("Get ROC response: " + m.command	+ m.result);
					latch.countDown();
					try {
						latch_finish.await();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					synchronized (syncObj) { syncObj.notify(); }
					waitForROCResponse++;					
				}
				
			} else {
				System.err.println("Replica: unknown msg type");
			}
		}
		
	}

}
