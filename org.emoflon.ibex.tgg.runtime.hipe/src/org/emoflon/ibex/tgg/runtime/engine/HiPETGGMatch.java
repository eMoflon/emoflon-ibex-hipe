package org.emoflon.ibex.tgg.runtime.engine;

import org.emoflon.ibex.gt.hipe.runtime.HiPEGTMatch;
import org.emoflon.ibex.tgg.operational.matches.IMatch;
import org.emoflon.ibex.tgg.operational.matches.SimpleMatch;

import hipe.engine.match.ProductionMatch;
import hipe.pattern.HiPEAbstractPattern;

/**
 * A TGG match from HiPE.
 */
public class HiPETGGMatch extends HiPEGTMatch implements IMatch {
	/**
	 * Creates a new HiPETGGMatch with the given match and pattern.
	 * 
	 * @param match
	 *            the HiPE match
	 * @param pattern
	 *            the HiPE pattern
	 */
	public HiPETGGMatch(final ProductionMatch match, final HiPEAbstractPattern pattern) {
		super(match, pattern);
	}

	@Override
	public IMatch copy() {
		SimpleMatch copy = new SimpleMatch(getPatternName());
		getParameterNames().forEach(n -> copy.put(n, get(n)));
		return copy;
	}
}
