package election_structure;

import java.util.logging.Level;

public class Ballot extends BaseAgent {


    @Override
    protected void setup() {

        logger.log(Level.INFO, "Starting Ballot...");

        Object[] args = getArguments();
		if (args != null && args.length > 0) 
			votingCode = Integer.parseInt(args[0].toString());

        logger.log(Level.INFO, "I'm the ballot!");
		this.registerDF(this, "Ballot", "ballot");

		
		addBehaviour(handleMessages());

        

    }

}
