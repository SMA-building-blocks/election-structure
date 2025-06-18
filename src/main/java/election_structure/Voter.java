package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

public class Voter extends BaseAgent {

	private static final long serialVersionUID = 1L;

	private Types myVotingType = Types.COMMON_VOTER;

	private Hashtable<String, String> recvProposals;
	private int candidatesCount; 

	@Override
	protected void setup() {
		addBehaviour(handleMessages());
		logger.log(Level.INFO,String.format("I'm voter: %s", this.getLocalName()));

		List<Types> possibleTypes = Arrays.asList(Types.values());

		int randomIndex = rand.nextInt(possibleTypes.size());
		myVotingType = possibleTypes.get(randomIndex);
		
		this.registerDF(this, "Voter", "voter");

		this.registerDF(this, myVotingType.toString(), myVotingType.toString());

		if ( !randomAgentMalfunction || rand.nextInt(11) != 10 ) {
			logger.log(Level.INFO, String.format("I'm the %s!", getLocalName()));
		} else {
			brokenAgent = true;
			logger.log(Level.WARNING,
				String.format("%s I'm agent %s and I have a malfunction! %s", ANSI_CYAN, getLocalName(), ANSI_RESET));
		}

		if ( rand.nextInt(11) <= 5 ) {
			logger.log(Level.INFO, String.format("I'm the %s!", getLocalName()));
		} else {
			candidate = true;
			logger.log(Level.WARNING,
				String.format("%s I'm agent %s and I'll candidate myself! %s", ANSI_CYAN, getLocalName(), ANSI_RESET));
		}
	}
	
	@Override
	protected OneShotBehaviour handleInform ( ACLMessage msg ) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action () {
				String [] splittedMsg = msg.getContent().split(" ");

				if (msg.getContent().startsWith(START)) {
					ACLMessage msg2 = new ACLMessage(ACLMessage.INFORM);
					msg2.setContent(START);

					ArrayList<DFAgentDescription> foundAgents = findMediators( new String[]{} );
					
					try {
						AID foundMediator = null;
						if ( foundAgents.size() > 0 ) {
							foundMediator = foundAgents.get(0).getName();
							
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
							String.format("RECEIVED ELECTION ID FROM %s: %s", msg.getSender().getLocalName(), msg.getContent()));
					
					votingCode = Integer.parseInt(splittedMsg[1]);
					recvProposals = new Hashtable<>();
					candidatesCount = 0;
					
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
					
					if ( candidate ) 
						requestCandidateCode();
					
				} else if (msg.getContent().startsWith(INVITE)) {
					logger.log(Level.INFO, 
							String.format("RECEIVED ELECTION INFO FROM %s: %s", msg.getSender().getLocalName(), msg.getContent()));		

					votingCode = Integer.parseInt(splittedMsg[2]);
					recvProposals = new Hashtable<>();
					candidatesCount = 0;
					
					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
					
					informVotingRegistration();

					if ( candidate ) 
						requestCandidateCode();

				} else if ( msg.getContent().startsWith("CANDIDCODE") ) {
					registerCandidature(myAgent, Integer.parseInt(splittedMsg[1]), msg);
				} else if ( msg.getContent().startsWith("CANDIDATE") ) { 
					String prop = msg.getContent().substring(msg.getContent().indexOf(PROPOSAL) + PROPOSAL.length() + 1); 

					recvProposals.put(splittedMsg[1], prop);
					candidatesCount++;
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
				String [] splittedMsg = msg.getContent().split(" ");

				if (msg.getContent().startsWith(REQUEST)) {	
					ArrayList<String> candidateCodes = new ArrayList<>();

					for ( int i = 6; i < splittedMsg.length; ++i )
						candidateCodes.add(splittedMsg[i]);
					
					String [] types = { Integer.toString(votingCode), "voter" };
					ArrayList<DFAgentDescription> foundElectionCandidates = new ArrayList<>(
							Arrays.asList(searchAgentByType(types)));

					/*
					 * CRIAR LÓGICA DE ENVIO DE VOTOS AO RECEBIMENTO DE TODAS AS PROPOSTAS
					 */
					
					logger.log(Level.INFO,  String.format("%s SENT VOTE TO %s", getLocalName(), msg.getSender().getLocalName()));
				} else {
					logger.log(Level.INFO, 
							String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG, msg.getSender().getLocalName()));
				}
			}
		};
	}

	private void informVotingRegistration() {
		ACLMessage informMsg = new ACLMessage(ACLMessage.INFORM);


		String sendMsg = String.format("%s IN %d", REGISTERED, votingCode);

		if ( candidate == true )
			sendMsg += " AND CANDIDATE";

		informMsg.setContent(sendMsg);

		ArrayList<DFAgentDescription> foundMediators = findMediators( new String[]{ Integer.toString(votingCode) } );
		
		foundMediators.forEach(ag -> 
			informMsg.addReceiver(ag.getName())
		);
		
		send(informMsg);
		logger.log(Level.INFO, String.format("%s INFORMED ELECTION REGISTRATION TO MEDIATOR!", getLocalName()));
	}

	private void requestCandidateCode() {
		ACLMessage requestMsg = new ACLMessage(ACLMessage.REQUEST);
		requestMsg.setContent(String.format("%s %d %s", REQUEST, votingCode, "candidateCode"));

		ArrayList<DFAgentDescription> foundMediators = findMediators( new String[]{ Integer.toString(votingCode) } );
		
		foundMediators.forEach(ag -> 
			requestMsg.addReceiver(ag.getName())
		);
		
		send(requestMsg);
		logger.log(Level.INFO, String.format("%s REQUESTED CANDIDATE CODE TO MEDIATOR!", getLocalName()));
	}

	private void registerCandidature(Agent myAgent, int candidateCode, ACLMessage msg){
		int proposalLen = rand.nextInt(DEFAULT_PROPOSAL.length());

		String proposal = DEFAULT_PROPOSAL.substring(0, proposalLen);

		registerDF(myAgent, "Candidate", Integer.toString(candidateCode));

		logger.log(Level.INFO, String.format("%s REGISTERED AS CANDIDATE!", getLocalName()));

		String sendContent = String.format("%s CODE %d PROPOSAL %s", CANDIDATURE, candidateCode, proposal);

		ACLMessage msg2 = msg.createReply();
		msg2.setPerformative(ACLMessage.REQUEST);
		msg2.setContent(sendContent);
		send(msg2);
	}
}
