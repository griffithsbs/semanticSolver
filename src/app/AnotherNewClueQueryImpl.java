/**
 * 
 */
package app;

import java.util.ArrayList;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.InfModel;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.reasoner.Reasoner;
import com.hp.hpl.jena.reasoner.ReasonerRegistry;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;
import com.hp.hpl.jena.vocabulary.RDFS;

import framework.Clue;
import framework.ClueQuery;
import framework.EntityRecogniser;
import framework.Pop;

/**
 * @author Ben Griffiths
 *
 */
public class AnotherNewClueQueryImpl implements ClueQuery {
	private final String ENDPOINT_URI = "http://dbpedia.org/sparql";
	private final String DBPEDIA_PREFIX_DECLARATION = "PREFIX dbpedia: <http://dbpedia.org/resource/>";
	private final String DBPEDIA_OWL_PREFIX_DECLARATION = "PREFIX dbpedia-owl: <http://dbpedia.org/ontology/>";
	private final String RDFS_PREFIX_DECLARATION = "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>";
	private final int LANGUAGE_TAG_LENGTH = 3;
	private final String LANGUAGE_TAG = "@";
	
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private ArrayList<Resource> extractedResources; // Resources whose labels have been extracted from DBpedia
	@Setter(AccessLevel.PRIVATE) private ArrayList<String> candidateSolutions;
	@Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PRIVATE) private Clue clue;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private EntityRecogniser entityRecogniser;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private Query query;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private QueryExecution queryExecution;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PUBLIC) private String whereClause;
	
	public AnotherNewClueQueryImpl(Clue clue, EntityRecogniser entityRecogniser) {
		this.setClue(clue);
		this.setEntityRecogniser(entityRecogniser);
		this.setCandidateSolutions(new ArrayList<String>());
		this.setExtractedResources(new ArrayList<Resource>());
	}
	
	/**
	 * @return ArrayList<String>
	 */
	@Override
	public ArrayList<String> getCandidateSolutions() {
		ArrayList<String> recognisedResourceURIs = this.getEntityRecogniser().getRecognisedResourceURIs();
		// ArrayList<String> recognisedPropertyURIs = this.getEntityRecogniser().getRecognisedPropertyURIs();
		Model schema = FileManager.get().loadModel("popv7.owl");
		Reasoner reasoner = ReasonerRegistry.getOWLMiniReasoner();
	    reasoner = reasoner.bindSchema(schema);
		for(String resourceUri : recognisedResourceURIs) {
			
		    Model data = this.constructModelFromRemoteStore(resourceUri, true); // Query DBpedia using resource as subject
		    InfModel infModel = ModelFactory.createInfModel(reasoner, data);
		    this.extractCandidates(infModel);
		    
		    
		    data = this.constructModelFromRemoteStore(resourceUri, false); // .. and using resource as object
		    infModel = ModelFactory.createInfModel(reasoner, data);
		    this.extractCandidates(infModel);
			
			/*
			for(String propertyUri : recognisedPropertyURIs) {
				// Look for subjects where <?subject propertyUri ResourceUri>
				String sparqlQuery = DBPEDIA_PREFIX_DECLARATION +
						" " + DBPEDIA_OWL_PREFIX_DECLARATION +
						" " + RDFS_PREFIX_DECLARATION +
						" select distinct ?label" +
							" where {?subject <" + propertyUri + "> <" + resourceUri +">." +
							"        ?subject rdfs:label ?label.}";
				this.executeSparqlQuery(sparqlQuery);
				
				// Look for objects where <?resourceUri propertyUri object>
				sparqlQuery = RDFS_PREFIX_DECLARATION +
							" select distinct ?label" +
							" where { <" + resourceUri + "> <" + propertyUri + "> ?object." +
							"        ?object rdfs:label ?label.}";
				this.executeSparqlQuery(sparqlQuery);
			} */
		}
		return this.candidateSolutions;
	}
	
	private Model constructModelFromRemoteStore(String resourceUri, boolean resourceAsSubject) {
		String sparqlQuery;
		if(resourceAsSubject) {
			sparqlQuery = RDFS_PREFIX_DECLARATION +
						" construct {<" + resourceUri + "> ?predicate ?object." +
						" 			?object rdfs:label ?label.}" +
						" where {<" + resourceUri + "> ?predicate ?object." +
						" 			?object rdfs:label ?label.}";
		}
		else {
			sparqlQuery = RDFS_PREFIX_DECLARATION +
						" construct { ?subject ?predicate <" + resourceUri + ">." +
						" 			?subject rdfs:label ?label.}" +
						" where {?subject ?predicate <" + resourceUri + ">." +
						" 			?subject rdfs:label ?label.}";
		}
		Query query = QueryFactory.create(sparqlQuery);
		QueryExecution queryExecution = QueryExecutionFactory.sparqlService(ENDPOINT_URI, query);
		String subOrOb = resourceAsSubject ? "subject" : "object";						 // DEBUGGING ******************************
		System.out.println("Constructing model with " + resourceUri + " as " + subOrOb); // DEBUGGING ******************************
		Model model = queryExecution.execConstruct();
		queryExecution.close();
		return model;
	}
	
	private void extractCandidates(InfModel infModel) {
		ArrayList<String> clueFragments = this.getEntityRecogniser().getClueFragments();
		System.out.println("Extracting labels..."); // DEBUGGING ****************************************************
		
		Selector selector = new SimpleSelector(null, Pop.relationalProperty, (RDFNode) null);
		
		// List statements in which the predicate is a pop:relationalProperty
		StmtIterator statements = infModel.listStatements(selector);
		
		while(statements.hasNext()) {
			Statement thisStatement = statements.nextStatement();
			Property thisPredicate = thisStatement.getPredicate();
			Resource thisPredicateInModel = infModel.getResource(thisPredicate.getURI());
			
			StmtIterator labelProperties = thisPredicateInModel.listProperties(RDFS.label);
			
			if(labelProperties != null) {
				System.err.println("Found some properties..."); // DEBUGGING ****************************
				while(labelProperties.hasNext()) {
					RDFNode predicateLabelValue = labelProperties.nextStatement().getObject();
					String rawPredicateLabel = predicateLabelValue.toString();
					System.err.println("Found pop:relationalProperty with label " + rawPredicateLabel); // DEBUGGING **************
					String predicateLabel = stripLanguageTag(rawPredicateLabel);
					if(clueFragments.contains(toProperCase(predicateLabel))) {
						RDFNode objectOfStatement = thisStatement.getObject();
						if(objectOfStatement.isLiteral()) { // a string has been identified which may be a solution
								this.addCandidateSolution(objectOfStatement.toString());
						}
							
						else {  // a resource has been identified whose label may represent a solution
								Resource object = objectOfStatement.asResource();
								if(!extractedResources.contains(object)) { // check if we have already tested this resource
									extractedResources.add(object);
									StmtIterator candidateLabels = object.listProperties(RDFS.label);
									while(candidateLabels.hasNext()) {
										RDFNode candidateLabelValue = candidateLabels.nextStatement().getObject();
										String rawCandidateLabel = candidateLabelValue.toString();
										String candidateLabel = stripLanguageTag(rawCandidateLabel);
										this.addCandidateSolution(candidateLabel);
									}
									
						
								}
						}
					}
				}
			}
			
		}
	}

	private void addCandidateSolution(String candidateSolution) {
		if(!(this.candidateSolutions.contains(candidateSolution)))
			this.candidateSolutions.add(candidateSolution);
		System.out.println("Found candidate solution: " + candidateSolution); // DEBUGGING ******************************
	}

	/*
	 * THIS CODE IS DUPLICATED IN THE SIMPLEENTITYRECOGNISER CLASS - REFACTOR IT OUT SOMEWHERE?
	 */
	private String stripLanguageTag(String solutionText) {
		int positionOfLanguageTag = solutionText.length() - LANGUAGE_TAG_LENGTH;
		if(solutionText.length() > LANGUAGE_TAG_LENGTH) {
			if(solutionText.substring(positionOfLanguageTag, positionOfLanguageTag + 1).equals(LANGUAGE_TAG))
				return solutionText.substring(0, positionOfLanguageTag);
		}
		return solutionText;
	}
	
	/*
	 * DUPLICATED FROM ENTITYRECOGNISERIMPL CLASS
	 */
	private String toProperCase(String thisWord) {
		String thisWordInProperCase = thisWord.substring(0, 1).toUpperCase();
		if(thisWord.length() > 1) {
			int index = 1; // start at the second letter of the word
			while(index < thisWord.length()) {
				String nextCharacter = thisWord.substring(index, index + 1);
				thisWordInProperCase += nextCharacter;
				if((nextCharacter.equals(" ")) && (index < (thisWord.length() - 1))) {
					 index++; // the next character needs to be capitalised
					 nextCharacter = thisWord.substring(index, index + 1);
					 thisWordInProperCase += nextCharacter.toUpperCase();
				}
				index++;
			}
		}
		return thisWordInProperCase;
	}
}
