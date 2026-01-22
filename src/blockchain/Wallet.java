package blockchain;

import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

public class Wallet implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private PrivateKey privateKey;
	private PublicKey publicKey;
	private double value;
	private Map<String, TransactionOutput.Output> outputs;
	
	public Wallet(KeyPair keyPair, double value) {
		this.privateKey = keyPair.getPrivate(); 
		this.publicKey = keyPair.getPublic();
		this.value = value;
		this.outputs = new HashMap<>();
	}

	public PrivateKey getPrivateKey() {
		return privateKey;
	}

	public void setPrivateKey(PrivateKey privateKey) {
		this.privateKey = privateKey;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(PublicKey publicKey) {
		this.publicKey = publicKey;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public Map<String, TransactionOutput.Output> getOutputs() {
		return outputs;
	}

	public void setOutputs(Map<String, TransactionOutput.Output> outputs) {
		this.outputs = outputs;
	}
	
	// Helper Functions
	public void addOutput(String txId, TransactionOutput.Output out) {
        outputs.put(txId, out);
    }

    public void removeOutput(String txId) {
        outputs.remove(txId);
    }

    public TransactionOutput.Output getOutput(String txId) {
        return outputs.get(txId);
    }

}
