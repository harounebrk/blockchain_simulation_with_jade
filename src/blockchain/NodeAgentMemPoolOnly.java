package blockchain;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

// https://developer.bitcoin.org/devguide/

public class NodeAgentMemPoolOnly extends Agent {

    private static final long serialVersionUID = 1L;
    private BlockchainGUI gui;
    private Wallet wallet;
    private String myAddress;
    
    private String[] allNodeNames;
    private Map<String, String> knownNodes = new HashMap<>();
    private List<Transaction> mempool = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();
    private List<Block> blockchain = new ArrayList<>();

    @Override
    protected void setup() {
        System.out.println("Agent " + getLocalName() + " started.");
        
        // Retrieve passed parameters
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            allNodeNames = (String[]) args[0];
        }

        // Create GUI and link both
        gui = new BlockchainGUI();
        // gui.setAgent(this);
        gui.log("Agent " + getLocalName() + " initialized.");
        gui.setInfo("<html><b>Node: </b>" + getLocalName() + "</html>");
        
        // Wallet generation for each user
        try {
			KeyPair keyPair = CryptoUtils.generateKeyPair();
			wallet = new Wallet(keyPair, 0.0); 
		    gui.log(getLocalName() + " wallet created.");
		    
		    // Generating the node's address (used for sending transaction).
		    /* NOTE: In real Bitcoin, the function used for hashing the public key is RIPEMD160(sha256(pk)))
		             However, in our simulation, we used sha256(sha256(pk))) for simplification */
		    String pubKeyStr = Base64.getEncoder().encodeToString(wallet.getPublicKey().getEncoded());
		    myAddress = CryptoUtils.hashData(CryptoUtils.hashData(pubKeyStr)).substring(0, 40);
		    
		    gui.appendInfoLine("<b>Address: </b>" + myAddress);
		    gui.appendInfoLine("<b>Balance: </b>" + wallet.getValue() + " BTC");
		    gui.log("Generated address for " + getLocalName());
		    
		    gui.appendInfoLine("<b>MemPool:</b> Empty");
		    
		    // Creating an initial system transaction to award users initial balance from the system
		    Transaction genesisTx = new Transaction(UUID.randomUUID().toString().substring(0, 8));
		    genesisTx.setSenderHash("SYSTEM");
		    genesisTx.setTimestamp(System.currentTimeMillis());
		    
		    TransactionOutput txOut = new TransactionOutput();
		    TransactionOutput.Output out = new TransactionOutput.Output(
		            10.0,      // value
		            myAddress    // scriptPubKey = just address
		    );
		    
		    txOut.getOutputList().add(out); // Add output to transaction output list
		    txOut.setOutCounter(1); // set counter to 1
		    genesisTx.setTxOutput(txOut); // set the created output inside Transaction
		    // ==============================================================================
		    wallet.setValue(wallet.getValue() + out.getValue()); // update wallet value.
		    wallet.addOutput(genesisTx.getId(), out); 	// add the output to the wallet.
		    
		    gui.updateInfoLine("Balance", wallet.getValue() + " BTC"); // update balance on the UI.
		    gui.log("System rewarded 10 BTC to " + getLocalName());
		    
		    updateMempool(genesisTx, "ADD"); // Add transaction to the mempool
		    gui.log("Added system transaction to mempool.");
		    
		    // Broadcast public key and genesis transaction to all nodes
	        broadcast(myAddress, "ADDRESS");
	        broadcast(genesisTx, "SYSTEM_TRANSACTION");
	        
	        
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        
        // Cyclic behaviours
        addBehaviour(new CyclicBehaviour() {

			private static final long serialVersionUID = 1L;

			@Override
			public void action() {
				ACLMessage msg = receive();
				if(msg != null) {
					try {
						// When receiving the address of another node
						if("ADDRESS".equals(msg.getConversationId())) {
							String address = (String) msg.getContentObject();
							knownNodes.put(msg.getSender().getLocalName(), address);
							gui.log("Received address from " + msg.getSender().getLocalName());
						}
						
						// When receiving a system transaction from other nodes
						if("SYSTEM_TRANSACTION".equals(msg.getConversationId())){
							Transaction tx = (Transaction) msg.getContentObject();
							updateMempool(tx, "ADD");
							gui.log("Received transaction " + tx.getId() + " from " + 
									msg.getSender().getLocalName());
							
							// Update the wallet if the transaction is for this node
							for (TransactionOutput.Output out : tx.getTxOutput().getOutputList()) {
								if (out.getScriptPubKey().equals(myAddress)) {
									wallet.addOutput(tx.getId(), out);
									wallet.setValue(wallet.getValue() + out.getValue());
									gui.updateInfoLine("Balance", wallet.getValue() + " BTC"); // update GUI
								}
							}
						}
						
						// When receiving a normal transaction from other nodes
						if ("TRANSACTION".equals(msg.getConversationId())) {
						    Transaction tx = (Transaction) msg.getContentObject();

						    boolean valid = verifyTransactionInputs(tx);
						    if (!valid) {
						        gui.log("Rejected invalid transaction " + tx.getId());
						        return;
						    }

						    updateMempool(tx, "ADD");
						    gui.log("Received valid transaction " + tx.getId() + " from " 
						            + msg.getSender().getLocalName());

						    for (TransactionOutput.Output out : tx.getTxOutput().getOutputList()) {
						        if (out.getScriptPubKey().equals(myAddress)) {
						            wallet.addOutput(tx.getId(), out);
						            wallet.setValue(wallet.getValue() + out.getValue());
						            gui.updateInfoLine("Balance", wallet.getValue() + " BTC");
						        }
						    }
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					block();
				}
				
			}
		});
    }

    // === Button-triggered methods ===
    public void createTransaction() {
        
    	// prompt the user for amount and recipient
    	List<String> recipients = new ArrayList<>(knownNodes.keySet());
    	if (recipients.isEmpty()) {
    		gui.displayResult("No known nodes to send to.", false);
            gui.log("No known nodes to send to.");
            return;
        }
    	
    	Object[] userInput = gui.createTransactionDialog(recipients);
        if (userInput == null) {
            gui.log("Transaction creation cancelled.");
            return;
        }
        
        String amountStr = (String) userInput[0];
        String recipientName = (String) userInput[1];
        
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (Exception e) {
        	gui.displayResult("The amount you entered is invalid.", false);
            gui.log("Invalid amount.");
            return;
        }

        String recipientAddress = knownNodes.get(recipientName);
        if (recipientAddress == null) {
        	gui.displayResult("The selected recipient cannot be found.", false);
            gui.log("Unknown recipient.");
            return;
        }
        
        // Select UTXOs automatically from the wallet
        List<Map.Entry<String, TransactionOutput.Output>> selected = new ArrayList<>();
        double accumulated = 0.0;
        
        for (Map.Entry<String, TransactionOutput.Output> entry : wallet.getOutputs().entrySet()) {
        	selected.add(entry);
        	accumulated += entry.getValue().getValue();
        	if (accumulated >= amount) break;
        }
        
        if (accumulated < amount) {
        	gui.displayResult("Not enough balance to create transaction.", false);
    	    gui.log("Not enough balance to create transaction.");
    	    return;
        }
        
        // Prepare the transaction details
        Transaction tx = new Transaction(UUID.randomUUID().toString().substring(0, 8));
        tx.setSenderHash(myAddress); // sender = myAddress = my PubKey Hash
        
        // Build Transaction Inputs
        TransactionInput txIn = new TransactionInput(); // Creating Transaction Input List
        for (Map.Entry<String, TransactionOutput.Output> entry : selected) {
        	 String prevTxId = entry.getKey(); // Get TxId of UTXO
        	 TransactionOutput.Output utxo = entry.getValue(); // get UTXO
        	 
        	 int outputIndex = findOutputIndex(prevTxId, myAddress); // find index to put in Input
        	 
        	 if (outputIndex == -1) {
        		 	gui.displayResult("Could not find output index for tx " + prevTxId, false);
        	        gui.log("Could not find output index for tx " + prevTxId);
        	        return;
        	 }
        	 
        	 Map<PublicKey, String> emptyScript = new HashMap<>(); // No SigScript at this stage
        	 TransactionInput.Input input =
        	         	new TransactionInput.Input(prevTxId, outputIndex, emptyScript);
        	 
        	 txIn.getInputList().add(input);
        }
        
        txIn.setInCounter(txIn.getInputList().size());
   	 	tx.setTxInput(txIn);
   	 	
   	 	// Build Transaction Outputs
   	 	TransactionOutput txOut = new TransactionOutput();
   	 	
   	 	TransactionOutput.Output recipientOut = new TransactionOutput.Output(amount, recipientAddress);
	   	txOut.getOutputList().add(recipientOut);
	   	
	   	double changeAmount = accumulated - amount;

	   	if (changeAmount > 0) {
	   	    TransactionOutput.Output changeOut = new TransactionOutput.Output(changeAmount, myAddress);
	   	    txOut.getOutputList().add(changeOut);
	   	}
	   	
	   	txOut.setOutCounter(txOut.getOutputList().size());

	   	tx.setTxOutput(txOut);
	   	
	   	// Adding the transaction to the pending list
	   	pendingTransactions.add(tx);

	   	gui.log("Transaction " + tx.getId() + " has been created.");
	   	gui.displayResult("A new Transaction [" + tx.getId() + "] has been created.", true);
	   	
        
    }

    public void sendTransaction() {
        if (pendingTransactions.isEmpty()) {
            gui.log("No pending transactions.");
            gui.displayResult("No pending transactions can be found.", false);
            return;
        }

        // Show dialog and get chosen transaction
        Transaction chosen = gui.SendTransactionDialog(pendingTransactions, knownNodes, myAddress);
        if (chosen == null) {
            gui.log("No transaction selected.");
            return;
        }
        
        // Verify if the node has enough balance before sending
        double valueSum = 0.0;
        for (TransactionOutput.Output out : chosen.getTxOutput().getOutputList()) {
        	valueSum += out.getValue();
        }

        if (valueSum > wallet.getValue()) {
            gui.log("Not enough balance to send tx " + chosen.getId());
            pendingTransactions.removeIf(t -> t.getId().equals(chosen.getId()));
            gui.log("Transaction " + chosen.getId() + "has been removed.");
            gui.displayResult("Transaction [" + chosen.getId() + "] has been removed and cannot "
            		+ "be sent due to insufficient balance.", false);
            return;
        }

        // Set the Tx timestamp
        chosen.setTimestamp(System.currentTimeMillis());

        // Set Base header String
        String baseHeader = chosen.getId()
                + "|" + chosen.getVersion()
                + "|" + chosen.getSenderHash()
                + "|" + chosen.getTimestamp();

        // Signing every input in the transaction
        try {
            for (TransactionInput.Input in : chosen.getTxInput().getInputList()) {
                String perInput = baseHeader + "|IN" + in.getPrevTxId() + in.getIndex();

                // hash before signing
                String digest = CryptoUtils.hashData(perInput);

                // sign the digest
                String signature = CryptoUtils.signData(digest, wallet.getPrivateKey());

                // set scriptSig (map: pubkey -> signature)
                Map<java.security.PublicKey, String> scriptSig = new HashMap<>();
                scriptSig.put(wallet.getPublicKey(), signature);
                in.setScriptSig(scriptSig);
            }
        } catch (Exception e) {
            gui.log("Signing error: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Move tx from pending -> mempool
        pendingTransactions.removeIf(t -> t.getId().equals(chosen.getId()));
        updateMempool(chosen, "ADD");

        // Remove consumed UTXOs from wallet
        for (TransactionInput.Input in : chosen.getTxInput().getInputList()) {
        	wallet.setValue(wallet.getValue() - wallet.getOutput(in.getPrevTxId()).getValue());
            wallet.removeOutput(in.getPrevTxId());
        }

        // Add change outputs that belong to the sender to wallet and update balance
        for (TransactionOutput.Output out : chosen.getTxOutput().getOutputList()) {
            if (out.getScriptPubKey().equals(myAddress)) {
                wallet.addOutput(chosen.getId(), out);
                wallet.setValue(wallet.getValue() + out.getValue());
            }
        }

        // Update GUI balance
        gui.updateInfoLine("Balance", wallet.getValue() + " BTC");

        // Broadcast signed transaction
        broadcast(chosen, "TRANSACTION");
        
        gui.log("Transaction " + chosen.getId() + " has been sent");
        gui.displayResult("Transaction [" + chosen.getId() + "] has been sent", true);
    }

    public void createBlock() {
        gui.log("Creating a new block...");
        // TODO: implement logic
    }

    public void sendBlock() {
        gui.log("Broadcasting block to the network...");
        // TODO: implement logic
    }

    public void hashBlock() {
        gui.log("Hashing current block...");
        // TODO: implement logic
    }

    public void mineBlock() {
        gui.log("Mining block (proof of work)...");
        // TODO: implement logic
    }

    public void verifyTransaction() {
    	Transaction chosen = gui.verifyTransactionDialog(mempool);
        if (chosen == null) return;

        boolean valid;

        // Automatically valid if sent by system
        if ("SYSTEM".equals(chosen.getSenderHash())) {
            valid = true;
        } else {
            valid = verifyTransactionInputs(chosen);
        }

        gui.displayResult(
            "Transaction [" + chosen.getId() + (valid ? "] is VALID" : "] is INVALID"),
            valid
        );
    }
    
    // Broadcasting messages to other nodes
    private void broadcast(Object payload, String conversationId) {
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setConversationId(conversationId);

        try {
            if (payload instanceof Serializable) {
                msg.setContentObject((Serializable) payload);
            } else {
                // fallback: send as plain text
                msg.setContent(payload.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        for (String nodeName : allNodeNames) {
            if (!nodeName.equals(getLocalName())) {
                msg.addReceiver(new AID(nodeName, AID.ISLOCALNAME));
            }
        }

        send(msg);
        gui.log("Broadcasted " + conversationId + " to peers.");
    }

    // Updating the MemPool Display
    public void updateMempool(Transaction tx, String op) {
    	
    	if (op.equals("DELETE")) {
    		mempool.removeIf(t -> t.getId().equals(tx.getId()));
    	} else if (op.equals("ADD")){
    		mempool.add(tx);
    	}
    	mempool.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp())); // Sorting the mempool
    	
        if (mempool.isEmpty()) {
            gui.updateInfoLine("MemPool", "Empty");
            return;
        }

        // Extract only transaction IDs
        List<String> ids = new ArrayList<>();
        for (Transaction t : mempool) {
            ids.add(t.getId());
        }

        String joined = String.join(" || ", ids);
        gui.updateInfoLine("MemPool", " " + joined);
    }
    
    // A helper function to find an output's index given a transaction ID and a ScriptPubKey address
    private int findOutputIndex(String txId, String address) {

        for (Transaction t : mempool) {  // search only mempool for now
            if (t.getId().equals(txId)) {

                List<TransactionOutput.Output> outs = t.getTxOutput().getOutputList();

                for (int i = 0; i < outs.size(); i++) {
                    if (outs.get(i).getScriptPubKey().equals(address)) {
                        return i;
                    }
                }
            }
        }

        return -1; // not found
    }

    private boolean verifyTransactionInputs(Transaction tx) {
        try {
            String baseHeader = tx.getId()
                    + "|" + tx.getVersion()
                    + "|" + tx.getSenderHash()
                    + "|" + tx.getTimestamp();

            for (TransactionInput.Input in : tx.getTxInput().getInputList()) {

                String perInput = baseHeader + "|IN" + in.getPrevTxId() + in.getIndex();
                String digest = CryptoUtils.hashData(perInput);

                for (Map.Entry<PublicKey, String> e : in.getScriptSig().entrySet()) {
                    PublicKey pub = e.getKey();
                    String sig = e.getValue();

                    boolean ok = CryptoUtils.verifySignature(digest, sig, pub);
                    if (!ok) return false;
                }
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    @Override
    protected void takeDown() {
        gui.log("Agent shutting down...");
        System.out.println("Agent " + getLocalName() + " terminated.");
    }
}

