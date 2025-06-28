package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class Mediator extends BaseAgent {

	private static final long serialVersionUID = 1L;
	private static final int MAX_VOTING_CODE = 9999;

	private int registeredQuorum = 0;
	private int totalQuorum = 0;
	private Boolean ballotCreated = false;
	private Boolean ballotRequested = false;

	private transient Hashtable<AID, Candidature> candidatures;
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

				switch ( splittedMsg[0] ) {
					case START:
						createElection(this.myAgent, msg);
						break;
					case INFORM:
						if (splittedMsg[1].equals(QUORUM)) {
							addBehaviour(timeoutBehaviour( "registration", TIMEOUT_LIMIT));
							totalQuorum = Integer.parseInt(splittedMsg[2]);
						}
						break;
					case REGISTERED:
						verifyElectionStatus(msg, splittedMsg); 
						break;
					case CHECK:
						setupBallot(msg);
						break;
					case FAILURE:
						resetElection(myAgent);
						break;
					case READY:
						startElection(splittedMsg);
						break;
					case RESULTS:
						processElectionResults(msg, splittedMsg);
						break;
					case ELECTIONLOG:
						logger.log(Level.INFO, String.format("%s %s %s", ANSI_PURPLE, msg.getContent(), ANSI_RESET));
						resetElection(myAgent);
						break;
					default:
						logger.log(Level.INFO, 
							String.format("%s RECEIVED AN UNEXPECTED MESSAGE FROM %s", getLocalName(), msg.getSender().getLocalName()));
						break;
				}
			}
		};
	}

	private void processElectionResults(ACLMessage msg, String[] splittedMsg) {
		informWinner(msg.getContent());

		String winnersCnt = splittedMsg[2];
		String winnersVote = splittedMsg[4];

		
		StringBuilder bld = new StringBuilder();
		
		for(int i = 6; i < splittedMsg.length; i++){
			bld.append(splittedMsg[i] + " ");
		}
		String winnersCode = bld.toString();
		
		String results = String.format(" ELECTION RESULTS FOR VOTING %d: %n", votingCode);
		results = results.concat(String.format(" \t\tWinner count: %s%n",winnersCnt));
		results = results.concat(String.format(" \t\tWinner received votes %s%n", winnersVote));
		results = results.concat(String.format(" \t\tWinner Codes: %s ", winnersCode));

		logger.log(Level.INFO, String.format("%s%s%s", ANSI_PURPLE, results, ANSI_RESET));
	}

	private void startElection(String[] splittedMsg) {
		try {
			int votCode = Integer.parseInt(splittedMsg[1]);

			if ( votCode == votingCode ) {
				ArrayList<DFAgentDescription> foundVotingParticipants;
				String [] types = { Integer.toString(votingCode), VOTER };
	
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
			} else {
				throw new IllegalArgumentException("Voting code does not match!");
			}
		} catch ( Exception e ) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE PERFORMING BALLOT SETUP %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}

	private void setupBallot(ACLMessage msg) {
		ballotCreated = true;
		ACLMessage msg2 = msg.createReply();

		StringBuilder strBld = new StringBuilder();
		for ( Map.Entry<Types,Integer> entry : votingWeights.entrySet() ) {
			strBld.append(String.format("%s %d ", entry.getKey().toString(), entry.getValue()));
		}

		msg2.setContent(String.format("VOTEID %d WEIGHTS %d %s", votingCode, votingWeights.size(), strBld.toString().trim()));
		send(msg2);
	}

	private void verifyElectionStatus(ACLMessage msg, String[] splittedMsg) {
		if( splittedMsg[2].startsWith(Integer.toString(votingCode)) )
			registeredQuorum++;

		if ( msg.getContent().endsWith(CANDIDATURE) ) {
			preCandidates.add(msg.getSender());
		}

		if ( (registeredQuorum == totalQuorum) && (preCandidates.isEmpty()) && Boolean.FALSE.equals(ballotRequested)) {
			createBallot();
		}
	}

	private void createElection(Agent myAgent, ACLMessage msg) {
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

					if ( (registeredQuorum == totalQuorum) && (preCandidates.isEmpty()) && Boolean.FALSE.equals(ballotRequested)) {
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
				if ( votingCode != -1 && motivation.equals("registration") && Boolean.FALSE.equals(ballotCreated) ) {
						logger.log(Level.WARNING,
							String.format("%s Agent registration timed out! %s", ANSI_YELLOW, ANSI_RESET));
						createBallot();
				} else if ( votingCode != -1 && motivation.equals("Create-Ballot") && Boolean.FALSE.equals(ballotCreated) ){
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
		String [] types = { Integer.toString(votingCode), VOTER };

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

	protected void resetElection(Agent myAgent){
		registeredQuorum = 0;
		totalQuorum = 0;
		ballotCreated = false;
		ballotRequested = false;

		candidatures = new Hashtable<>();
		preCandidates = new ArrayList<>();
		candidateCodes = new Stack<>();

		deleteAgentServices(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
		deleteAgentServices(myAgent, VOTER, "Candidate");

		votingCode = -1;

		logger.log(Level.WARNING, ANSI_YELLOW + "VOTING ENDED!" + ANSI_RESET);
	}

	private void deleteAgentServices(Agent myAgent, String searchAttr, String deleteCondition) {
		DFAgentDescription[] dfd = searchAgentByType(searchAttr);

		for (int i = 0; i < dfd.length; i++) {
			Iterator<ServiceDescription> it = dfd[i].getAllServices();

			ServiceDescription sd;
			while (it.hasNext()) {
				sd = it.next();
				if( sd.getType().equals(deleteCondition) || sd.getName().equals(deleteCondition) ){
					dfd[i].removeServices(sd);
					break;
				}
			}
			
			try {
				DFService.modify(myAgent, dfd[i]);
			} catch (FIPAException e) {
				logger.log(Level.SEVERE, ANSI_RED + "ERROR WHILE MODIFYING AGENTS" + ANSI_RESET);
				e.printStackTrace();
			}
		}
	}


	private void setupVotingWeights () {
		votingWeights = new Hashtable<>();

		for ( Types element : Types.values() ) {
			votingWeights.put(element, rand.nextInt(1,6));
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
