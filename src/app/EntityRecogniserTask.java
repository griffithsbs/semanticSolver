package app;

import java.util.ArrayList;
import java.util.Map;

import javax.swing.SwingWorker;

import org.apache.log4j.Logger;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import framework.Clue;
import framework.Pop;

/**
 * @author Ben Griffiths
 * EntityRecogniserTask
 * The EntityRecogniserTask queries DBpedia's SPARQL endpoint in order to compile a list of URIS of resources whose labels match fragments
 * of the clue text of the clue with which it is initialised.
 * @extends javax.swing.SwingWorker
 */
public class EntityRecogniserTask extends SwingWorker<ArrayList<String>, Void> {
	private static Logger log = Logger.getLogger(EntityRecogniserTask.class);
	private final String LANG = "@en";
	private final int RESULT_LIMIT = 200;
	private final int FITB_RESULT_LIMIT = 100;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private Clue clue;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private StmtIterator statementsIterator;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private ResIterator propertiesIterator;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private ArrayList<String> recognisedResourceUris;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private Map<String, Boolean> commonClueFragments;
	
	/**
	 * extractEntities - constructs a list of URIs of resources in the DBpedia knowledge base whose labels match exactly
	 * the given clue fragment
	 * @param clueFragment - the fragment of clue text with which to try to find resources on DBpedia with matching labels
	 * @throws com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP - if any of the generated SPARQL queries throw this exception
	 */
	private void extractEntities(String clueFragment) throws QueryExceptionHTTP {
    	if(this.getCommonClueFragments().containsKey(clueFragment.toLowerCase()))
    		return;	// do not construct models around the most commonly occurring English words, as defined in the commonClueFragments list
	     String wrappedClueFragment = "\"" + clueFragment + "\"" + LANG; // wrap with escaped quotes and append a language tag
	
	     String SPARQLquery = Pop.RDFS_PREFIX_DECLARATION + " " +
	    		 			Pop.DBPPROP_PREFIX_DECLARATION + " " +
	    		 			Pop.DB_OWL_PREFIX_DECLARATION + " " +
	    		 			Pop.FOAF_PREFIX_DECLARATION +
	                                " select distinct ?resource {" +
	                               " {" +
	                                        "{ select distinct ?resource" +
	                                        " where {?resource rdfs:label " + wrappedClueFragment + ".}" +
	                                        " }" +
	                                        " UNION" +
	                                        " { select distinct ?resource" +
	                                        "  where {?resource dbpprop:name " + wrappedClueFragment + ".}" +
	                                        " }" +
	                                        " UNION" +
	                                        " { select distinct ?resource" +
	                                        "  where {?resource foaf:givenName " + wrappedClueFragment + ".}" +
	                                        " }" +
	                                        " UNION" +
	                                        " { select distinct ?resource" +
	                                        "  where {?resource foaf:surname " + wrappedClueFragment + ".}" +
	                                        " }" +
	                                        " UNION" +
	                                        " { select distinct ?resource" +
	                                        "  where {?redirectingResource rdfs:label " + wrappedClueFragment + "." +
	                                        "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource.}" +
	                                        " }" +
	                                        " UNION" +
	                                        " { select distinct ?resource" +
	                                        "  where {?redirectingResource dbpprop:name " + wrappedClueFragment + "." +
	                                        "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource.}" +
	                                        " }" +
	                                        " UNION" +
	                                        " { select distinct ?resource" +
	                                        "  where {?redirectingResource foaf:givenName " + wrappedClueFragment + "." +
	                                        "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource.}" + 
	                                        " }" +
	                                        " UNION" +
	                                        " { select distinct ?resource" +
	                                        "  where {?redirectingResource foaf:surname " + wrappedClueFragment + "." +
	                                        "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource.}" +
	                                        " }" +
	                               " }" + 
	                     " }" +
	                     " LIMIT " + this.RESULT_LIMIT;
	
	     Query query = QueryFactory.create(SPARQLquery);
	     QueryExecution queryExecution = QueryExecutionFactory.sparqlService(Pop.ENDPOINT_URI, query);
	     try {
	              ResultSet resultSet = queryExecution.execSelect();
	              while(resultSet.hasNext()) {
	                       QuerySolution querySolution = resultSet.nextSolution();
	                       Resource thisResource = querySolution.getResource("?resource");
	                       String nameSpace = thisResource.getNameSpace();
	                       /* We only want to consider resources in the BDpedia namespace */
	                       if(!nameSpace.contains(Pop.DBPEDIA_RESOURCE_NS)) {
	                    	   continue;
	                       }
	                       String resourceUri = thisResource.getURI();
	                       this.getRecognisedResourceUris().add(resourceUri);
	                       log.debug("Recognised resource: " + resourceUri);
	              }
	              queryExecution.close();   
	     }
	     catch(QueryExceptionHTTP e) {
	              throw e;
	     }
	}
	
	/**
	 * extractFITBEntities - constructs a list of URIs of resources in the DBpedia knowledge base whose labels match partially
	 * the given clue fragment
	 * @param clueFragment - the fragment of clue text with which to try to find resources on DBpedia with partially matching labels
	 * @throws com.hp.hpl.jena.sparql.engine.http.QueryExceptionHTTP - if any of the generated SPARQL queries throw this exception
	 */
	private void extractFITBEntities(String clueFragment) throws QueryExceptionHTTP {
		if(this.getCommonClueFragments().containsKey(clueFragment.toLowerCase()))
    		return; // do not construct models around the most commonly occurring English words, as defined in the commonClueFragments list
	    String wrappedClueFragment = "'\"" + clueFragment + "\"'";
	    
	    log.debug("Attempting to extract resources whose labels contain " + wrappedClueFragment);
	    
	    String SPARQLquery = Pop.XPATH_FUNCTIONS_PREFIX_DECLARATION + " " +
	    					Pop.RDFS_PREFIX_DECLARATION + " " +
	    					Pop.DBPPROP_PREFIX_DECLARATION + " " +
	    					Pop.DB_OWL_PREFIX_DECLARATION + " " +
	    					Pop.FOAF_PREFIX_DECLARATION +
	                   " select distinct ?resource {" +
	                  " {" +
	                           "{ select distinct ?resource" +
	                           " where {?resource rdfs:label ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                                    "}" +
	                           " }" +
	                           " UNION" +
	                           " { select distinct ?resource" +
	                           "  where {?resource dbpprop:name ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                                    "}" +
	                           " }" +
	                           " UNION" +
	                           " { select distinct ?resource" +
	                           "  where {?resource foaf:givenName ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                                    "}" +
	                           " }" +
	                           " UNION" +
	                           " { select distinct ?resource" +
	                           "  where {?resource foaf:surname ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                                    "}" +
	                           " }" +
	                           " UNION" +
	                           " { select distinct ?resource" +
	                           "  where {?redirectingResource rdfs:label ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                           "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource." +                                                             
	                                    "}" +
	
	                           " }" +
	                           " UNION" +
	                           " { select distinct ?resource" +
	                           "  where {?redirectingResource dbpprop:name ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                           "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource." +
	                                    "}" +
	                           " }" +
	                           " UNION" +
	                           " { select distinct ?resource" +
	                           "  where {?redirectingResource foaf:givenName ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                           "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource." +
	                                    "}" +
	                           " }" +
	                           " UNION" +
	                           " { select distinct ?resource" +
	                           "  where {?redirectingResource foaf:surname ?label." +
	                           			" ?label <bif:contains> " + wrappedClueFragment + "." +
	                           "         ?redirectingResource dbpedia-owl:wikiPageRedirects ?resource." +
	                                    "}" +
	                           " }" +
	                  " }" + 
	        " }" +
	        " LIMIT " + this.FITB_RESULT_LIMIT;
	
	    Query query = QueryFactory.create(SPARQLquery);
	    QueryExecution queryExecution = QueryExecutionFactory.sparqlService(Pop.ENDPOINT_URI, query);
	    try {
	             ResultSet resultSet = queryExecution.execSelect();
	             while(resultSet.hasNext()) {
	                      QuerySolution querySolution = resultSet.nextSolution();
	                      Resource thisResource = querySolution.getResource("?resource");
	                      String nameSpace = thisResource.getNameSpace();
	                      /* We only want to consider resources in the BDpedia namespace */
	                      if(!nameSpace.contains(Pop.DBPEDIA_RESOURCE_NS)) {
	                   	   continue;
	                      }
	                      String resourceUri = thisResource.getURI();
	                      if(!this.getRecognisedResourceUris().contains(resourceUri)) {
	                    	   this.getRecognisedResourceUris().add(resourceUri);
	                           log.debug("Recognised resource: " + resourceUri);
	                      }
	             }
	             queryExecution.close();   
	    }
	    catch(QueryExceptionHTTP e) {
	             throw e;
	    }
	}

	/**
	 * Constructor - instantiates a new EntityRecogniserTask object with the purpose of recognising named entities in the text of
	 * the given clue
	 * @param clue - the clue in the text of which the EntityRecogniserTask will attempt to find named entities
	 */
	public EntityRecogniserTask(Clue clue) {
		super();
		this.setClue(clue);
		this.setRecognisedResourceUris(new ArrayList<String>());
		this.setCommonClueFragments(ModelLoader.getCommonClueFragments());
	}
    
	/**
	 * doInBackground - for each fragment in the clueFragments member of the clue with which the EntityRecogniserTask was initialised,
	 * an attempt is made to retrieve a list of resources from DBpedia with matching labels
	 * @override javax.swing.SwingWorker.doInBackground
	 * @return a list of URIs of resources representing named entities in the text of the clue
	 */
    @Override
    public ArrayList<String> doInBackground() {
        int progress = 0;
        this.setProgress(progress); // Initialise progress property of SwingWorker
        
        
        int combinedLengthOfQueries = this.getClue().getClueFragments().size();
        int taskLength = (100 / combinedLengthOfQueries);
        
        for(String clueFragment : this.getClue().getClueFragments()) {
        	try {
        		if(this.getClue().isFillInTheBlank())
        			this.extractFITBEntities(clueFragment);
        		else this.extractEntities(clueFragment);
        	}
        	catch (QueryExceptionHTTP e) {
        		log.debug("DBpedia connection dropped. Entity recognition for clue fragment " + clueFragment + " failed");
        		log.debug(e.getResponseMessage());
        	}
        	progress += taskLength;
            this.setProgress(progress); // one query has been completed
        }
        return this.getRecognisedResourceUris();
    }
    
    /**
     * @override javax.swing.SwingWorker.done
     */
    @Override
    public void done() {
    	this.setProgress(100);
    }
}