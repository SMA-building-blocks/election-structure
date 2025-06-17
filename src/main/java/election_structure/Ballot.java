package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class Ballot extends BaseAgent {

	private Hashtable<AID, Types> registeredVoters;
	private ArrayList<AID> registeredCandidates;

	private Hashtable<Types, Integer> receivedVotes;
	private Hashtable<Types, Integer> votingWeights;

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

				if ( msg.getContent().startsWith(VOTEID) ) {
					logger.log(Level.INFO, 
							String.format("RECEIVED ELECTION ID FROM %s: %s", msg.getSender().getLocalName(), msg.getContent()));
					
					votingCode = Integer.parseInt(splittedMsg[1]);
					
					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));

					votingWeights = new Hashtable<>();
					for ( int i = 4; i < splittedMsg.length; i += 2 ) {
						votingWeights.put(Types.valueOf(splittedMsg[i]), Integer.parseInt(splittedMsg[i+1]));
					}

					setupBallot();


				} else {
					System.out.println(msg.getContent());
					logger.log(Level.INFO, 
							String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG, msg.getSender().getLocalName()));
				}
			}
		};
	}

	private void setupBallot () {
		receivedVotes = new Hashtable<>();
		registeredCandidates = new ArrayList<>();
		registeredVoters = new Hashtable<>();

		ArrayList<DFAgentDescription> foundVoters = new ArrayList<>(
			Arrays.asList(searchAgentByType(Integer.toString(votingCode))));

		Iterator<ServiceDescription> iterator;
		try {
			for ( DFAgentDescription voter : foundVoters ) {
				iterator = voter.getAllServices();

				while ( iterator.hasNext() ) {
					ServiceDescription el = iterator.next();
					if ( Arrays.toString(Types.values()).contains(el.getName()) ) {
						registeredVoters.put(voter.getName(), Types.valueOf(el.getName()));
					} else if ( el.getName().equals("Candidate") ) {
						registeredCandidates.add(voter.getName());
					}
				}
			}
		} catch ( Exception e ) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE PERFORMING BALLOT SETUP %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}

		if ( registeredCandidates.size() < 1 || registeredVoters.size() < 2 ) {
			logger.log(Level.WARNING, String.format("%s THERE CANNOT BE AN ELECTION WITH NO CANDIDATES OR NOT ENOUGH QUORUM %s", ANSI_YELLOW, ANSI_RESET));
			
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			
			ArrayList<DFAgentDescription> foundMediators;
			String [] searchTypes = { Integer.toString(votingCode), "mediator" };
			foundMediators = new ArrayList<>(
				Arrays.asList(searchAgentByType(searchTypes)));

			if ( foundMediators.size() > 0 ) {
				for ( DFAgentDescription fndMed : foundMediators ) {
					msg.addReceiver(fndMed.getName());
				}
			}

			msg.setContent(String.format("FAILURE %d", votingCode));
			send(msg);
		}

		/*
		 * HERE, WE SHOULD CONTINUE THE ELECTION
		 * BY SENDING A "READY" MESSAGE TO THE MEDIATOR
		 */
	}
}
