package org.emoflon.ibex.gt.hipe.runtime;

import org.emoflon.ibex.common.operational.SimpleMatch;

import hipe.engine.match.ProductionMatch;
import hipe.pattern.HiPEAbstractPattern;


/**
 * A graph transformation match from Democles.
 */
public class HiPEGTMatch extends SimpleMatch {
	/**
	 * Creates a new DemoclesGTMatch with the given frame and pattern.
	 * 
	 * @param frame
	 *            the Democles frame
	 * @param pattern
	 *            the Democles pattern
	 */
	public HiPEGTMatch(final ProductionMatch match, final HiPEAbstractPattern pattern) {
		super(pattern.getName());
		for(String label : match.getLabels()) {
			put(label, match.getNode(label));
		}
	}
}
