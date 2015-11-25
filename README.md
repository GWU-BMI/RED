## RED

Regular Expression Discovery: a set of utilities for discovering regular expressions in a corpus and applying those regular expressions to de-novo documents.
RED consists of two main utilities:
	REDEx : RED for Extraction is for information extractions (for example pain scores, body weight values, blood pressure values, etc.)
	REDCl : RED for Classification is for classification of documents.

### To build
* build the uber-jar:

> mvn assembly:single

resulting jar will be named something like RED-<version>-<build number>-jar-with-dependencies.jar

### REDEx Model Training

#### REDEx Configuration and Options

REDEx requires a single argument, the name of the properties file where options are specified. The valid properties are:

| property              | data type  | multiple allowed | default | description
|-----------------------|------------|------------------|---------|------------
| vtt.file              | string     | yes              |         | The file path of a vtt formatted file of snippets.
| label                 | string     | yes              |         | A label name used in annotations in the vtt.file(s) to be considered for extraction. If absent then all labels will be used. |
| folds                 | integer    | no               |         | The number of folds to use for cross-validation.
| allow.overmatches     | true/false | no               | true    | If *true* then if the predicted and actual values overlap but do not match exactly, it is still counted as a true positive. If *false* then predicted and actual values must match exactly to be counted as a true positive.
| case.insensitive      | true/false | no               | true    | If *true* then all text is converted to lowercase (in order, for example, to make case-insensitive comparisons easier).|
| stop.after.first.fold | true/false | no               | false   | If *true* then the cross validation quits after the first fold.
| shuffle               | true/false | no               | true    | If *true* then the snippets will be shuffled before cross validation. This will make the cross-validation non-deterministic, having a different result each time.|
| snippet.limit         | integer    | no               | -1      | Limit the number of snippets this value. A value <= 0 means no limit.
| model.output.file     | string     | no               |         | A file path where the trained model (regular expressions) will be saved for later use.
| holdout               | string     | yes              |         | A string in the training snippets that should not be removed or modified when transforming to regular expressions.
| use.tier2             | true/false | no               | true    | Tier 1 consists of regular expressions with high precision, Tier 2 consists of regular expressions with high recall. Specify *true* to bias for high recall, or *false* to bias for high precision.
| regex.output.file     | string     | no               |         | A file path where all resulting regular expressions will be writted (for debugging purposes).

#### Usage

java -cp RED-<version>-<build number>-jar-with-dependencies.jar gov.va.research.red.ex.REDExFactory [crossvalidate|buildmodel] properties.filename

#### Examples

Assuming the jar file is named RED-2015.9.0-b2015-10-31T23-59-59-jar-with-dependencies.jar

> java -cp RED-2015.9.0-b2015-10-31T23-59-59-jar-with-dependencies.jar gov.va.research.red.ex.REDExFactory crossvalidate pain.properties

This would perform a cross validation using according to the configuration options stored in the file *pain.properties*.

> java -cp RED-2015.9.0-b2015-10-31T23-59-59-jar-with-dependencies.jar gov.va.research.red.ex.REDExFactory buildmodel pain.properties

This would build a REDEx model (two tiered collection of regular expressions) according to the configuration options stored in the file *pain.properties*.

### Running REDEx (model application)

#### Usage

java -jar RED-<version>-<build number>-jar-with-dependencies.jar gov.va.research.red.ex.REDExtractor <REDEx model file> <file dir> [file glob | file ] ...

#### Examples 

(Assuming the jar file is named RED-2015.9.0-b2015-10-31T23-59-59-jar-with-dependencies.jar)

> java -jar RED-2015.9.0-b2015-10-31T23-59-59-jar-with-dependencies.jar gov.va.research.red.ex.REDExtractor painscore.model docs *.txt

This would use the model stored in the file *painscore.model* to extract values from all files in the *docs* directory whose names end in *.txt*.
