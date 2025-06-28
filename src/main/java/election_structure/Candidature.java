package election_structure;

public class Candidature {
    int candidatureNumber;
	String proposal;

    public Candidature(int candidatureNumber, String proposal){
        this.candidatureNumber = candidatureNumber;
        this.proposal = proposal;
    }

    public int getCandidatureNumber() {
        return candidatureNumber;
    }

    public String getProposal() {
        return proposal;
    }
}
