package blockchain;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class NodeAgentTP1 extends Agent{
	
	private static final long serialVersionUID = 1L;
	
	private List<Block> blockchain = new ArrayList<>();
	private List<String> networkNodes = new ArrayList<>();

	protected void setup() {
		System.out.println("Node " + getLocalName() + " is up and running");
		
		// Get arguments
		Object[] args = getArguments();
		if (args != null && args.length > 0 && args[0] instanceof String[]) {
			String[] allNames = (String[]) args[0];
			networkNodes.addAll(Arrays.asList(allNames)); // get network node names (1st argument passed)
		}
		
		// Create and send a block every 15 seconds
		addBehaviour(new TickerBehaviour(this, 15000) {

			@Override
			protected void onTick() {
				Block block = createBlock();
				sendBlock(block);
			}
		});
		
		// Receive blocks and add them to the blockchain
		addBehaviour(new CyclicBehaviour() {
			
			@Override
			public void action() {
				ACLMessage msg = receive();
				if (msg != null) {
					try {
						Object obj = msg.getContentObject();
						if (obj instanceof Block) {
							Block receivedBlock = (Block) obj;
							blockchain.add(receivedBlock);
							/*System.out.println(getLocalName() + " received block: " + 
												receivedBlock.toString());*/
							System.out.println(getLocalName() + " --> BLOCKCHAIN: " + blockchain.toString());
						}
					} catch (UnreadableException e) {
						e.printStackTrace();
					}
					
				} else {
					block();
				}
			}
		});
	}
	
	// Create the block with a random ID and the local sender name
	private Block createBlock() {
		String id = UUID.randomUUID().toString().substring(0, 8);
		Block newBlock = new Block(id, getLocalName());
		blockchain.add(newBlock);
		//System.out.println(getLocalName() + " created block: " + newBlock.toString());
		return newBlock;
	}
	
	// Send a broadcast block message
	private void sendBlock(Block block) {
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		
		for (String target : networkNodes) {
			if(!target.equals(getLocalName())) {
				// ISLOCALNAME indicates the target (receiver) is a local agent name. AID = Agent ID
				msg.addReceiver(new AID(target, AID.ISLOCALNAME)); 
			}
		}
		
		try {
			msg.setContentObject(block);
			send(msg);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	protected void takedown() {
		System.out.println("Node " + getLocalName() + " is shutting down.");
	}
	
}
