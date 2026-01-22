package blockchain;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.time.Instant;
import java.util.Base64;

import java.security.PrivateKey;

public class Transaction implements Serializable{
	
	private static final long serialVersionUID = 1L;
	
	private String version;
	private String id;
	private String senderHash;
	private long timestamp;
	private TransactionInput txInput;
	private TransactionOutput txOutput;
	
	public Transaction(String id) {
		this.version = "1.0";
		this.id = id;
		this.senderHash = "";
		this.timestamp = Instant.now().toEpochMilli();
		this.txInput = new TransactionInput();
        this.txOutput = new TransactionOutput();
	}
	
	
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSenderHash() {
		return senderHash;
	}

	public void setSenderHash(String senderHash) {
		this.senderHash = senderHash;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getVersion() {
		return version;
	}

	public TransactionInput getTxInput() {
		return txInput;
	}

	public void setTxInput(TransactionInput txInput) {
		this.txInput = txInput;
	}

	public TransactionOutput getTxOutput() {
		return txOutput;
	}

	public void setTxOutput(TransactionOutput txOutput) {
		this.txOutput = txOutput;
	}



	@Override
    public String toString() {
        return "Transaction [id=" + id + "]";
    }
	
}
