package org.emoflon.ibex.tgg.runtime.hipe;

import java.util.List;

import org.emoflon.ibex.common.engine.SimpleMatch;
import org.emoflon.ibex.tgg.compiler.patterns.PatternSuffixes;
import org.emoflon.ibex.tgg.compiler.patterns.PatternType;
import org.emoflon.ibex.tgg.compiler.patterns.PatternUtil;
import org.emoflon.ibex.tgg.runtime.matches.ITGGMatch;
import org.emoflon.ibex.tgg.runtime.matches.SimpleTGGMatch;
import org.emoflon.ibex.tgg.runtime.matches.TGGMatchParameterOrderProvider;

import hipe.engine.match.ProductionMatch;

/**
 * A TGG match from HiPE.
 */
public class HiPETGGMatch extends SimpleMatch implements ITGGMatch {
	/**
	 * Creates a new HiPEGTMatch with the given match and pattern.
	 * 
	 * @param match   the HiPE match
	 * @param pattern the HiPE pattern
	 */
	public HiPETGGMatch(final ProductionMatch match, String patternName) {
		super(patternName);
		if (TGGMatchParameterOrderProvider.isInitialized())
			initWithOrderedParams(match, patternName);
		else
			init(match);
	}

	private void init(final ProductionMatch match) {
		for (String label : match.getLabels()) {
			put(label, match.getNode(label));
		}
	}

	/**
	 * Inserts parameters in a predefined order for determined match hashing.
	 * 
	 * @param match
	 * @param params
	 */
	private void initWithOrderedParams(final ProductionMatch match, String patternName) {
		List<String> params = null;
		if (patternName != null)
			params = TGGMatchParameterOrderProvider.getParams(PatternSuffixes.removeSuffix(patternName));
		if (params != null) {
			for (String p : params) {
				if (match.getLabels().contains(p))
					put(p, match.getNode(p));
			}
		} else {
			init(match);
		}
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
