package org.emoflon.ibex.tgg.runtime.hipe;

import java.util.Map;

import org.emoflon.ibex.gt.build.hipe.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXContextPattern;
import org.emoflon.ibex.util.config.IbexOptions;

import hipe.pattern.HiPEPattern;
import language.TGGNamedElement;

public class TGGIBeXToHiPEPatternTransformation extends IBeXToHiPEPatternTransformation {
	private IbexOptions options;
	private Map<IBeXContextPattern, TGGNamedElement> patternToRuleMap;

	public TGGIBeXToHiPEPatternTransformation(IbexOptions options,
			Map<IBeXContextPattern, TGGNamedElement> patternToRuleMap) {
		this.options = options;
		this.patternToRuleMap = patternToRuleMap;

	}

	@Override
	public HiPEPattern transform(IBeXContextPattern ibexPattern) {
		if (name2pattern.containsKey(ibexPattern.getName())) {
			return name2pattern.get(ibexPattern.getName());
		}

		// Transform nodes, edges, injectivity and attribute constraints.
		HiPEPattern pattern = super.transform(ibexPattern);

		// Add to patterns.
		name2pattern.put(ibexPattern.getName(), pattern);
		return pattern;
	}

}
