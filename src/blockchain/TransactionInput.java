package blockchain;

import java.io.Serializable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class TransactionInput implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private int inCounter;
	private List<Input> inputList;
	
	public TransactionInput() {
		this.inCounter = 0;
		this.inputList = new ArrayList<>();
	}
	
	public static class Input implements Serializable {

		private static final long serialVersionUID = 1L;
		
		private String prevTxId;
		private int index;
		private Map<PublicKey, String> scriptSig; 
		
		public Input(String prevTxId, int index, Map<PublicKey, String> scriptSig) {
			super();
			this.prevTxId = prevTxId;
			this.index = index;
			this.scriptSig = scriptSig;
		}

		public String getPrevTxId() {
			return prevTxId;
		}

		public void setPrevTxId(String prevTxId) {
			this.prevTxId = prevTxId;
		}

		public int getIndex() {
			return index;
		}

		public void setIndex(int index) {
			this.index = index;
		}

		public Map<PublicKey, String> getScriptSig() {
			return scriptSig;
		}

		public void setScriptSig(Map<PublicKey, String> scriptSig) {
			this.scriptSig = scriptSig;
		}
		
	}

	public int getInCounter() {
		return inCounter;
	}

	public void setInCounter(int inCounter) {
		this.inCounter = inCounter;
	}

	public List<Input> getInputList() {
		return inputList;
	}

	public void setInputList(List<Input> inputList) {
		this.inputList = inputList;
	}
	
}
