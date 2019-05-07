package org.emoflon.ibex.tgg.runtime.engine;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emoflon.ibex.common.operational.IMatch;
import org.emoflon.ibex.common.operational.IMatchObserver;
import org.emoflon.ibex.gt.hipe.runtime.HiPEGTEngine;
import org.emoflon.ibex.gt.hipe.runtime.IBeXToHiPEPatternTransformation;
import org.emoflon.ibex.tgg.compiler.transformations.patterns.ContextPatternTransformation;
import org.emoflon.ibex.tgg.operational.IBlackInterpreter;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.OperationalStrategy;
import org.emoflon.ibex.tgg.runtime.engine.csp.nativeOps.TGGAttributeConstraintAdornmentStrategy;
import org.emoflon.ibex.tgg.runtime.engine.csp.nativeOps.TGGAttributeConstraintModule;
import org.emoflon.ibex.tgg.runtime.engine.csp.nativeOps.TGGAttributeConstraintTypeModule;
import org.emoflon.ibex.tgg.runtime.engine.csp.nativeOps.TGGConstraintComponentBuilder;
import org.emoflon.ibex.tgg.runtime.engine.csp.nativeOps.TGGNativeOperationBuilder;

import IBeXLanguage.IBeXContextPattern;
import IBeXLanguage.IBeXPatternSet;
import hipe.engine.match.ProductionMatch;
import hipe.pattern.HiPEAbstractPattern;
import language.TGGNamedElement;

/**
 * Engine for (bidirectional) graph transformations with Democles.
 */
public class HiPETGGEngine extends HiPEGTEngine implements IBlackInterpreter {
	private IbexOptions options;
	private IBeXPatternSet ibexPatterns;
	private Map<IBeXContextPattern, TGGNamedElement> patternToRuleMap;
	private OperationalStrategy strategy;

	/**
	 * Creates a new DemoclesTGGEngine.
	 */
	public HiPETGGEngine() {
		super();
	}

	@Override
	public void initialise(final IbexOptions options, Registry registry, IMatchObserver matchObserver) {
		super.initialise(registry, matchObserver);
		
		this.options = options;
		this.strategy = (OperationalStrategy) matchObserver;
		
		ContextPatternTransformation compiler = new ContextPatternTransformation(options, strategy);
		ibexPatterns = compiler.transform();
		patternToRuleMap = compiler.getPatternToRuleMap();
		initPatterns(ibexPatterns);
	}

	@Override
	public void initPatterns(final IBeXPatternSet ibexPatternSet) {
		IBeXToHiPEPatternTransformation transformation = new TGGIBeXToHiPEPatternTransformation(options,
				patternToRuleMap);
		this.ibexPatternSet = ibexPatternSet;
		setPatterns(transformation.transform(ibexPatternSet));
		generateHiPENetworkCode();
		savePatternsForDebugging();
		saveNetworkForDebugging();
	}
	
	//TODO: wtf is this?
	/*
	private Optional<TGGConstraintComponentBuilder<VariableRuntime>> handleTGGAttributeConstraints() {
		if (!this.options.blackInterpSupportsAttrConstrs()) {
			return Optional.empty();
		}

		// Handle constraints for the EMF to Java transformation
		TGGAttributeConstraintModule.INSTANCE.registerConstraintTypes(options.constraintProvider());
		TypeModule<TGGAttributeConstraintModule> tggAttributeConstraintTypeModule = new TGGAttributeConstraintTypeModule(
				TGGAttributeConstraintModule.INSTANCE);
		patternBuilder.addConstraintTypeSwitch(tggAttributeConstraintTypeModule.getConstraintTypeSwitch());

		// Native operation
		final TGGNativeOperationBuilder<VariableRuntime> tggNativeOperationModule = new TGGNativeOperationBuilder<VariableRuntime>(
				options.constraintProvider());
		// Batch operations
		final GenericOperationBuilder<VariableRuntime> tggBatchOperationModule = new GenericOperationBuilder<VariableRuntime>(
				tggNativeOperationModule, TGGAttributeConstraintAdornmentStrategy.INSTANCE);
		retePatternMatcherModule.addOperationBuilder(tggBatchOperationModule);

		// Incremental operation
		return Optional.of(new TGGConstraintComponentBuilder<VariableRuntime>(tggNativeOperationModule));
	}
	*/
	
	@Override
	public void monitor(final ResourceSet resourceSet) {
		if (options.debug()) {
			savePatterns(resourceSet, options.projectPath() + "/debug/hipe-patterns.xmi", patterns.values()//
					.stream()//
					.sorted((p1, p2) -> p1.getName().compareTo(p2.getName()))//
					.collect(Collectors.toList()));

			savePatterns(resourceSet, options.projectPath() + "/debug/ibex-patterns.xmi", Arrays.asList(ibexPatterns));
		}

		super.monitor(resourceSet);
	}

	/**
	 * Use this method to get extra debug information concerning the rete network.
	 * Currently not used to reduce debug output.
	 */
	@SuppressWarnings("unused")
	private void printReteNetwork() {
		//TODO
	}

	private void savePatterns(ResourceSet rs, String path, Collection<EObject> patterns) {
		Resource hipePatterns = rs.createResource(URI.createPlatformResourceURI(path, true));
		hipePatterns.getContents().addAll(patterns);
		try {
			hipePatterns.save(null);
			rs.getResources().remove(hipePatterns);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected IMatch createMatch(ProductionMatch match, HiPEAbstractPattern pattern) {
		return new HiPETGGMatch(match, pattern);
	}
	
}
