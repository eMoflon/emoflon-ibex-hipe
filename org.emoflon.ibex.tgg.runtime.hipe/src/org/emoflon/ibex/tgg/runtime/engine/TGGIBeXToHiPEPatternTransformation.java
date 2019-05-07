package org.emoflon.ibex.tgg.runtime.engine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.emoflon.ibex.common.patterns.IBeXPatternUtils;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.tgg.operational.csp.sorting.SearchPlanAction;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;

import IBeXLanguage.IBeXContextPattern;
import hipe.pattern.HiPEPattern;
import language.NAC;
import language.TGGAttributeConstraint;
import language.TGGAttributeConstraintLibrary;
import language.TGGNamedElement;
import language.TGGRule;

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

		// TODO: Handle TGG attribute constraints
		// wtf happens here?
		/*
		if (options.blackInterpSupportsAttrConstrs()) {
			HiPEAttributeHelper helper = new HiPEAttributeHelper(options, pattern);
			TGGNamedElement tggElement = patternToRuleMap.get(ibexPattern);
			if (tggElement != null) {
				Map<String, EMFVariable> nameToVar = new HashMap<>();
				patternHelper.getNodeToVariableMapping().keySet()
						.forEach(k -> nameToVar.put(k.getName(), patternHelper.getNodeToVariableMapping().get(k)));
				helper.createAttributeConstraints(getAttributeConstraintsForPattern(tggElement, ibexPattern), body,
						nameToVar, democlesPattern.getSymbolicParameters());
			}
		}
		*/
		// TODO: Transform each invocations to a PatternInvocationConstraint.
		// I think this step is unnecessary because it was already done in line 39

		// Add to patterns.
		name2pattern.put(ibexPattern.getName(), pattern);
		return pattern;
	}

	private Collection<TGGAttributeConstraint> getAttributeConstraintsForPattern(TGGNamedElement tggElement,
			IBeXContextPattern pattern) {
		TGGAttributeConstraintLibrary library = null;

		if (tggElement instanceof TGGRule) {
			TGGRule rule = (TGGRule) tggElement;
			assert (rule != null && rule.getAttributeConditionLibrary() != null);
			library = rule.getAttributeConditionLibrary();
		} else if (tggElement instanceof NAC) {
			NAC nac = (NAC) tggElement;
			library = nac.getAttributeConditionLibrary();
		}

		Collection<TGGAttributeConstraint> attributeConstraints = library.getTggAttributeConstraints();

		return attributeConstraints//
				.stream()//
				.filter(c -> isBlackAttributeConstraintInPattern(c, pattern))//
				.collect(Collectors.toList());
	}

	private boolean isBlackAttributeConstraintInPattern(TGGAttributeConstraint constraint, IBeXContextPattern pattern) {
		return constraint.getParameters()//
				.stream()//
				.allMatch(p -> SearchPlanAction.isConnectedToPattern(p, n -> IBeXPatternUtils.getAllNodes(pattern)//
						.stream()//
						.anyMatch(node -> node.getName().contentEquals(n))));
	}
}
