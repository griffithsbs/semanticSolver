package prototype;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * @author Ben Griffiths
 *
 */
public class SemanticSolverRunner {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		/* log4j logging configuration */
		Logger.getRootLogger().setLevel(Level.INFO);
		PropertyConfigurator.configure("log4j.properties");
		
		UserInterface ui = new SimpleUserInterface();
		ui.createAndShow();
	}

}
