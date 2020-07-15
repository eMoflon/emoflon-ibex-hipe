package org.emoflon.ibex.gt.hipe.runtime;

import java.util.List;

import org.emoflon.ibex.common.operational.SimpleMatch;
import org.emoflon.ibex.tgg.compiler.patterns.PatternSuffixes;
import org.emoflon.ibex.tgg.operational.matches.TGGMatchParameterOrderProvider;

import hipe.engine.match.ProductionMatch;

/**
 * A graph transformation match from HiPE.
 */
public class HiPEGTMatch extends SimpleMatch {
	/**
	 * Creates a new HiPEGTMatch with the given match and pattern.
	 * 
	 * @param match   the HiPE match
	 * @param pattern the HiPE pattern
	 */
	public HiPEGTMatch(final ProductionMatch match, String patternName) {
		super(patternName);
		List<String> params = null;
		if (patternName != null)
			params = TGGMatchParameterOrderProvider.getParams(PatternSuffixes.removeSuffix(patternName));
		if (params != null) {
			// Insert parameters in a predefined order for determined match hashing
			for (String p : params) {
				if (match.getLabels().contains(p))
					put(p, match.getNode(p));
			}
		} else {
			for (String label : match.getLabels()) {
				put(label, match.getNode(label));
			}
		}
	}
}
