package gov.va.research.red.cat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.va.research.red.CVScore;
import gov.va.research.red.ex.REDExFactory;
import gov.va.vinci.nlp.framework.utils.U;
import gov.va.vinci.nlp.framework.utils.Use;

/** 
 * RedCat is a main around the Regular Expression Categorization functionality
 * 
 */
public class RedCat {

	public static void main(String[] pArgs) {
		
		try {
		
			String args[] = setArgs( pArgs);
		
			String       inputDir =                       U.getOption(args, "--inputDir=",  "./data");
			String      outputDir =                       U.getOption(args, "--outputDir=", "./regExes"); 
			String   keywordsFile =                       U.getOption(args, "--keywordsFile=", ""); 
			int             folds = Integer.parseInt(     U.getOption(args, "--folds=", "10" ));
			boolean biasForRecall = Boolean.parseBoolean( U.getOption(args, "--biasForRecall=", "false"));
			List<File>     inputFiles = getInputFiles( inputDir);
			
			List<String> yesLabels = new ArrayList<>();
			yesLabels.add("yes");
			List<String> noLabels = new ArrayList<>();
			noLabels.add("no");
			
		
			CrossValidateCategorizer rexcv = new CrossValidateCategorizer();
			List<CVScore>          results = rexcv.crossValidateClassifier(inputFiles, yesLabels, noLabels, folds, biasForRecall);
			int i = 0;
			for (CVScore score : results) {
				System.out.println("--- Run " + (i+1) + " ---");
				System.out.println(score.getEvaluation());
				i++;
			}
			System.out.println("--- Aggregate ---");
			CVScore aggregate = CVScore.aggregate(results);
			System.out.println(aggregate.getEvaluation());//
		
			System.out.println("............... Done cross validating");
			
			
			
			// --------------------------------
			// build a model using all the data
			System.out.println("............... Building the model using all the data ");
			
			RedCatModelCreator redCati = new RedCatModelCreator();
			redCati.createModel(inputFiles, keywordsFile, outputDir, yesLabels,  noLabels,  biasForRecall);
			
			System.out.println("............... Finished, - look in " + outputDir  + "for regex files ");
			
			
			// --------------------
			
			
			
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Issue with REGEx Categorization " + e.toString());
		}
		

	} // end METHOD Main() ----------------------
	
	

	

	  // ------------------------------------------
	  /**
	   * getInputFiles
	   * 
	   * @param pInputDir
	   * @return List<File>
	   */
	  // ------------------------------------------
	  public static List<File> getInputFiles(String pInputDir) {
		  
		  
		  ArrayList<File> buff = new ArrayList<File>();
		  
		  File aDir = new File ( pInputDir);
		  if ( aDir != null && aDir.exists() && aDir.isDirectory() ) {
			  File[] someFiles = aDir.listFiles();
			  for ( File aFile : someFiles ) {
				  if ( aFile.getName().endsWith(".vtt")) {
					  buff.add(  aFile);
				  }
			  }
			  
			  
		  } else {
			  System.err.println("Issue with " + pInputDir + " not a valid inputDir ");
			  buff = null;
		  }
		  if ( buff != null && buff.isEmpty())
			  buff = null;
		  
		  return buff;
		  
	  } // end Method getInputFiles

	  // ------------------------------------------
	  /**
	   * setArgs takes command line args, which override
	   *         default args set here
	   * 
	   * 
	   * @return String
	   */
	  // ------------------------------------------
	  public static String[] setArgs(String pArgs[]) {

	  
	    // -------------------------------------
	    // dateStamp
	    String dateStamp = U.getDateStampSimple();

	    // -------------------------------------
	    // Input and Output

	    String        drive = U.getOption(pArgs,  "--drive=", "d:");
	    String     inputDir = U.getOption(pArgs,  "--inputDir=",  drive + "/utah/branches/RED/target/data");
	    String    outputDir = U.getOption(pArgs,  "--outputDir=", drive + "/utah/branches/RED/target/regExes_" + dateStamp);
	    String keywordsFile = U.getOption(pArgs,  "--keywordsFile=", ""); 
	    
	    String    logDir = outputDir + "/logs"; 
	    String     folds = U.getOption(pArgs,  "--folds=", "4" );
		String biasForRecall = U.getOption(pArgs, "--biasForRecall=", "false");
	    
	   
	    String args[] = {
	       
	        "--inputDir=" + inputDir,
	        "--outputDir=" + outputDir,
	        "--keywordsFile=" + keywordsFile,
	        "--logDir="    + logDir,
	        "--folds="     + folds,
	        "--biasForRecall=" + biasForRecall
	      
	    };
	    
	    String description = 
	    		" \n\n\n RedCat is a binary classifier that learns from roughly swiped snippets \n" +
	            " to produce a set of regular expressions (strict,less strict, least strict) \n" +
                " to be used with the regcat classifier if need be.  This version will run a set \n" +
	            " of cross validating folds to determine the effecacy before creating regular \n" +
                " expression files that can be used within other pattern extraction tools. \n" + 
	            " The regular expression files use all the data to learn from.\n" + 
	            " \n\n The biasForRecall set to true will lump unlabeled snippets in with the positive \n" +
                " examples. It is set to true by default.\n";

	    if ( Use.usageAndExitIfHelp(RedCat.class.getCanonicalName(), pArgs, args) ) {
	    	System.out.print( description);
	    	System.exit(0);
	    }
	     
	     

	    return args;

	  }  // End Method setArgs() -----------------------
	  

	  // ------------------------------------------------
	  // Global Variables
	  // ------------------------------------------------

		private static final Logger LOG = LoggerFactory
				.getLogger(REDExFactory.class);
		private static final boolean DEBUG = Boolean.valueOf(System.getProperty(
				"debug", String.valueOf(false)));

}
