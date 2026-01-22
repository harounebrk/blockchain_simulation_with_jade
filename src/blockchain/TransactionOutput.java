package blockchain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TransactionOutput implements Serializable{
	
	private static final long serialVersionUID = 1L;
	
	private int outCounter;
	private List<Output> outputList;
	
	public TransactionOutput() {
		this.outCounter = 0;
		this.outputList = new ArrayList<>();
	}
	
	public static class Output implements Serializable {
		
		private static final long serialVersionUID = 1L;
	
		private double value;
		private String scriptPubKey;
		
		public Output(double value, String scriptPubKey) {
			this.value = value;
			this.scriptPubKey = scriptPubKey;
		}

		public double getValue() {
			return value;
		}

		public void setValue(double value) {
			this.value = value;
		}

		public String getScriptPubKey() {
			return scriptPubKey;
		}

		public void setScriptPubKey(String scriptPubKey) {
			this.scriptPubKey = scriptPubKey;
		}
	
	}

	public int getOutCounter() {
		return outCounter;
	}

	public void setOutCounter(int outCounter) {
		this.outCounter = outCounter;
	}

	public List<Output> getOutputList() {
		return outputList;
	}

	public void setOutputList(List<Output> outputList) {
		this.outputList = outputList;
	}
	
}
