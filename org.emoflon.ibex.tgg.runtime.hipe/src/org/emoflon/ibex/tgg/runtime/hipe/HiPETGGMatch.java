package org.emoflon.ibex.tgg.runtime.hipe;

import org.emoflon.ibex.gt.hipe.runtime.HiPEGTMatch;
import org.emoflon.ibex.tgg.compiler.patterns.PatternUtil;
import org.emoflon.ibex.tgg.runtime.matches.ITGGMatch;
import org.emoflon.ibex.tgg.runtime.matches.SimpleTGGMatch;
import org.emoflon.ibex.tgg.compiler.patterns.PatternType;

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
		SimpleTGGMatch copy = new SimpleTGGMatch(getPatternName());
		getParameterNames().forEach(n -> copy.put(n, get(n)));
		return copy;
	}

	@Override
	public PatternType getType() {
		return PatternUtil.resolve(getPatternName());
	}
}
