/**
 * 
 */
package framework;

import java.util.ArrayList;

import com.hp.hpl.jena.rdf.model.Selector;

/**
 * @author Ben Griffiths
 *
 */
public interface Clue {
	public String getSourceClue(); // returns the original text String used to initialise the Clue object
	public void setSourceClue(String sourceClue);
	public ArrayList<String> getClueVariations(); // returns an arrayList of Strings representing variations of the original text String
	public ArrayList<Selector> getSelectorVariations(); // returns an arrayList of Selectors representing parsed versions of the clueVariations
	public void setSelectorVariations(ArrayList<Selector> selectorVariations);
	public int[] getSolutionStructure();
	public void setSolutionStructure(int[] solutionStructure);
	public String getSolutionStructureAsString();
	public boolean matchesStructure(Solution solution); // compares the structure of this clue with the Solution argument
}
