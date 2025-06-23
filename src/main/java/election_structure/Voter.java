package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

public class Voter extends BaseAgent {

	private static final long serialVersionUID = 1L;

	private Types myVotingType = Types.COMMON_VOTER;

	private Hashtable<String, String> recvProposals;
	private int candidatesCount; 
	private int candidatesExpected;
	private int selectionMethod;
	private int myCandidatureCode = -1;;

	@Override
	protected void setup() {
		addBehaviour(handleMessages());
		logger.log(Level.INFO,String.format("I'm voter: %s", this.getLocalName()));

		List<Types> possibleTypes = Arrays.asList(Types.values());

		int randomIndex = rand.nextInt(possibleTypes.size());
		myVotingType = possibleTypes.get(randomIndex);
		
		this.registerDF(this, "Voter", "voter");

		this.registerDF(this, myVotingType.toString(), myVotingType.toString());

		if ( rand.nextInt(11) <= 5 ) {
			logger.log(Level.INFO, String.format("I'm the %s!", getLocalName()));
		} else {
			candidate = true;
			logger.log(Level.WARNING,
				String.format("%s I'm agent %s and I'll candidate myself! %s", ANSI_CYAN, getLocalName(), ANSI_RESET));
		}

		selectionMethod = rand.nextInt(2);
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
						if ( !foundAgents.isEmpty() ) {
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
					myCandidatureCode = -1;
					
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
					myCandidatureCode = Integer.parseInt(splittedMsg[1]);
					registerCandidature(myAgent, myCandidatureCode, msg);
				} else if ( msg.getContent().startsWith("CANDIDATE") ) { 
					String prop = msg.getContent().substring(msg.getContent().indexOf(PROPOSAL) + PROPOSAL.length() + 1); 

					recvProposals.put(splittedMsg[1], prop);
					candidatesCount++;
				} else if ( msg.getContent().startsWith("RESULTS") ) { 
					String logContent = String.format("%s RECEIVED THE RESULTS OF ELECTION %d!", getLocalName(), votingCode);

					for( int i = 6; i < splittedMsg.length; i++ ){
						if ( candidate && Integer.parseInt(splittedMsg[i]) == myCandidatureCode ) 
							logContent = String.format("%s I'M %s AND I WON THE ELECTION WITH CODE %d! %s", ANSI_GREEN, getLocalName(), votingCode, ANSI_RESET);
					}

					logger.log(Level.INFO, logContent);
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
					
					candidatesExpected = Integer.parseInt(splittedMsg[5]);

					if (candidatesCount == candidatesExpected){
						vote();
					}else{
						addBehaviour(timeoutBehaviour("recvCandidatures", TIMEOUT_LIMIT*3));
					}
					
					logger.log(Level.INFO,  String.format("%s SENT VOTE TO %s", getLocalName(), msg.getSender().getLocalName()));
				} else {
					logger.log(Level.INFO, 
							String.format("%s %s %s", getLocalName(), UNEXPECTED_MSG, msg.getSender().getLocalName()));
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
				if ( motivation.equals("recvCandidatures") ) {
					vote();
				}
			}
		};
	}

	private void informVotingRegistration() {
		ACLMessage informMsg = new ACLMessage(ACLMessage.INFORM);


		String sendMsg = String.format("%s IN %d", REGISTERED, votingCode);

		if ( candidate )
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

	private void vote(){
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

		ArrayList<DFAgentDescription> foundVotingParticipants;
		String [] types = { Integer.toString(votingCode), "ballot" };

		foundVotingParticipants = new ArrayList<>(
				Arrays.asList(searchAgentByType(types)));

		msg.addReceiver(foundVotingParticipants.get(0).getName());

		String selectedCandidate = chooseVote();
		msg.setContent(String.format("%d %s", votingCode, selectedCandidate));

		send(msg);

		logger.log(Level.INFO, String.format("%s VOTED in %s!", getLocalName(), selectedCandidate));
	}

	private String chooseVote(){
		
		String selectedCandidate = "";
		
		int max =-1;
		int min = Integer.MAX_VALUE;
		int len;
		for(Map.Entry<String,String> entry : recvProposals.entrySet()){
			len = entry.getValue().length();
			if(len > max && selectionMethod == 1){
				max = len;
				selectedCandidate = entry.getKey();
			}

			if(len < min && selectionMethod == 0){
				min = len;
				selectedCandidate = entry.getKey();
			}
		}
		
		return selectedCandidate;
	}
}
