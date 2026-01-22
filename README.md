Welcome to this blockchain simulation project.

To make this project work, please follow the following steps:

1- Create a new Java project and add jade.jar and guava-23.0.jar files to the referenced libraries of the project.
   (Download the jade.jar file from "https://jade.tilab.com/download/jade/license/jade-download/" and the guava-23.0.jar file from "https://repo1.maven.org/maven2/com/google/guava/guava/23.0/")

2- Copy the 'blockchain' directory and the 'module-info.java' file found in this reporsitory and paste it inside the src/ folder of your new project. 'blockchain' is the package containing the project's java classes.

3- Open the MainContainer.java class in your editor and run the project.

4- The typical scenario to follow in our blockchain simulation is to first create the genesis block (click 'Create Block'), mine the block and then send it to other nodes.
   New BTC coins will then be given to each node. You can therefore use them to create transactions and send them to the MemPool.
   After the genesis block, each newly created block automatically includes the coinbase transaction that rewards the miner, along with up to four of the oldest transactions currently in the mempool.

5- Each node has his own GUI to create, mine and send blocks from it. The system typically starts with two nodes. 
   However, you can change the number of nodes by changing the value of the "numAgents" variable in the MainContainer class.

That's it! Enjoy simulating how blockchain systems work and feel free to improve and adjust the code to add new functionalities or meet other needs and requirements.

Thank you!
