/**
 * 
 */
package prototype;

import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import framework.EntityRecogniser;

/**
 * @author Ben Griffiths
 *
 */
public class EntityRecogniserRunnable implements Runnable {
	private final int LANGUAGE_TAG_LENGTH = 3; // DUPLICATED IN SIMPLESOLUTION CLASS - REFACTOR OUT AS A STATIC CONSTANT?
	private final String LANGUAGE_TAG = "@"; // DUPLICATED IN SIMPLESOLUTION CLASS - REFACTOR OUT AS A STATIC CONSTANT?
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private EntityRecogniser entityRecogniser;
	
	public EntityRecogniserRunnable(EntityRecogniser entityRecogniser) {
		this.setEntityRecogniser(entityRecogniser);
	}

	@Override
	public void run() {
		ResIterator subjectsInModel = this.getEntityRecogniser().getModel().listSubjects();
		while(subjectsInModel.hasNext()) {
			Resource thisSubject = subjectsInModel.nextResource();
			if(this.getEntityRecogniser().getRecognisedSubjects().contains(thisSubject))
				continue;
			OntResource subject = this.getEntityRecogniser().getOntModel().getOntResource(thisSubject); // create an OntResource from the Resource object
			ExtendedIterator<RDFNode> labels = subject.listLabels(null); // list all values of RDFS:label for this resource
			while(labels.hasNext()) {
				String thisLabel = stripLanguageTag(labels.next().toString());
				if(this.getEntityRecogniser().getClueFragments().contains(thisLabel.toLowerCase()))
					this.getEntityRecogniser().getRecognisedSubjects().add(thisSubject);
			}
		}
		
	}
	
	/*
	 * THIS CODE IS DUPLICATED IN THE SIMPLESOLUTION CLASS - REFACTOR IT OUT SOMEWHERE?
	 */
	private String stripLanguageTag(String text) {
		int positionOfLanguageTag = text.length() - LANGUAGE_TAG_LENGTH;
		if(text.length() > LANGUAGE_TAG_LENGTH) {
			if(text.substring(positionOfLanguageTag, positionOfLanguageTag + 1).equals(LANGUAGE_TAG))
				return text.substring(0, positionOfLanguageTag);
		}
		return text;
	}
}