package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

public class Voter extends BaseAgent {

	private static final long serialVersionUID = 1L;
	
	private int minVotingValue = 0;
	private int maxVotingValue = 0;
	private int myVotingValue = 0;

	@Override
	protected void setup() {
		logger.log(Level.INFO,String.format("I'm voter: %s", this.getLocalName()));
		
		this.registerDF(this, "Voter", "voter");

		addBehaviour(handleMessages());
	}
	
	@Override
	protected OneShotBehaviour handleInform ( ACLMessage msg ) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action () {
				if (msg.getContent().startsWith(START)) {
					ACLMessage msg2 = new ACLMessage(ACLMessage.INFORM);
					msg2.setContent(START);
					
					DFAgentDescription [] foundAgents = searchAgentByType("mediator");
					
					try {
						AID foundMediator = null;
						if ( foundAgents.length > 0 ) {
							foundMediator = foundAgents[0].getName();
							
							msg2.addReceiver(foundMediator);
							
							send(msg2);
							logger.log(Level.INFO, String.format("%s SENT START MESSAGE TO %s", getLocalName(), foundMediator.getLocalName()));
						}
					} catch ( Exception any ) {
						logger.log(Level.SEVERE, ANSI_RED + "ERROR WHILE SENDING MESSAGE" + ANSI_RESET);
						any.printStackTrace();
					}
				} else if (msg.getContent().startsWith(VOTEID)) {
					logger.log(Level.INFO, 
							String.format("RECEIVED VOTING STRUCTURE FROM %s: %s", msg.getSender().getLocalName(), msg.getContent()));
					
					String [] splittedMsg = msg.getContent().split(" ");
					
					votingCode = Integer.parseInt(splittedMsg[1]);
					minVotingValue = Integer.parseInt(splittedMsg[3]);
					maxVotingValue = Integer.parseInt(splittedMsg[5]);
					
					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
					
					informVotingRegistration();
					
					ArrayList<DFAgentDescription> foundAgents = new ArrayList<>(
							Arrays.asList(searchAgentByType("voter")));
					
					ACLMessage msg2 = new ACLMessage(ACLMessage.INFORM);
					msg2.setContent(String.format("%s %s", INVITE, msg.getContent()));
					
					foundAgents.forEach(ag -> {
						if ( !ag.getName().equals(myAgent.getAID())  ) {
							msg2.addReceiver(ag.getName());
						}
					});
					
					ACLMessage reply = msg.createReply();
					reply.setContent(String.format("%s QUORUM %d", INFORM, foundAgents.size()));
					myAgent.send(reply);
					
					send(msg2);
					logger.log(Level.INFO, String.format("%s SENT INVITE TO VOTERS!", getLocalName()));
					
					
				} else if (msg.getContent().startsWith(INVITE)) {
					logger.log(Level.INFO, 
							String.format("RECEIVED VOTING STRUCTURE FROM %s: %s", msg.getSender().getLocalName(), msg.getContent()));		
					
					String [] splittedMsg = msg.getContent().split(" ");

					votingCode = Integer.parseInt(splittedMsg[2]);
					minVotingValue = Integer.parseInt(splittedMsg[4]);
					maxVotingValue = Integer.parseInt(splittedMsg[6]);
					
					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
					
					informVotingRegistration();
				} else if(msg.getContent().startsWith(WINNER) || msg.getContent().startsWith(DRAW)){ 
					ACLMessage rpl = msg.createReply();

					rpl.setContent(THANKS);
					send(rpl);

					minVotingValue = 0;
					maxVotingValue = 0;
					myVotingValue = 0;
					votingCode = 0;
					
				} else {
					logger.log(Level.INFO, 
							String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG, msg.getSender().getLocalName()));
				}
			}
		};
	}
	
	@Override
	protected OneShotBehaviour handleRequest ( ACLMessage msg ) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action () {
				if (msg.getContent().startsWith(REQUEST)) {
					
					myVotingValue = rand.nextInt(minVotingValue, maxVotingValue + 1);	
					
					ACLMessage voteMsg = msg.createReply();
					voteMsg.setPerformative(ACLMessage.INFORM);
					voteMsg.setContent(String.format("%s ON %d: %d", VOTE, votingCode, myVotingValue));
					
					send(voteMsg);

					logger.log(Level.INFO,  String.format("%s SENT VOTE TO %s", getLocalName(), msg.getSender().getLocalName()));
				} else {
					logger.log(Level.INFO, 
							String.format("%s %S %s", getLocalName(), UNEXPECTED_MSG, msg.getSender().getLocalName()));
				}
			}
		};
	}

	private void informVotingRegistration() {
		ACLMessage informMsg = new ACLMessage(ACLMessage.INFORM);
		informMsg.setContent(String.format("%s IN %d", REGISTERED, votingCode));
		
		ArrayList<DFAgentDescription> foundVotingParticipants;

		String [] types = { Integer.toString(votingCode), "mediator" };
		
		foundVotingParticipants = new ArrayList<>(
				Arrays.asList(searchAgentByType(types)));
		
		foundVotingParticipants.forEach(ag -> 
			informMsg.addReceiver(ag.getName())
		);
		
		send(informMsg);
		logger.log(Level.INFO, String.format("%s INFORMED VOTING REGISTRATION TO MEDIATOR!", getLocalName()));
	}
}
