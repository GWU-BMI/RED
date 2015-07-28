package gov.va.research.red.cat;

import gov.va.research.red.cat.IREDClassifier;

import org.python.util.PythonInterpreter;
import org.python.core.PyObject;
import org.python.core.PyString;

public final class REDClassifierFactory {
	private static PyObject pythonClass = null;
	
	private REDClassifierFactory(){
	}
	
	public static IREDClassifier createModel() {
		if (pythonClass==null) {
			System.out.print("Starting Jython Interpreter...");
			PythonInterpreter pint = new PythonInterpreter();
			System.out.println("done.");
			pint.exec("import os");
			pint.exec("import sys");
			pint.exec("oscwd = os.getcwd()");
			PyString cwd = (PyString)pint.get("oscwd");
			System.out.println("Please make sure the Python file \"REDClassifier5.py\" is inside the folder:");
			System.out.println(cwd+"/src/main/python");
			pint.exec("sys.path.append(os.getcwd()+'/src/main/python')");
			pint.exec("from REDClassifier5 import REDClassifier");
			pythonClass = pint.get("REDClassifier");
			pint.close();
		}
		PyObject model = pythonClass.__call__();
		return (IREDClassifier)model.__tojava__(IREDClassifier.class);
	}
}
