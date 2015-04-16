/*
 *  Copyright 2015 United States Department of Veterans Affairs,
 *		Health Services Research & Development Service
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 */
package gov.va.research.red;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author doug
 */
public class CVResult {

	private CVScore score;
	private Collection<String> regExes;
	
	public CVResult (CVScore score, Collection<String> regExes) {
		this.score = score;
		this.regExes = regExes;
	}

	public static CVResult aggregate(List<CVResult> foldResults) {
		CVScore aggregateScore = new CVScore();
		Set<String> regExes = new HashSet<>();
		for (CVResult fr : foldResults) {
			if (fr != null) {
				if (fr.getScore() != null) {			
					aggregateScore.setTp(aggregateScore.getTp() + fr.getScore().getTp());
					aggregateScore.setTn(aggregateScore.getTn() + fr.getScore().getTn());
					aggregateScore.setFp(aggregateScore.getFp() + fr.getScore().getFp());
					aggregateScore.setFn(aggregateScore.getFn() + fr.getScore().getFn());
				}
				if (fr.getRegExes() != null) {
					regExes.addAll(fr.getRegExes());
				}
			}
		}
		return new CVResult(aggregateScore, regExes);
	}
	
	public CVScore getScore() {
		return score;
	}
	public void setScore(CVScore score) {
		this.score = score;
	}
	public Collection<String> getRegExes() {
		return regExes;
	}
	public void setRegExes(List<String> regExes) {
		this.regExes = regExes;
	}

}
