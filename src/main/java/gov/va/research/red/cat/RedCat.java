package gov.va.research.red.cat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.output.TeeOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.va.research.red.CVScore;
import gov.va.research.red.RegEx;
import gov.va.research.red.ex.REDExFactory;
import gov.va.research.red.regex.JSEPatternAdapter;
import gov.va.research.red.regex.PatternAdapter;
import gov.va.research.red.regex.RE2JPatternAdapter;
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
			int             folds = Integer.parseInt(     U.getOption(args,  "--folds=", "10" ));
			boolean biasForRecall = Boolean.parseBoolean( U.getOption(args, "--biasForRecall=", "true"));
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
			System.out.println(aggregate.getEvaluation());//*/
		
			System.out.println("Done cross validating");
			
			
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

	    String     drive = U.getOption(pArgs,  "--drive=", "d:");
	    String  inputDir = U.getOption(pArgs,  "--inputDir=",  drive + "/utah/branches/RED/target/data");
	    String outputDir = U.getOption(pArgs,  "--outputDir=", drive + "utah/branches/RED/target/output_" + dateStamp);
	    
	    String    logDir = outputDir + "/logs"; 
	    String     folds = U.getOption(pArgs,  "--folds=", "10" );
		String biasForRecall = U.getOption(pArgs, "--biasForRecall=", "true");
	    
	   
	    String args[] = {
	       
	        "--inputDir=" + inputDir,
	        "--outputDir=" + outputDir,
	        "--logDir="    + logDir,
	        "--folds="     + folds,
	        "--biasForRecall=" + biasForRecall
	      
	    };

	    if ( Use.usageAndExitIfHelp(RedCat.class.getCanonicalName(), pArgs, args) ) 
	    	System.exit(0);
	     
	     

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
