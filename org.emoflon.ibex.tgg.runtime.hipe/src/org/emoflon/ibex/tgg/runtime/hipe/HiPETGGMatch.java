package org.emoflon.ibex.tgg.runtime.hipe;

import org.emoflon.ibex.common.operational.IMatch;
import org.emoflon.ibex.gt.hipe.runtime.HiPEGTMatch;
import org.emoflon.ibex.tgg.compiler.patterns.Pattern2Type;
import org.emoflon.ibex.tgg.compiler.patterns.PatternType;
import org.emoflon.ibex.tgg.operational.matches.ITGGMatch;
import org.emoflon.ibex.tgg.operational.matches.SimpleMatch;

import hipe.engine.match.ProductionMatch;

/**
 * A TGG match from HiPE.
 */
public class HiPETGGMatch extends HiPEGTMatch implements ITGGMatch {
	/**
	 * Creates a new HiPETGGMatch with the given match and pattern.
	 * 
	 * @param match
	 *            the HiPE match
	 * @param pattern
	 *            the HiPE pattern
	 */
	public HiPETGGMatch(final ProductionMatch match, final String patternName) {
		super(match, patternName);
	}

	@Override
	public ITGGMatch copy() {
		SimpleMatch copy = new SimpleMatch(getPatternName());
		getParameterNames().forEach(n -> copy.put(n, get(n)));
		return copy;
	}

	@Override
	public PatternType getType() {
		return Pattern2Type.resolve(getPatternName());
	}
}
