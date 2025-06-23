package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

public class Mediator extends BaseAgent {

	private static final long serialVersionUID = 1L;
	private static final int MAX_VOTING_CODE = 9999;

	private int registeredQuorum = 0;
	private int totalQuorum = 0;
	private Boolean ballotCreated = false;
	private Boolean ballotRequested = false;

	private Hashtable<AID, Candidature> candidatures;
	private ArrayList<AID> preCandidates;
	private Stack<Integer> candidateCodes;

	protected Hashtable<Types, Integer> votingWeights;

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
					
					votingCode = votingCodeGenerator();

					setupVotingWeights();
					genCandidateCodes();

					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
					
					registeredQuorum = 0;

					logger.log(Level.INFO, String.format("%s AGENT GENERATED ELECTION WITH CODE %d!", getLocalName(), votingCode));
					
					ACLMessage msg2 = msg.createReply();

					msg2.setContent(String.format("VOTEID %d", votingCode));

					send(msg2);
					logger.log(Level.INFO,  String.format("%s SENT ELECTION CODE TO %s", getLocalName(), msg.getSender().getLocalName()));
					
					candidatures = new Hashtable<>();
					preCandidates = new ArrayList<>();

					

				} else if ( msg.getContent().startsWith(INFORM) ) {
					if (splittedMsg[1].equals(QUORUM)) {
						addBehaviour(timeoutBehaviour( "registration", TIMEOUT_LIMIT));
						totalQuorum = Integer.parseInt(splittedMsg[2]);
					}

				} else if ( msg.getContent().startsWith(REGISTERED) ) {
					if( splittedMsg[2].startsWith(Integer.toString(votingCode)) )
						registeredQuorum++;

					if ( msg.getContent().endsWith("CANDIDATURE") ) {
						preCandidates.add(msg.getSender());
					}

					if ( (registeredQuorum == totalQuorum) && (preCandidates.isEmpty()) && !ballotRequested) {
						createBallot();
					} 

				} else if(msg.getContent().startsWith("CHECK")){

					ballotCreated = true;
					ACLMessage msg2 = msg.createReply();

					StringBuilder strBld = new StringBuilder();
					for ( Map.Entry<Types,Integer> entry : votingWeights.entrySet() ) {
						strBld.append(String.format("%s %d ", entry.getKey().toString(), entry.getValue()));
					}

					msg2.setContent(String.format("VOTEID %d WEIGHTS %d %s", votingCode, votingWeights.size(), strBld.toString().trim()));
					send(msg2);
				} else if ( msg.getContent().startsWith("FAILURE") ) { 
					deleteElection(Integer.parseInt(splittedMsg[1]));
				} else if ( msg.getContent().startsWith("READY") ) {
					try {
						int votCode = Integer.parseInt(splittedMsg[1]);

						if ( votCode == votingCode ) {
							startElection();
						} else {
							throw new Exception("Voting code does not match!");
						}
					} catch ( Exception e ) {
						logger.log(Level.SEVERE, String.format("%s ERROR WHILE PERFORMING BALLOT SETUP %s", ANSI_RED, ANSI_RESET));
						e.printStackTrace();
					}

				} else if(msg.getContent().startsWith("RESULTS") ) {

					informWinner(msg.getContent());
					
					System.out.println(msg.getContent());

					String winnersCnt = splittedMsg[2];
					String winnersVote = splittedMsg[4];

					String winnersCode = "";

					for(int i = 6; i < splittedMsg.length; i++){
						winnersCode += splittedMsg[i] + " ";
					}

					String results = String.format(" ELECTION RESULTS FOR VOTING %d: \n", votingCode);
					results = results.concat(String.format(" \t\tWinner count: %s\n",winnersCnt));
					results = results.concat(String.format(" \t\tWinner received votes %s\n", winnersVote));
					results = results.concat(String.format(" \t\tWinner Codes: %s ", winnersCode));

					logger.log(Level.INFO, String.format("%s%s%s", ANSI_PURPLE, results, ANSI_RESET));

				} else if(msg.getContent().startsWith("ELECTIONLOG") ) {

					logger.log(Level.INFO, String.format("%s %s %s", ANSI_PURPLE, msg.getContent(), ANSI_RESET));

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

					preCandidates.remove(msg.getSender());

					if ( (registeredQuorum == totalQuorum) && (preCandidates.isEmpty()) && !ballotRequested) {
						createBallot();
					} 

					logger.log(Level.INFO, String.format("%s %s REGISTERED AS CANDIDATE WITH CODE %d AND PROPOSAL: '%s'! %s", ANSI_PURPLE, msg.getSender().getLocalName(), candidateCode, proposal, ANSI_RESET));
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
				if ( motivation.equals("registration") && !ballotCreated) {
						logger.log(Level.WARNING,
							String.format("%s Agent registration timed out! %s", ANSI_YELLOW, ANSI_RESET));
						createBallot();
				} else if (motivation.equals("Create-Ballot") && !ballotCreated){
						logger.log(Level.WARNING,
							String.format("%s Ballot creation timed out! %s", ANSI_YELLOW, ANSI_RESET));
						createBallot();
				}
			}
		};
	}
	
	private void informWinner(String content){
		ACLMessage msg2 = new ACLMessage(ACLMessage.INFORM);
					
		ArrayList<DFAgentDescription> foundVotingParticipants;
		String [] types = { Integer.toString(votingCode), "voter" };

		foundVotingParticipants = new ArrayList<>(
				Arrays.asList(searchAgentByType(types)));

		foundVotingParticipants.forEach(vot -> 
			msg2.addReceiver(vot.getName())
		);

		msg2.setContent(content);
		send(msg2);

		logger.log(Level.INFO, 
					String.format("%s %s SENT ELECTION RESULTS TO ALL VOTERS! %s", ANSI_PURPLE , getLocalName(), ANSI_RESET));
	}

	protected void resetVoting(Agent myAgent){
		/*
		 * TODO: when finished election reset all data
		 */
	}

	private void deleteElection (int receivedVotingCode) {
		logger.log(Level.INFO, String.format("%s DELETING ELECTION WITH CODE %d %s", ANSI_CYAN, receivedVotingCode, ANSI_RESET));

		/*
		 * HERE, WE SHOULD IMPLEMENT VOTING DELETION LOGIC
		 */
	}

	private void setupVotingWeights () {
		votingWeights = new Hashtable<>();

		for ( Types element : Types.values() ) {
			votingWeights.put(element, rand.nextInt(1,6));
		}
	}
	
	private void startElection() {
		try {
			ArrayList<DFAgentDescription> foundVotingParticipants;
			String [] types = { Integer.toString(votingCode), "voter" };

			foundVotingParticipants = new ArrayList<>(
					Arrays.asList(searchAgentByType(types)));

			informCandidatesProposals(foundVotingParticipants);

			ACLMessage requestVoteMsg = new ACLMessage(ACLMessage.REQUEST);
			requestVoteMsg.setContent(String.format("%s VOTE FOR %d WITH %d CANDIDATES", REQUEST, votingCode, candidatures.size()));
			
			foundVotingParticipants.forEach(ag -> 
				requestVoteMsg.addReceiver(ag.getName())
			);
			
			send(requestVoteMsg);
			logger.log(Level.INFO, 
					String.format("%s REQUESTED A VOTE FOR ALL %d VOTERS!", getLocalName(), foundVotingParticipants.size()));
		} catch ( Exception e ) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE INFORMING ELECTION START TO VOTERS! %s", ANSI_RED, ANSI_RESET));
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
		ACLMessage reqAgentMsg = new ACLMessage(ACLMessage.REQUEST);
		reqAgentMsg.setContent(String.format("%s %s", CREATE, "Ballot"));
		reqAgentMsg.addReceiver(searchAgentByType(CREATOR)[0].getName());
		send(reqAgentMsg);
		ballotRequested = true;
		addBehaviour(timeoutBehaviour( "Create-Ballot", TIMEOUT_LIMIT));
	}

	private void genCandidateCodes() {
		candidateCodes =  new Stack<>();

		for (int i = 1; i <= MAX_VOTING_CODE; i++)
			candidateCodes.push(i);
		
		Collections.shuffle(candidateCodes);
	}

	private void informCandidatesProposals ( ArrayList<DFAgentDescription> foundVotingParticipants ) {
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
		foundVotingParticipants.forEach(vot -> 
			msg.addReceiver(vot.getName())
		);

		for ( Map.Entry<AID, Candidature> entry : candidatures.entrySet() ) {
			Candidature cdtr = entry.getValue();
			String content = String.format("CANDIDATE %d %s %s", cdtr.candidatureNumber, PROPOSAL, cdtr.proposal);

			msg.setContent(content);
			send(msg);
		}

		logger.log(Level.INFO, 
					String.format("%s %s SENT CANDIDATES' PROPOSALS TO ALL VOTERS! %s", ANSI_PURPLE , getLocalName(), ANSI_RESET));
	}
}
