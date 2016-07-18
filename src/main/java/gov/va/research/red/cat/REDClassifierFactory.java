package gov.va.research.red.cat;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;

import gov.va.research.red.cat.IREDClassifier;

import org.python.util.PythonInterpreter;
import org.python.core.PyObject;
import org.python.core.PyString;

public final class REDClassifierFactory {
	private static final String RED_CLASSIFIER_PY = "REDClassifier5_5.py";
	private static PyObject pythonClass = null;
	
	private REDClassifierFactory(){
	}
	
	public static IREDClassifier createModel() throws FileNotFoundException, URISyntaxException {
		if (pythonClass==null) {
			// Find the python source file
			File py = new File(RED_CLASSIFIER_PY);
			if (!py.exists()) {
				URL pyUrl = REDClassifierFactory.class.getClassLoader().getResource(RED_CLASSIFIER_PY);
				if (pyUrl == null) {
					throw new FileNotFoundException("Python source file \"" + RED_CLASSIFIER_PY + "\" not found in current directory or classpath.");
				}
				py = new File(pyUrl.toURI());
			}
			System.out.println("Using " + py.getAbsolutePath());
			System.out.print("Starting Jython Interpreter...");
			System.setProperty("python.console.encoding", "UTF-8");
			PythonInterpreter pint = new PythonInterpreter();
			System.out.println("done.");
			pint.exec("import os");
			pint.exec("import sys");
//			pint.exec("oscwd = os.getcwd()");
//			PyString cwd = (PyString)pint.get("oscwd");
			pint.exec("sys.path.append('" + py.getParentFile().getAbsolutePath() /*.os.getcwd()+'/src/main/python'*/ + "')");
			pint.exec("from REDClassifier5_5 import REDClassifier");
			pythonClass = pint.get("REDClassifier");
			pint.close();
		}
		PyObject model = pythonClass.__call__();
		return (IREDClassifier)model.__tojava__(IREDClassifier.class);
	}
}
