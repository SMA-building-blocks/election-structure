package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.UnexpectedArgumentCount;
import jade.lang.acl.ACLMessage;

public class Mediator extends BaseAgent {

	private static final long serialVersionUID = 1L;
	private static final int MAX_VOTING_CODE = 9999;
	private static final int MIN_VOTING_VALUE = 1;
	private static final int MAX_VOTING_VALUE = 100;
	
	private int votingAnswer = 0;
	private int registeredQuorum = 0;
	private int totalQuorum = 0;
	
	private Hashtable<AID, Integer> votingLog;
	private ArrayList<AID> winners;

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
				if (msg.getContent().startsWith(START)) {
					// send them a message requesting for a number
					
					votingCode = votingCodeGenerator();
					
					votingLog = new Hashtable<>();
					winners = new ArrayList<>();
					
					setAns();

					registerDF(myAgent, Integer.toString(votingCode), Integer.toString(votingCode));
					
					registeredQuorum = 0;

					logger.log(Level.INFO, String.format("%s AGENT GENERATED VOTING WITH CODE %d!", getLocalName(), votingCode));
					
					ACLMessage msg2 = msg.createReply();

					msg2.setContent(String.format("VOTEID %d MINVALUE %d MAXVALUE %d", votingCode, MIN_VOTING_VALUE, MAX_VOTING_VALUE));

					send(msg2);
					logger.log(Level.INFO,  String.format("%s SENT VOTING CODE TO %s", getLocalName(), msg.getSender().getLocalName()));
				} else if ( msg.getContent().startsWith(INFORM) ) {
					String [] splittedMsg = msg.getContent().split(" ");
					
					totalQuorum = Integer.parseInt(splittedMsg[2]);
					
					logger.log(Level.INFO, String.format("EXPECTED QUORUM BY %s: %d VOTERS!", getLocalName(), totalQuorum));
				} else if ( msg.getContent().startsWith(REGISTERED) ) { 
					++registeredQuorum;
					
					if ( registeredQuorum == totalQuorum ) {
						logger.log(Level.INFO, "TOTAL QUORUM REACHED! REQUESTING VOTES!");
						
						requestVotes();
					}
				} else if ( msg.getContent().startsWith(VOTE) ) {
					int voteValue = Integer.parseInt(msg.getContent().split(" ")[3]);
					votingLog.put(msg.getSender(), voteValue);
					logger.log(Level.INFO, String.format("%s RECEIVED VOTE FROM %s!", getLocalName(), msg.getSender().getLocalName()));
					
					if ( votingLog.size() == registeredQuorum ){
						computeResults();
						informWinner();
						resetVoting(myAgent);
					}
				} else if (msg.getContent().startsWith(THANKS)){
					logger.log(Level.INFO, "RECEIVED THANKS");
				} else {
					logger.log(Level.INFO, 
							String.format("%s RECEIVED AN UNEXPECTED MESSAGE FROM %s", getLocalName(), msg.getSender().getLocalName()));
				}
			}
		};
	}
	
	private void informWinner(){
		ACLMessage informMsg = new ACLMessage(ACLMessage.INFORM);
		
		ArrayList<DFAgentDescription> foundVotingParticipants;

		String [] types = { Integer.toString(votingCode), "voter" };

		foundVotingParticipants = new ArrayList<>(
			Arrays.asList(searchAgentByType(types)));
			
		foundVotingParticipants.forEach(ag -> 
			informMsg.addReceiver(ag.getName()));

		StringBuilder bld = new StringBuilder();
			
		for (int i =0; i<winners.size(); i++){
			bld.append(String.format("%s VOTED: %d ", winners.get(i).getName(), votingLog.get(winners.get(i))));
		}
			
		String winnersName = bld.toString();
		String content = String.format("%s TOTAL-WINNERS %d RIGHT-ANSWER %d VOTING-CODE %d: %s", (winners.size()>1? DRAW: WINNER), winners.size(), votingAnswer, votingCode, winnersName);
		informMsg.setContent(content);

		logger.log(Level.INFO, String.format("%s %s %s", ANSI_GREEN, content, ANSI_RESET));
		
		send(informMsg);
		logger.log(Level.INFO, String.format("%s INFORMED WINNERS", getLocalName()));
	}

	protected void resetVoting(Agent myAgent){

		winners.clear();
		votingLog.clear();
		
		totalQuorum = 0;
		votingAnswer = 0;
		registeredQuorum = 0;

		DFAgentDescription[] dfd = searchAgentByType(Integer.toString(votingCode));

		for (int i =0; i<dfd.length; i++) {
			Iterator<ServiceDescription> it = dfd[i].getAllServices();
			while(it.hasNext()) {
				ServiceDescription sd = it.next();
				if(sd.getType().equals(Integer.toString(votingCode))){
					dfd[i].removeServices(sd);
					break;
				}
			}
			
			try {
				DFService.modify(myAgent, dfd[i]);
			} catch (FIPAException e) {
				logger.log(Level.SEVERE, ANSI_RED + "ERROR WHILE modifing agents" + ANSI_RESET);
				e.printStackTrace();
			}
		}

		votingCode = 0;

		logger.log(Level.WARNING, ANSI_YELLOW + "VOTING ENDED!" + ANSI_RESET);
	}

	private void computeResults() {
		int voteDist = 0;
		int minVoteDist = Integer.MAX_VALUE;
		
		Enumeration<AID> agents = votingLog.keys();
		
		while (agents.hasMoreElements()) {
			AID currAg = agents.nextElement();
			voteDist = calcVoteDistance(votingLog.get(currAg));
			
			if ( voteDist == minVoteDist ) winners.add(currAg);
			else if ( voteDist < minVoteDist ) {
				minVoteDist = voteDist;
				winners.clear();
				winners.add(currAg);
			}
		}
	}
	
	private int calcVoteDistance(int vote) {
		return Math.abs(votingAnswer - vote);
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

	private void setAns(){
		votingAnswer = rand.nextInt(MIN_VOTING_VALUE, MAX_VOTING_VALUE + 1);
	}
}
