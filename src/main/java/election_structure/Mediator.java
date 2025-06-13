package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Stack;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.UnexpectedArgumentCount;
import jade.lang.acl.ACLMessage;

public class Mediator extends BaseAgent {

	private static final long serialVersionUID = 1L;
	private static final int MAX_VOTING_CODE = 9999;

	private int registeredQuorum = 0;
	private int totalQuorum = 0;
	private Boolean ballotCreated = false;
	
	private Hashtable<AID, Integer> votingLog;
	private Hashtable<AID, Candidature> candidatures;
	private ArrayList<AID> winners;
	private ArrayList<AID> candidates;
	private Stack<Integer> candidateCodes;

	@Override
	protected void setup() {

		logger.log(Level.INFO, "I'm the mediator!");
		this.registerDF(this, "Mediator", "mediator");
		
		addBehaviour(handleMessages());
	}
	
	@Override
	protected OneShotBehaviour handleInform ( ACLMessage msg ) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action () {
				String [] splittedMsg = msg.getContent().split(" ");

				if (msg.getContent().startsWith(START)) {
					// send them a message requesting for a number
					
					votingCode = votingCodeGenerator();
					
					votingLog = new Hashtable<>();
					winners = new ArrayList<>();

					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
					
					registeredQuorum = 0;

					logger.log(Level.INFO, String.format("%s AGENT GENERATED ELECTION WITH CODE %d!", getLocalName(), votingCode));
					
					ACLMessage msg2 = msg.createReply();

					msg2.setContent(String.format("VOTEID %d", votingCode));

					send(msg2);
					logger.log(Level.INFO,  String.format("%s SENT ELECTION CODE TO %s", getLocalName(), msg.getSender().getLocalName()));
					
					candidatures = new Hashtable<>();
					candidates = new ArrayList<>();

					genCandidateCodes();

				} else if ( msg.getContent().startsWith(INFORM) ) {

					if (splittedMsg[1].equals(QUORUM)) {
						addBehaviour(timeoutBehaviour( "registration", TIMEOUT_LIMIT));
						totalQuorum = Integer.parseInt(splittedMsg[2]);
					}

				} else if ( msg.getContent().startsWith(REGISTERED) ) {
					if( splittedMsg[2].startsWith(Integer.toString(votingCode)) )
						registeredQuorum++;

					if ( msg.getContent().endsWith("CANDIDATURE") ) {
						candidates.add(msg.getSender());
					}

					if ( (registeredQuorum == totalQuorum) && (candidates.isEmpty()) ) {
						

					} 

				} else {
					logger.log(Level.INFO, 
							String.format("%s RECEIVED AN UNEXPECTED MESSAGE FROM %s", getLocalName(), msg.getSender().getLocalName()));
				}
			}
		};
	}

	@Override
	protected OneShotBehaviour handleRequest(ACLMessage msg) {
		return new OneShotBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action() {
				String [] splittedMsg = msg.getContent().split(" ");
				if ( splittedMsg[0].equals(REQUEST) ) {
					if ( splittedMsg[2].equals("candidateCode") ) {
						ACLMessage msg2 = msg.createReply();
						msg2.setPerformative(ACLMessage.INFORM);

						int candidateCode = candidateCodes.pop();

						msg2.setContent(String.format("CANDIDCODE %d",candidateCode));
						send(msg2);
					}
				} else if ( splittedMsg[0].equals(CANDIDATURE) ) {
					int candidateCode = Integer.parseInt(splittedMsg[2]);
					StringBuilder bldProposal = new StringBuilder();

					for ( int i = 4; i < splittedMsg.length; ++i )
						bldProposal.append(splittedMsg[i] + " ");

					String proposal = bldProposal.toString().trim();

					Candidature newCand = new Candidature(candidateCode, proposal);

					candidatures.put(msg.getSender(), newCand);

					candidates.remove(msg.getSender());

					if ( (registeredQuorum == totalQuorum) && (candidates.isEmpty()) ) {
						

					} 

					logger.log(Level.INFO, String.format("%s %s REGISTERED AS CANDIDATE WITH CODE %d AND PROPOSAL: '%s'! %s", ANSI_BLUE, msg.getSender().getLocalName(), candidateCode, proposal, ANSI_RESET));
				} else {
					logger.log(Level.INFO, 
							String.format("%s RECEIVED AN UNEXPECTED MESSAGE FROM %s", getLocalName(), msg.getSender().getLocalName()));
				}
			}
		};
	}

	@Override
	protected WakerBehaviour timeoutBehaviour( String motivation, long timeout) {
		return new WakerBehaviour(this, timeout) {
			private static final long serialVersionUID = 1L;

			@Override
			protected void onWake() {
				if ( motivation.equals("registration") ) {
					logger.log(Level.WARNING,
						String.format("%s Agent registration timed out! %s", ANSI_YELLOW, ANSI_RESET));

					createBallot();
				}
			}
		};
	}
	
	private void informWinner(){
		
	}

	protected void resetVoting(Agent myAgent){

	}

	private void computeResults() {

	}
	
	private void requestVotes() {
		try {
			ACLMessage requestVoteMsg = new ACLMessage(ACLMessage.REQUEST);
			requestVoteMsg.setContent(String.format("%s VOTE FOR %d", REQUEST, votingCode));
			
			ArrayList<DFAgentDescription> foundVotingParticipants;

			String [] types = { Integer.toString(votingCode), "voter" };

			foundVotingParticipants = new ArrayList<>(
					Arrays.asList(searchAgentByType(types)));
			
			if ( foundVotingParticipants.size() != registeredQuorum ) {
				throw new UnexpectedArgumentCount();
			}
			
			foundVotingParticipants.forEach(ag -> 
				requestVoteMsg.addReceiver(ag.getName())
			);
			
			send(requestVoteMsg);
			logger.log(Level.INFO, 
					String.format("%s REQUESTED A VOTE FOR ALL %d VOTERS!", getLocalName(), foundVotingParticipants.size()));
		} catch (Exception e) {
			logger.log(Level.SEVERE, String.format("%s FOUND VOTERS DIFFERS FROM REGISTERED QUORUM! %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}
	
	private int votingCodeGenerator () {
		int proposedCode;
		DFAgentDescription [] foundAgents;
		
		do {
			proposedCode = rand.nextInt(MAX_VOTING_CODE);
			
			foundAgents = searchAgentByType(Integer.toString(proposedCode));
		} while ( foundAgents.length > 0 );

		return proposedCode;
	}

	private void createBallot(){


	}

	private void genCandidateCodes() {
		candidateCodes =  new Stack<>();

		for (int i = 1; i <= MAX_VOTING_CODE; i++) {
			candidateCodes.push(i);
		}
		Collections.shuffle(candidateCodes);
	}
}
