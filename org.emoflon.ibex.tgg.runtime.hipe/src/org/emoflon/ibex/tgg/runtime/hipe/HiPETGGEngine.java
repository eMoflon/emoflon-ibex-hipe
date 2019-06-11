package org.emoflon.ibex.tgg.runtime.hipe;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

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
import org.emoflon.ibex.tgg.operational.strategies.opt.CO;
import org.emoflon.ibex.tgg.operational.strategies.opt.cc.CC;

import IBeXLanguage.IBeXContextPattern;
import IBeXLanguage.IBeXPatternSet;
import hipe.engine.match.ProductionMatch;
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
		setPatterns(ibexPatternSet);
		generateHiPEClassName(options.projectName());
	}	
	
	@Override
	protected void generateHiPEClassName(String projectName) {
		if(strategy instanceof CC) {
			engineClassName = projectName.replace("/", ".")+".cc.hipe.engine.HiPEEngine";	
		}
		else if(strategy instanceof CO) {
			engineClassName = projectName.replace("/", ".")+".co.hipe.engine.HiPEEngine";	
		}
		else {
			engineClassName = projectName.replace("/", ".")+".sync.hipe.engine.HiPEEngine";	
		}
	}
	
	@Override
	public void monitor(final ResourceSet resourceSet) {
		if (options.debug()) {
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
	protected IMatch createMatch(ProductionMatch match, final String patternName) {
		return new HiPETGGMatch(match, patternName);
	}
	
}
