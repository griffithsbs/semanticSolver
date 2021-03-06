package app;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

import org.apache.log4j.Logger;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Selector;
import com.hp.hpl.jena.rdf.model.SimpleSelector;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.shared.JenaException;
import com.hp.hpl.jena.vocabulary.RDF;

import framework.Clue;
import framework.CrosswordKB;
import framework.Solution;

/**
 * @author Ben Griffiths
 * KnowledgeBaseManager
 * Responsible for managing the persistent knowledge base of previously solved clues and their solutions.
 * Implemented as a Singleton class.
 */
public class KnowledgeBaseManager {
	private static KnowledgeBaseManager instance;
	private static Logger log = Logger.getLogger(SemanticSolverImpl.class);
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private Model knowledgeBase;
	@Getter(AccessLevel.PRIVATE) @Setter(AccessLevel.PRIVATE) private ArrayList<SolvedClue> solvedClues;
	@Getter(AccessLevel.PUBLIC) @Setter(AccessLevel.PRIVATE) private volatile boolean finished;
	
	/**
	 * Constructor - the only constructor is private. Loads the persistent knowledge base into memory and then reads into the 
	 * solvedClues list all solved clues contained within the knowledge base
	 */
	private KnowledgeBaseManager() {
		this.setFinished(false);
		try {
			this.setKnowledgeBase(ModelLoader.getKnowledgeBase());
		}
		catch(JenaException e) {
			log.debug(e.getMessage());
			this.setKnowledgeBase(null);
		}
		this.setSolvedClues(new ArrayList<SolvedClue>());
		if(this.getKnowledgeBase() != null)
			this.gatherPreviouslySolvedClues();
		this.setFinished(true);
	}
	
	/**
	 * gatherPreviouslySolvedClues - initialises the solvedClues member with a list of all solved clues present in the knowledge base
	 */
	private void gatherPreviouslySolvedClues() {

		Selector selector = new SimpleSelector(null, CrosswordKB.solvedBy, (RDFNode) null);
		StmtIterator statements = this.getKnowledgeBase().listStatements(selector);
		
		while(statements.hasNext()) {
			Statement thisStatement = statements.nextStatement();
			Resource thisClue = thisStatement.getSubject();
			Resource thisSolution = thisStatement.getObject().asResource();

			Statement clueTextStatement = thisClue.getProperty(CrosswordKB.hasClueText);
			String clueText = clueTextStatement.getObject().toString();
			
			Statement solutionStructureStatement = thisClue.getProperty(CrosswordKB.hasSolutionStructure);
			String solutionStructure = solutionStructureStatement.getObject().toString();
			
			String clueUri = thisClue.getURI();
			
			SolvedClue solvedClue = new SolvedClue(clueText, solutionStructure, clueUri);
			
			StmtIterator solutionTextStatements = thisSolution.listProperties(CrosswordKB.hasSolutionText);
			while(solutionTextStatements.hasNext()) {
				Statement solutionTextStatement = solutionTextStatements.nextStatement();
				String solutionText = solutionTextStatement.getObject().toString();
				solvedClue.getSolutionTexts().add(solutionText);
			}
			solvedClues.add(solvedClue);
		}
	}
	
	/**
	 * addSolutionOnlyToKnowledgeBase - adds a new solution to an existing solved clue in the in-memory representation of the knowledge
	 * base
	 * @param clueUri - the URI of the solved clue, as used in the crossword knowledge base
	 * @param solution - a Solution object representing the new solution found for this clue
	 */
	private void addSolutionOnlyToKnowledgeBase(String clueUri, Solution solution) {
		Resource clueResource = this.getKnowledgeBase().getResource(clueUri);
		
		UUID solutionUID = UUID.randomUUID();
		String solutionUri = CrosswordKB.CROSSWORD_KB_URI + solutionUID.toString();
		String solutionText = solution.getSolutionText();
		
		Resource solutionResource = this.getKnowledgeBase().createResource(solutionUri);
		
		this.getKnowledgeBase().add(solutionResource, RDF.type, CrosswordKB.solution);
		this.getKnowledgeBase().add(solutionResource, CrosswordKB.hasSolutionText, solutionText);
		
		this.getKnowledgeBase().add(clueResource, CrosswordKB.solvedBy, solutionResource);
	}
	
	/**
	 * addToKnowledgeBase - adds a new clue-solution pair to the in-memory representation of the knowledge base
	 * @param clue - a Clue object representing the newly solved clue
	 * @param solution - a Solution object representing a found Solution to the clue
	 */
	private void addToKnowledgeBase(Clue clue, Solution solution) {
		String clueText = clue.getSourceClue();
		String solutionStructure = clue.getSolutionStructureAsString();
		String solutionText = solution.getSolutionText();
		
		UUID clueUID = UUID.randomUUID();
		UUID solutionUID = UUID.randomUUID();
		
		String clueUri = CrosswordKB.CROSSWORD_KB_URI + clueUID.toString();
		String solutionUri = CrosswordKB.CROSSWORD_KB_URI + solutionUID.toString();

		Resource clueResource = this.getKnowledgeBase().createResource(clueUri);
		Resource solutionResource = this.getKnowledgeBase().createResource(solutionUri);
		
		this.getKnowledgeBase().add(clueResource, RDF.type, CrosswordKB.clue);
		this.getKnowledgeBase().add(clueResource, CrosswordKB.hasClueText, clueText);
		this.getKnowledgeBase().add(clueResource, CrosswordKB.hasSolutionStructure, solutionStructure);
		
		this.getKnowledgeBase().add(solutionResource, RDF.type, CrosswordKB.solution);
		this.getKnowledgeBase().add(solutionResource, CrosswordKB.hasSolutionText, solutionText);
		
		this.getKnowledgeBase().add(clueResource, CrosswordKB.solvedBy, solutionResource);
		
		SolvedClue solvedClue = new SolvedClue(clueText, solutionStructure, clueUri, solutionText);
		this.getSolvedClues().add(solvedClue);
	}
	
	/**
	 * getInstance - returns the unique instance of the KnowledgeBaseManager class
	 * @return
	 */
	public static KnowledgeBaseManager getInstance() {
		if(instance == null)
			instance = new KnowledgeBaseManager();
		return instance;
	}
	
	/**
	 * addToKnowledgeBase - adds a new clue to the in-memory representation of the knowledge base, and a new clue-solution pair for
	 * each Solution object in the solutions argument
	 * @param clue - a Clue object representing the newly solved clue
	 * @param solutions - an ArrayList of Solution objects representing found solutions to the new clue
	 */
	public void addToKnowledgeBase(Clue clue, ArrayList<Solution> solutions) {
		this.setFinished(false);
		if(this.getKnowledgeBase() == null) {
			this.setFinished(true);
			return;
		}
		for(Solution solution : solutions) {
			if(solution.getConfidence() > 0) {
				SolvedClue solvedClue = new SolvedClue(clue.getSourceClue(), clue.getSolutionStructureAsString(), null, 
						solution.getSolutionText()); // create a dummy solvedClue object with a null uri
				if(this.getSolvedClues().contains(solvedClue)) {
					int index = this.getSolvedClues().indexOf(solvedClue);
					SolvedClue previouslySolvedClue = this.getSolvedClues().get(index);
					String clueResourceUri = previouslySolvedClue.getClueResourceUri();
					if(!previouslySolvedClue.getSolutionTexts().contains(solution.getSolutionText()))
						this.addSolutionOnlyToKnowledgeBase(clueResourceUri, solution);
				}
				else {
					this.addToKnowledgeBase(clue, solution); // add the new triples to the knowledge base
				}
			}
		}
		solutions = null; // allow solutions to be garbage-collected
		this.setFinished(true);
	}
	
	/**
	 * persistKnowledgeBase - writes the in-memory representation of the knowledge base out to disk in RDF/XML form
	 */
	public void persistKnowledgeBase() {
		this.setFinished(false);
		if(this.getKnowledgeBase() == null) {
			this.setFinished(true);
			return;
		}
		try {
			String fileName = "data\\" + CrosswordKB.LOCAL_KNOWLEDGE_BASE_URI;
			FileOutputStream outFile = new FileOutputStream(fileName);
			log.debug("Writing out crosswordKB to disk");
			this.getKnowledgeBase().write(outFile, "RDF/XML-ABBREV");
			outFile.close();
			log.debug("CrosswordKB written to disk");
		}
		catch(FileNotFoundException e) {
			log.debug("Failed to write crosswordKB out to disk");
			log.debug(e.getMessage());
		} 
		catch (IOException e) {
			log.debug("Failed to write crosswordKB out to disk");
			log.debug(e.getMessage());
		}
		this.setFinished(true);
	}
}
