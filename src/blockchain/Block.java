package blockchain;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

public class Block implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private String id;
	private String sender;
	private String version;
	private long timestamp;
	private String hashPrevBlock;
	private int nonce;
	private int target;
	private String merkleRoot;
	private String hashHeaderBlock;
	private List<Transaction> transactions;
	private long miningTime;
	
	public Block(String id, String sender, String hashPrevBlock, int target) {
		this.id = id;
		this.sender = sender;
		this.version = "1.0";
		this.timestamp = Instant.now().toEpochMilli();
		this.hashPrevBlock = hashPrevBlock;
		this.nonce = 0;
		this.target = target;
		this.transactions = new ArrayList<>();
		this.merkleRoot = "";
		this.hashHeaderBlock = "";
	}
	
	// Merkle Root Computation Method
	public String calculateMerkleRoot() {
		if (transactions == null || transactions.isEmpty()) {
			return "";
		}
		
		// Hash transactions, we combined id + timestamp + senderHash to create the hash of each Tx.
		List<String> hashes = new ArrayList<>();
		
		for (Transaction tx : transactions) {
			String data = tx.getId() + tx.getTimestamp() + tx.getSenderHash() + tx.getVersion();
			String hash = Hashing.sha256().hashString(data, StandardCharsets.UTF_8).toString();
			hashes.add(hash);
		}
		
		// Recursive Hashing until we end up with one hash
		int hashesSize = hashes.size();
		while(hashesSize > 1) {
			List<String> newHashes = new ArrayList<>();
			for(int i = 0; i < hashesSize; i+=2) {
				String left = hashes.get(i);
				String right = (i+1 < hashesSize) ? hashes.get(i+1) : left;
				String combinedHash = Hashing.sha256()
										.hashString(left + right, StandardCharsets.UTF_8).toString();
				newHashes.add(combinedHash);
			}
			hashes = newHashes;
			hashesSize = hashes.size();
		}
		return hashes.get(0);
	}
	
	// Block Hash Computation Method
	public String calculateBlockHash() {
		String data = version + id + timestamp + (hashPrevBlock != null ? hashPrevBlock : "") + nonce 
				+ target + (merkleRoot != null ? merkleRoot : "");
		
		return Hashing.sha256().hashString(data, StandardCharsets.UTF_8).toString();
	}
	
	public Block(String id, String sender) {
        this(id, sender, null, 0);
    }
	
	public String getId() {
		return id;
	}
	
	public String getSender() {
		return sender;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public String getHashPrevBlock() {
		return hashPrevBlock;
	}

	public void setHashPrevBlock(String hashPrevBlock) {
		this.hashPrevBlock = hashPrevBlock;
	}

	public int getNonce() {
		return nonce;
	}

	public void setNonce(int nonce) {
		this.nonce = nonce;
	}

	public int getTarget() {
		return target;
	}

	public void setTarget(int target) {
		this.target = target;
	}

	public String getMerkleRoot() {
		return merkleRoot;
	}

	public void setMerkleRoot(String merkleRoot) {
		this.merkleRoot = merkleRoot;
	}

	public String getHashHeaderBlock() {
		return hashHeaderBlock;
	}

	public void setHashHeaderBlock(String hashHeaderBlock) {
		this.hashHeaderBlock = hashHeaderBlock;
	}

	public List<Transaction> getTransactions() {
		return transactions;
	}

	public void setTransactions(List<Transaction> transactions) {
		this.transactions = transactions;
	}

	public String getVersion() {
		return version;
	}
	
	public long getMiningTime() { return miningTime; }
	public void setMiningTime(long ms) { this.miningTime = ms; }

	@Override
	public String toString() {
		return "Block [ id = " + id + ", sender = " + sender + " ]";
	}
	
}
