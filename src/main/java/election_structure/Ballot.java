package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

public class Ballot extends BaseAgent {


    @Override
    protected void setup() {

        logger.log(Level.INFO, "Starting Ballot...");

        logger.log(Level.INFO, "I'm the ballot!");
		this.registerDF(this, "Ballot", "ballot");

		
		addBehaviour(handleMessages());

        ArrayList<DFAgentDescription> foundAgent = new ArrayList<>(
			Arrays.asList(searchAgentByType("Mediator")));

		sendMessage(foundAgent.get(0).getName().getLocalName(), ACLMessage.INFORM, "CHECK");

    }

    @Override
	protected OneShotBehaviour handleInform ( ACLMessage msg ) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action () {
				String [] splittedMsg = msg.getContent().split(" ");

				if (msg.getContent().startsWith(VOTEID)) {
					logger.log(Level.INFO, 
							String.format("RECEIVED ELECTION ID FROM %s: %s", msg.getSender().getLocalName(), msg.getContent()));
					
					
					votingCode = Integer.parseInt(splittedMsg[1]);
					
					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
					
				} else {
					logger.log(Level.INFO, 
							String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG, msg.getSender().getLocalName()));
				}
			}
		};
	}

}
