package blockchain;

import jade.core.Runtime; // JADE Runtime Environment
import jade.core.Profile; 
import jade.core.ProfileImpl; // Used with Profile to store configuration parameters
import jade.wrapper.AgentContainer; // represents a container that holds the agents
import jade.wrapper.AgentController; // used to manage the agents (run, stop, ...)

public class MainContainer {

	public static void main(String[] args) {
		try {
			// Start JADE Runtime
			Runtime runtime = Runtime.instance();
			
			// Profile Settings
			Profile profile = new ProfileImpl();
			profile.setParameter(Profile.GUI, "false");
			
			// Creating the main container for the agents
			AgentContainer mainContainer = runtime.createMainContainer(profile);
			
			// Create the blockchain nodes
			int numAgents = 2;
            String[] agentNames = new String[numAgents];
            for (int i = 0; i < numAgents; i++) {
                agentNames[i] = "node" + (i + 1);
            }

            // Pass all agent names to each agent
            Object[] arguments = new Object[] { agentNames };

            for (String name : agentNames) {
                AgentController agent = mainContainer.createNewAgent(
                        name,
                        "blockchain.NodeAgent",
                        arguments
                );
                agent.start();
            }
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
