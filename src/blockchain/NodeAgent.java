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

//import java.util.concurrent.TimeUnit;

// https://developer.bitcoin.org/devguide/  -> to understand more about Bitcoin system development

public class NodeAgent extends Agent {

    private static final long serialVersionUID = 1L;
    private BlockchainGUI gui;
    private Wallet wallet;
    private String myAddress;
    private Block currentBlock;
    
    private int TARGET_VALUE = 3;
    private double MINING_REWARD = 6.25;
    private volatile boolean mining = false;
    
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
        gui.setAgent(this);
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
		    gui.appendInfoLine("<b>Blockchain:</b> Empty");
		    
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
						
						// When receiving a transaction from other nodes
						if ("TRANSACTION".equals(msg.getConversationId()) || 
							"SYSTEM_TRANSACTION".equals(msg.getConversationId())) {
						    Transaction tx = (Transaction) msg.getContentObject();

						    boolean valid = verifyTransactionInputs(tx);
						    if (!valid) {
						        gui.log("Rejected invalid transaction " + tx.getId() + " from " + 
						        		msg.getSender().getLocalName());
						        return;
						    }

						    updateMempool(tx, "ADD");
						    gui.log("Received valid transaction " + tx.getId() + " from " 
						            + msg.getSender().getLocalName());
						}
						
						// When receiving a new block
						if ("BLOCK".equals(msg.getConversationId())) {
						    Block block = (Block) msg.getContentObject();
						    
						    if(!verifyBlock(block)) {
						    	gui.log("Rejected invalid block " + block.getId() + " from " + 
						    			msg.getSender().getLocalName());
						    	return;
						    }
						    
						    for (Transaction tx : block.getTransactions()) {
					            updateMempool(tx, "DELETE");
					        }
						    updateBlockchain(block, "ADD");
						    applyBlockTransactions(block);
						    adjustDifficulty(block);
						    
						    mining = false;
						    currentBlock = block;
						    gui.showCurrentBlock(displayBlockDetails(block).toString());
						    
						    gui.log("Received valid block " + block.getId() + " from " 
						            + msg.getSender().getLocalName());
						    gui.appendProofOfWork("A new block was received.");
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
            if (amount <= 0) {
            	gui.displayResult("The amount must be a positive value", false);
                gui.log("Invalid amount due to non-positive value.");
                return;
            }
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
        	 
        	 List<Transaction> searchList = new ArrayList<>();
        	 for (Block b : blockchain) searchList.addAll(b.getTransactions());
        	 
        	 int outputIndex = findOutputIndex(prevTxId, myAddress, searchList); // find index to put in Input
        	 
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
        double memPoolSum = 0.0;
        
        for (TransactionOutput.Output out : chosen.getTxOutput().getOutputList()) {
        	if(!out.getScriptPubKey().equals(myAddress)) {
        		valueSum += out.getValue();
        	}
        }
        
        for (Transaction tx : mempool) {
            if (tx.getSenderHash().equals(myAddress)) {
                for (TransactionOutput.Output out : tx.getTxOutput().getOutputList()) {
                    if (!out.getScriptPubKey().equals(myAddress)) {
                        memPoolSum += out.getValue();
                    }
                }
            }
        }
        
        if (valueSum > wallet.getValue() - memPoolSum) {
            gui.log("Not enough balance to send tx " + chosen.getId());
            gui.displayResult("Transaction [" + chosen.getId() + "] cannot be sent due to "
            		+ "insufficient balance.", false);
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

        // Broadcast signed transaction
        broadcast(chosen, "TRANSACTION");
        
        gui.log("Transaction " + chosen.getId() + " has been sent");
        gui.displayResult("Transaction [" + chosen.getId() + "] has been sent", true);
    }

    public void createBlock() {
        // Pick transactions for the block
    	int maxTx = Math.min(4, mempool.size());
    	List<Transaction> txsForBlock = new ArrayList<>(mempool.subList(0, maxTx));
    	
    	// Determine Previous Block hash
    	String prevHash;
    	if(blockchain.isEmpty()) {
    		prevHash = "ON BRINK OF COLLAPSE " + LocalDateTime.now().toLocalDate().toString();
    	} else {
    		prevHash = blockchain.get(blockchain.size() - 1).getHashHeaderBlock();
    	}
    	
    	// Creating the block object
    	String blockId = UUID.randomUUID().toString().substring(0, 8);
        currentBlock = new Block(blockId, myAddress, prevHash, TARGET_VALUE);
        currentBlock.setTransactions(txsForBlock);
        
        // Creating the coinbase transaction that awards the miner an amount of BTC
	    Transaction coinbaseTx = new Transaction(UUID.randomUUID().toString().substring(0, 8));
	    coinbaseTx.setSenderHash("SYSTEM_COINBASE");
	    coinbaseTx.setTimestamp(System.currentTimeMillis());
	    
	    TransactionInput coinIn = new TransactionInput();
	    coinIn.setInCounter(0);
	    coinbaseTx.setTxInput(coinIn);
	    
	    TransactionOutput coinOut = new TransactionOutput();
	    double reward = MINING_REWARD;
	    coinOut.getOutputList().add(new TransactionOutput.Output(reward, currentBlock.getSender()));
	    coinOut.setOutCounter(1);
	    coinbaseTx.setTxOutput(coinOut);
	    
	    currentBlock.getTransactions().add(0, coinbaseTx); // Adding the coinbase Tx to the Block's Tx list
	    
	    // Compute Merkle root
	    String merkle = currentBlock.calculateMerkleRoot();
	    currentBlock.setMerkleRoot(merkle);
        
        StringBuilder sb = displayBlockDetails(currentBlock);
        
        // Display block in GUI
        gui.showCurrentBlock(sb.toString());

        gui.displayResult("Your new block [" + currentBlock.getId() + "] has been "
        		+ "successfully created with " + txsForBlock.size() + " transactions", true);
        gui.log("New block created with " + txsForBlock.size() + " transactions.");
    }
    
    public void sendBlock() {
    	// Verify the block exists
    	if (currentBlock == null) {
            gui.log("No block to send.");
            gui.displayResult("No block to send. Please create a new block and mine it first.", false);
            return;
        }
    	
    	// Verify that the block is not already added to the blockchain by other nodes
    	boolean block_exists = blockchain.stream()
    	        .anyMatch(b -> b.getId().equals(currentBlock.getId()));

    	if (block_exists) {
    	    gui.log("Block already exists in the blockchain.");
    	    gui.displayResult("The Block already exists in the blockchain. Please create a new one!", false);
    	    return;
    	}
    	
    	// Verify that the block is mined
    	if (currentBlock.getNonce() == 0 || 
    	    currentBlock.getHashHeaderBlock() == null ||
    	    currentBlock.getHashHeaderBlock().isEmpty() ||
    	    (currentBlock.getHashHeaderBlock() != null && 
    	     !currentBlock.getHashHeaderBlock().startsWith("0".repeat(TARGET_VALUE)))) {
    	    
    		gui.displayResult("Block is not mined! Please mine it first.", false);
    		gui.log("Block is not mined.");
    	    return;
    	}
    	
    	// Applying all block transactions
    	applyBlockTransactions(currentBlock);
    	
    	// Adding the block to the blockchain and updating the mempool
    	for (Transaction tx : currentBlock.getTransactions()) {
            updateMempool(tx, "DELETE");
        }
    	updateBlockchain(currentBlock, "ADD");
    	adjustDifficulty(currentBlock);
    	
    	// Broadcast the block to peers
    	broadcast(currentBlock, "BLOCK");

    	gui.log("Block " + currentBlock.getId() + " has been sent.");
        gui.displayResult("Block [" + currentBlock.getId() + "] has been sent to peers.", true);
        
    }

    public void mineBlock() {
	    if (currentBlock == null) {
	    	gui.log("No block is currently created to mine.");
	        gui.displayResult("No block is currently created to mine.", false);
	        return;
	    }
	    
	    gui.log("Mining started for block " + currentBlock.getId());
	    gui.appendProofOfWork("Mining started for block " + currentBlock.getId());
	    gui.appendProofOfWork("Currently mining! please wait...");
	    
	    // Setting the correct nonce and starting PoW
	    currentBlock.setNonce(1);
	    new Thread(() -> {
	        while (true) {
	            String hash = proofOfWork(currentBlock, TARGET_VALUE);

	            if (hash == null) {
	                gui.log("Mining aborted. Another block was received.");
	                gui.appendProofOfWork("Mining stopped!");
	                return;
	            }

	            if (!hash.isEmpty()) {
	                currentBlock.setHashHeaderBlock(hash);
	                
	                gui.appendProofOfWork("Mining SUCCESS in " + 
	                	Math.round((currentBlock.getMiningTime() / 1000.0) * 100.0) / 100.0 + " seconds");
	                gui.log("Mining finished successfully for block " + currentBlock.getId());
	                gui.displayResult("Block [" + currentBlock.getId() +"] mined successfully in " + 
	                	Math.round((currentBlock.getMiningTime() / 1000.0) * 100.0) / 100.0 + " seconds!", 
	                	true);
	                gui.showCurrentBlock(displayBlockDetails(currentBlock).toString());
	                return;
	            }

	            // If PoW returned empty string, retry with new timestamp
	            currentBlock.setTimestamp(System.currentTimeMillis());
	        }
	    }).start();
    }
    
    public void deleteTransaction() {
    	
    	Transaction chosen = gui.deleteTransactionDialog(pendingTransactions);
        if (chosen == null) {
        	gui.displayResult("No pending transactions to delete were found.", false);
        	gui.log("No pending transactions to delete.");
        	return;
        }
        
        pendingTransactions.removeIf(t -> t.getId().equals(chosen.getId()));

        gui.displayResult("Transaction [" + chosen.getId() + "] has been deleted", true);
        gui.log("Transaction [" + chosen.getId() + "] has been deleted");
    }

    public void verifyTransaction() {
    	List<Transaction> allTxs = new ArrayList<>();
    	allTxs.addAll(mempool);
    	for (Block b : blockchain) allTxs.addAll(b.getTransactions());
    	
    	Transaction chosen = gui.verifyTransactionDialog(mempool); // Combine mempool + blockchain txs
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
    
    // Updating the Blockchain display
    public void updateBlockchain(Block block, String op) {
    	
    	if (op.equals("DELETE")) {
    		blockchain.removeIf(b -> b.getId().equals(block.getId()));
    	} else if (op.equals("ADD")){
    		blockchain.add(block);
    	}
    	
        if (blockchain.isEmpty()) {
            gui.updateInfoLine("Blockchain", "Empty");
            return;
        }

        // Extract only block IDs
        List<String> ids = new ArrayList<>();
        for (Block b : blockchain) {
            ids.add(b.getId());
        }

        String joined = String.join(" || ", ids);
        gui.updateInfoLine("Blockchain", " " + joined);
    }
    
    // A helper function to find an output's index given a transaction ID and a ScriptPubKey address
    private int findOutputIndex(String txId, String address, List<Transaction> txList) {

        for (Transaction t : txList) {
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
    
    // Display Block Details Function
    private StringBuilder displayBlockDetails(Block currentBlock) {
    	
    	StringBuilder sb = new StringBuilder();
    	
    	sb.append("<html>");
        // sb.append("<b>==== HEADER INFORMATION ====</b><br>");
        sb.append("<b>Block ID:</b> ").append(currentBlock.getId()).append("<br>");
        sb.append("<b>Block Version:</b> ").append(currentBlock.getVersion()).append("<br>");
        sb.append("<b>Sender:</b> ").append(knownNodes.entrySet().stream()
                							.filter(e -> e.getValue().equals(currentBlock.getSender()))
                							.map(Map.Entry::getKey).findFirst()
                							.orElse(getLocalName())).append("<br>");
        sb.append("<b>Timestamp:</b> ").append(java.time.Instant.ofEpochMilli(currentBlock.getTimestamp())
        								.atZone(java.time.ZoneId.systemDefault())
        								.toLocalDateTime().toString()).append("<br>");
        sb.append("<b>Previous Block Hash:</b> ").append(currentBlock.getHashPrevBlock()).append("<br>");
        sb.append("<b>Target:</b> ").append(currentBlock.getTarget()).append("<br>");
        sb.append("<b>Nonce:</b> ")
        	.append(currentBlock.getNonce() == 0 ? "" : currentBlock.getNonce()).append("<br>");
        sb.append("<b>Merkle Root:</b> ").append(currentBlock.getMerkleRoot()).append("<br>");
        sb.append("<b>Hash Header:</b> ").append(currentBlock.getHashHeaderBlock()).append("<br>");
        sb.append("<br>");

        // ==== TRANSACTION DETAILS ====
        sb.append("<b>==== TRANSACTIONS LIST ====</b><br>");
        int count = 1;
        for (Transaction tx : currentBlock.getTransactions()) {
            sb.append("<b>Transaction ").append(count++).append(":</b><br>");

            sb.append("&nbsp;&nbsp;<b>version:</b> ").append(tx.getVersion()).append("<br>");
            sb.append("&nbsp;&nbsp;<b>id:</b> ").append(tx.getId()).append("<br>");
            sb.append("&nbsp;&nbsp;<b>sender:</b> ").append(tx.getSenderHash()).append("<br>");

            // Convert timestamp nicely
            String dateStr = java.time.Instant.ofEpochMilli(tx.getTimestamp())
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime().toString();

            sb.append("&nbsp;&nbsp;<b>timestamp:</b> ").append(dateStr).append("<br>");

            // Inputs count + expenses total
            int inCount = tx.getTxInput().getInputList().size();
            double inTotal = 0.0;

            for (TransactionInput.Input in : tx.getTxInput().getInputList()) {
                // We do not have direct values here unless you store UTXO
                // So we find the referenced UTXO:
                TransactionOutput.Output utxo = wallet.getOutputs().get(in.getPrevTxId());
                if (utxo != null) {
                    inTotal += utxo.getValue();
                }
            }

            sb.append("&nbsp;&nbsp;<b>Number of inputs:</b> ").append(inCount).append("<br>");
            sb.append("&nbsp;&nbsp;<b>Total expenses:</b> ").append(inTotal).append(" BTC<br>");

            // Outputs
            List<TransactionOutput.Output> outs = tx.getTxOutput().getOutputList();

            int outCount = outs.size();

            // change = output that belongs to me
            double change = outs.stream()
                    .filter(o -> o.getScriptPubKey().equals(tx.getSenderHash()))
                    .mapToDouble(o -> o.getValue())
                    .sum();

            // outputs value excluding change (sum of all outputs sent to others)
            double outTotalExcludingChange = outs.stream()
                    .filter(o -> !o.getScriptPubKey().equals(tx.getSenderHash()))
                    .mapToDouble(o -> o.getValue())
                    .sum();

            sb.append("&nbsp;&nbsp;<b>Number of outputs:</b> ").append(outCount).append("<br>");
            sb.append("&nbsp;&nbsp;<b>Change:</b> ").append(change).append(" BTC<br>");
            sb.append("&nbsp;&nbsp;<b>Remaining output total:</b> ").append(outTotalExcludingChange)
            	.append(" BTC<br><br>");
        }

        sb.append("</html>");
        
        return sb;
    }
    
    // A method for applying all block transactions when a block is mined.
    private void applyBlockTransactions(Block block) {
    	for (Transaction tx : block.getTransactions()) {
    		// If the node was the sender, remove spent outputs
    	    if (tx.getSenderHash().equals(myAddress)) {
    	        for (TransactionInput.Input in : tx.getTxInput().getInputList()) {
    	            wallet.setValue(wallet.getValue() - wallet.getOutput(in.getPrevTxId()).getValue());
    	            wallet.removeOutput(in.getPrevTxId());
    	        }
    	    }

    	    // If any output pays me, add it to my wallet
    	    for (TransactionOutput.Output out : tx.getTxOutput().getOutputList()) {
    	        if (out.getScriptPubKey().equals(myAddress)) {
    	            wallet.addOutput(tx.getId(), out);
    	            wallet.setValue(wallet.getValue() + out.getValue());
    	        }
    	    }
    	    
    	    gui.updateInfoLine("Balance", wallet.getValue() + " BTC");
        }
    }
    
    // A method for verifying if a block is valid or not
    private boolean verifyBlock(Block block) {
    	
    	if(!block.calculateMerkleRoot().equals(block.getMerkleRoot()) ||
           !block.calculateBlockHash().equals(block.getHashHeaderBlock())) {
    		return false;
    	}
    	
    	if (!blockchain.isEmpty()) {
            String lastHash = blockchain.get(blockchain.size() - 1).getHashHeaderBlock();
            if (!block.getHashPrevBlock().equals(lastHash)) {
                return false;
            }
        }
    	
    	return true;
    	
    }
    
    // Proof-of-work computation method
    private String proofOfWork(Block block, int target) {
    	mining = true;
    	int nonce = block.getNonce();
    	String prefix = "0".repeat(target);
    	
    	long startTime = System.currentTimeMillis();
    	
    	while (mining) {
	    	if (currentBlock != null && !currentBlock.getId().equals(block.getId())) {
	            return null;
	        }
	    	
	    	 block.setTimestamp(System.currentTimeMillis());
	         String hash = block.calculateBlockHash();
	         
	         if (hash.startsWith(prefix)) {
	             long end = System.currentTimeMillis();
	             block.setMiningTime(end - startTime);
	             return hash;
	         }
	         
	         block.setNonce(block.getNonce() + 1);
	         
	         Thread.yield(); // prevents agent from blocking GUI   
    	}
    	
    	return null;
    }
    
    // A method for adjusting difficulty
    private void adjustDifficulty(Block block) {
        long time = block.getMiningTime();

        if (time > 180000) { // more than 3 minutes
            TARGET_VALUE = Math.max(1, TARGET_VALUE - 1);
            gui.log("Difficulty DECREASED → new TARGET = " + TARGET_VALUE);
        } else {
            TARGET_VALUE += 1;
            gui.log("Difficulty INCREASED → new TARGET = " + TARGET_VALUE);
        }
        
        if(blockchain.size() % 5 == 0) {
        	MINING_REWARD -= 0.1;
        }
    }

    @Override
    protected void takeDown() {
        gui.log("Agent shutting down...");
        System.out.println("Agent " + getLocalName() + " terminated.");
    }
}
