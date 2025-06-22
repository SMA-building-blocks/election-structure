package election_structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class Ballot extends BaseAgent {

	private Hashtable<AID, Types> registeredVoters;
	private Hashtable<Integer, AID> registeredCandidates;

	private Hashtable<Integer, Map<Types, Integer>> receivedVotes;
	private Hashtable<Types, Integer> votingWeights;

	private AtomicInteger receivedVotesCnt;

	private Boolean votesCollected = false;

	private final transient Object lock = new Object();

    @Override
    protected void setup() {

        logger.log(Level.INFO, "Starting Ballot...");

        logger.log(Level.INFO, "I'm the ballot!");
		this.registerDF(this, "Ballot", "ballot");

		receivedVotes = new Hashtable<>();

		receivedVotesCnt = new AtomicInteger(0);
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

				} else if (msg.getContent().startsWith(Integer.toString(votingCode))) {
					Types voterType = registeredVoters.get(msg.getSender());
					int vote = Integer.parseInt(splittedMsg[1]);

					if(!registeredCandidates.containsKey(vote)) vote = -1;
					
					synchronized(lock){
						Map<Types, Integer> updateMap = receivedVotes.get(vote);

						if (updateMap == null) updateMap = new EnumMap<>(Types.class);
						
						updateMap.put(voterType, updateMap.get(voterType) == null? 1 : updateMap.get(voterType) + 1);
						receivedVotes.put(vote, updateMap);
						
						if(receivedVotesCnt.incrementAndGet() == 1){
							addBehaviour(timeoutBehaviour("collectVotes", TIMEOUT_LIMIT*3));
						}
						
						if(receivedVotesCnt.get() == registeredVoters.size()){
							votesCollected = true;
							computeResults();
						}
					}
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
				if ( motivation.equals("collectVotes") && !votesCollected) {
					votesCollected = true;
					logger.log(Level.WARNING,
							String.format("%s Agent voting time window ended! %s", ANSI_YELLOW, ANSI_RESET));
					computeResults();
				}
			}
		};
	}

	private void setupBallot () {
		receivedVotes = new Hashtable<>();
		registeredCandidates = new Hashtable<>();
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
						registeredCandidates.put( Integer.parseInt(el.getType()), voter.getName());
					}
				}
			}
		} catch ( Exception e ) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE PERFORMING BALLOT SETUP %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}

		if ( registeredCandidates.isEmpty() || registeredVoters.size() < 2 ) {
			logger.log(Level.WARNING, String.format("%s THERE CANNOT BE AN ELECTION WITH NO CANDIDATES OR NOT ENOUGH QUORUM %s", ANSI_YELLOW, ANSI_RESET));
			
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			
			ArrayList<DFAgentDescription> foundMediators = findMediators( new String[]{ Integer.toString(votingCode) } );
			if ( !foundMediators.isEmpty() ) {
				for ( DFAgentDescription fndMed : foundMediators ) {
					msg.addReceiver(fndMed.getName());
				}
			}

			msg.setContent(String.format("FAILURE %d", votingCode));
			send(msg);

			return;
		}

		startElection();

	}

	private void startElection () {
		try {
			logger.log(Level.INFO, String.format("%s BALLOT READY! REQUESTING ELECTION START! %s", ANSI_GREEN, ANSI_RESET));
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				
			ArrayList<DFAgentDescription> foundMediators = findMediators( new String[]{ Integer.toString(votingCode) } );
			if ( !foundMediators.isEmpty() ) {
				for ( DFAgentDescription fndMed : foundMediators ) {
					msg.addReceiver(fndMed.getName());
				}
			}

			msg.setContent(String.format("READY %d", votingCode));
			send(msg);
		} catch ( Exception e ) {
			logger.log(Level.SEVERE, String.format("%s ERROR WHILE PERFORMING BALLOT SETUP %s", ANSI_RED, ANSI_RESET));
			e.printStackTrace();
		}
	}

	private void computeResults() {
		HashMap<Integer, Integer> results = new HashMap<>();

		int sum;

		int maxVote = -1;

		ArrayList<Integer> winnerCandidates = new ArrayList<>();

		for(Map.Entry<Integer, Map<Types, Integer>> entry : receivedVotes.entrySet()){
			sum = 0;
			Map<Types, Integer> votesCount = entry.getValue();

			for(Map.Entry<Types, Integer> entry2 : votesCount.entrySet()){
				sum += votingWeights.get(entry2.getKey()) * entry2.getValue();
			}

			results.put(entry.getKey(), sum);
			
			if(sum == maxVote){
				winnerCandidates.add(entry.getKey());
			}
			
			if(sum > maxVote){
				maxVote = sum;
				winnerCandidates.clear();
				winnerCandidates.add(entry.getKey());
			}

		}
	
		ACLMessage msg = new ACLMessage(ACLMessage.INFORM);

		msg.addReceiver(findMediators(new String[]{ Integer.toString(votingCode) }).get(0).getName());

		StringBuilder strBld = new StringBuilder();
		for ( Integer winner : winnerCandidates ) {
			strBld.append(String.format("%d ", winner));
		}

		String winners = strBld.toString().trim();
		msg.setContent(String.format("%s WinnersCount %d VotesCount %d Winners %s", "RESULTS", winnerCandidates.size(), maxVote, winners));
		send(msg);

		strBld.setLength(0);
		for ( Map.Entry<Integer,Integer> entry : results.entrySet() ) {
			strBld.append(String.format("%d %d ", entry.getKey(), entry.getValue()));
		}

		String voteLog = strBld.toString().trim();
		msg.setContent(String.format("%s Size %d %s", "ELECTIONLOG", results.size(),voteLog));
		send(msg);

	}
}
