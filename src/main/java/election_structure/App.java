package election_structure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;

import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

/**
 * Class that set the main agent and it's actions
 */
public class App extends BaseAgent {

	private static final long serialVersionUID = 1L;

	@Override
	protected void setup() {

		loggerSetup();

		registerDF(this, CREATOR, CREATOR);
		addBehaviour(handleMessages());

		logger.log(Level.INFO, "Starting Agents...");

		logger.log(Level.INFO, "Creating Workers...");

		ArrayList<String> votersName = new ArrayList<>();
		
		Object[] args = getArguments();
		int votersQuorum = 0;
		if (args != null && args.length > 0) {
			votersQuorum = Integer.parseInt(args[0].toString());
			randomAgentMalfunction = (Integer.parseInt(args[1].toString()) > 0);
		}

		int votingStarter = rand.nextInt(votersQuorum);

		logger.log(Level.INFO, String.format("Agent number %d will request to the mediator!", votingStarter));

		for (int i = 0; i < votersQuorum; ++i)
			votersName.add("voter_" + i);

		try {
			AgentContainer container = getContainerController();

			votersName.forEach(worker -> {
				this.launchAgent(worker, "election_structure.Voter", null);
				logger.log(Level.INFO, String.format("%s CREATED AND STARTED NEW VOTER: %s ON CONTAINER %s",
						getLocalName(), worker, container.getName()));
			});
		} catch (Exception any) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE CREATING AGENTS %s", ANSI_RED, ANSI_RESET));
			any.printStackTrace();
		}

		String m1AgentName = "Mediator";
		launchAgent(m1AgentName, "election_structure.Mediator", null);

		logger.log(Level.INFO, "Agents started...");
		pauseSystem();

		// send them a message demanding start
		logger.log(Level.INFO, "Starting system!");

		sendMessage(votersName.get(votingStarter), ACLMessage.INFORM, START);
		logger.log(Level.INFO, String.format("%s SENT START MESSAGE TO %s", getLocalName(), votersName.get(votingStarter)));
	}

	private void pauseSystem() {
		try {
			logger.log(Level.WARNING, String.format(
					"%s The system is paused -- this action is here only to let you activate the sniffer on the agents, if you want (see documentation) %s",
					ANSI_YELLOW, ANSI_RESET));
			logger.log(Level.WARNING,
					String.format("%s Press enter in the console to start the agents %s", ANSI_YELLOW, ANSI_RESET));
			System.in.read();
		} catch (IOException e) {
			logger.log(Level.SEVERE, String.format("%s ERROR STARTING THE SYSTEM %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}

	private void launchAgent(String agentName, String className, Object[] args) {
		try {
			AgentContainer container = getContainerController(); // get a container controller for creating new agents
			AgentController newAgent = container.createNewAgent(agentName, className, args);
			newAgent.start();
		} catch (Exception e) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE LAUNCHING AGENTS %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}
}