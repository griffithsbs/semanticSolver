/**
 * 
 */
package prototype;

import java.util.ArrayList;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.vocabulary.RDF;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Ben Griffiths
 *
 */
public class SimpleClueParser implements ClueParser {
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) Model model;
	@Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PRIVATE) Clue clue;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) EntityRecogniser entityRecogniser;
	
	
	public SimpleClueParser(Clue clue, Model model) {
		this.setClue(clue);
		this.setModel(model);
		this.setStringVariations();
		this.setEntityRecogniser(new SimpleEntityRecogniser(clue, model));
	}
	

	@Override
	public void parse() {
		ArrayList<Selector> selectorVariations = new ArrayList<Selector>();
		
		
		printRecognisedEntities(); // DEBUGGING LINE - TO BE REMOVED
		
		ArrayList<Resource> recognisedSubjects = this.getEntityRecogniser().getRecognisedSubjects();
		ArrayList<Property> recognisedProperties = this.getEntityRecogniser().getRecognisedProperties();
		ArrayList<Resource> recognisedObjects = this.getEntityRecogniser().getRecognisedObjects();
		
		for(Property predicate: recognisedProperties) {
			for(Resource subject : recognisedSubjects) { // Add all combinations of recognised subjects and properties
				Selector selector = new SimpleSelector(subject, predicate, (RDFNode)null);
				selectorVariations.add(selector);
			}
			for(Resource object : recognisedObjects) { // Add all combinations of recognised properties and objects
				Selector selector = new SimpleSelector(null, predicate, object);
				selectorVariations.add(selector);
			}
		}
		this.getClue().setSelectorVariations(selectorVariations);
	}

	/**
	 * CURRENTLY DOES NOTHING
	 * setStringVariations - breaks down the source clue into variations of the original String, and sets
	 * the Clue objects string variations
	 */
	private void setStringVariations() {
		String sourceClue = this.getClue().getSourceClue();
		String[] sourceClueFragments = sourceClue.split(" ");
	}
	
	/*
	 * FOR DEBUGGING - TO BE REMOVED
	 */
	private void printRecognisedEntities() {
		ArrayList<Resource> subjects = this.getEntityRecogniser().getRecognisedSubjects();
		ArrayList<Property> predicates = this.getEntityRecogniser().getRecognisedProperties();
		ArrayList<Resource> objects = this.getEntityRecogniser().getRecognisedObjects();
		
		System.out.println("Recognised subjects:");
		for(Resource subject : subjects)
			System.out.println(subject.toString());
		
		System.out.println("Recognised properties:");
		for(Property predicate: predicates)
			System.out.println(predicate.toString());
		
		System.out.println("Recognised objects:");
		for(Resource object : objects)
			System.out.println(object.toString());
	}
}
