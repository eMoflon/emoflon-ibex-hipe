package org.emoflon.ibex.tgg.runtime.hipe;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.emoflon.ibex.common.operational.IMatch;
import org.emoflon.ibex.common.operational.IMatchObserver;
import org.emoflon.ibex.gt.hipe.runtime.HiPEGTEngine;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXContext;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXModel;
import org.emoflon.ibex.patternmodel.IBeXPatternModel.IBeXPatternSet;
import org.emoflon.ibex.tgg.compiler.patterns.PatternSuffixes;
import org.emoflon.ibex.tgg.compiler.patterns.PatternUtil;
import org.emoflon.ibex.tgg.operational.IBlackInterpreter;
import org.emoflon.ibex.tgg.operational.benchmark.TimeMeasurable;
import org.emoflon.ibex.tgg.operational.benchmark.TimeRegistry;
import org.emoflon.ibex.tgg.operational.benchmark.Timer;
import org.emoflon.ibex.tgg.operational.benchmark.Times;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.gen.MODELGEN;
import org.emoflon.ibex.tgg.operational.strategies.integrate.INTEGRATE;
import org.emoflon.ibex.tgg.operational.strategies.modules.IbexExecutable;
import org.emoflon.ibex.tgg.operational.strategies.opt.CC;
import org.emoflon.ibex.tgg.operational.strategies.opt.CO;
import org.emoflon.ibex.tgg.operational.strategies.sync.INITIAL_BWD;
import org.emoflon.ibex.tgg.operational.strategies.sync.INITIAL_FWD;
import org.emoflon.ibex.tgg.operational.strategies.sync.SYNC;

import hipe.engine.IHiPEEngine;
import hipe.engine.match.ProductionMatch;
import hipe.engine.message.production.ProductionResult;

/**
 * Engine for (bidirectional) graph transformations with HiPE.
 */
public class HiPETGGEngine extends HiPEGTEngine implements IBlackInterpreter, TimeMeasurable {
	private IbexOptions options;
	private IBeXPatternSet ibexPatterns;
	private IbexExecutable executable;
	private final Times times = new Times();
	
	/**
	 * Creates a new DemoclesTGGEngine.
	 */
	public HiPETGGEngine() {
		super();
		TimeRegistry.register(this);
	}
	
	public HiPETGGEngine(IHiPEEngine engine) {
		this();
		this.engine = engine;
	}

	@Override
	public void initialise(IbexExecutable executable, final IbexOptions options, Registry registry,  IMatchObserver matchObserver) {
		super.initialise(registry, matchObserver);
		
		this.options = options;
		this.executable = executable; 
		
		Resource r = null;
		try {
			r = loadResource(options.project.path() + "/debug/" +  getIbexPatternFileName());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		IBeXModel ibexModel = (IBeXModel)r.getContents().get(0);
		ibexPatterns = ibexModel.getPatternSet();
		
		for(IBeXContext context : ibexPatterns.getContextPatterns()) {
			PatternUtil.registerPattern(context.getName(), PatternSuffixes.extractType(context.getName()));
		}
		
		initPatterns(ibexPatterns);
	}

	@Override
	public void initPatterns(final IBeXPatternSet ibexPatternSet) {
		this.ibexPatternSet = ibexPatternSet;
		setPatterns(ibexPatternSet);
		generateHiPEClassName(options.project.name());
	}	
	
//	@Override
	protected String getPackageName(URI patternURI) {
		Pattern pattern = Pattern.compile("^(.*/)(.*)(/debug/.*)$");
		Matcher matcher = pattern.matcher(patternURI.toString());
		matcher.matches();
		String packageName = matcher.group(2);
		return packageName;
	}
	
	@Override
	protected String getProjectName() {
		return options.project.name();
	}
	
	private String getIbexPatternFileName() {
		if(executable instanceof INITIAL_FWD) {
			return "initial_fwd_ibexPatterns.xmi";
		}
		if(executable instanceof INITIAL_BWD) {
			return "initial_bwd_ibexPatterns.xmi";
		}
		if(executable instanceof SYNC) {
			return "sync_ibexPatterns.xmi";
		}
		if(executable instanceof CC) {
			return "cc_ibexPatterns.xmi";
		}
		if(executable instanceof CO) {
			return "co_ibexPatterns.xmi";
		}
		if(executable instanceof MODELGEN) {
			return "modelgen_ibexPatterns.xmi";
		}
		if(executable instanceof INTEGRATE) {
			return "integrate_ibexPatterns.xmi";
		}
		throw new RuntimeException("Unsupported operationalization detected! - " + executable.getClass().getSimpleName());
	}
	
	@Override
	protected String getNetworkFileName() {
		if(executable instanceof INITIAL_FWD) {
			return "initial_fwd_hipe-network.xmi";
		}
		if(executable instanceof INITIAL_BWD) {
			return "initial_bwd_hipe-network.xmi";
		}
		if(executable instanceof SYNC) {
			return "sync_hipe-network.xmi";
		}
		if(executable instanceof CC) {
			return "cc_hipe-network.xmi";
		}
		if(executable instanceof CO) {
			return "co_hipe-network.xmi";
		}
		if(executable instanceof MODELGEN) {
			return "modelgen_hipe-network.xmi";
		}
		if(executable instanceof INTEGRATE) {
			return "integrate_hipe-network.xmi";
		}
		throw new RuntimeException("Unsupported operationalization detected! - " + executable.getClass().getSimpleName());
	}
	
	@Override
	protected void generateHiPEClassName(String projectName) {
		if(executable instanceof INITIAL_FWD) {
			engineClassName = projectName.replace("/", ".")+".initfwd.hipe.engine.HiPEEngine";	
		} 
		else if(executable instanceof INITIAL_FWD) {
			engineClassName = projectName.replace("/", ".")+".initbwd.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CC) {
			engineClassName = projectName.replace("/", ".")+".cc.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CO) {
			engineClassName = projectName.replace("/", ".")+".co.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof SYNC) {
			engineClassName = projectName.replace("/", ".")+".sync.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof INTEGRATE) {
			engineClassName = projectName.replace("/", ".")+".integrate.hipe.engine.HiPEEngine";	
		}
		else {
			engineClassName = projectName.replace("/", ".")+".modelgen.hipe.engine.HiPEEngine";	
		}
	}
	
	@Override
	public void monitor(final Collection<Resource> resources) {
		if (options.debug.ibexDebug()) {
			savePatterns(resourceSet, options.project.path() + "/debug/ibex-patterns.xmi", Arrays.asList(ibexPatterns));
		}

		super.monitor(resources);
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
	protected void addNewMatches(Map<String, ProductionResult> extractData) {
		// TODO Auto-generated method stub
		if(!options.patterns.parallelizeMatchProcessing()) {
			super.addNewMatches(extractData);
			return;
		}
		
		for(String patternName : extractData.keySet()) {
			if(patterns.get(patternName) == null)
				continue;
			String pName = patterns.get(patternName);
			Collection<ProductionMatch> matches = extractData.get(patternName).getNewMatches();
			Collection<IMatch> iMatches = matches.parallelStream().map(m -> createMatch(m, pName)).collect(Collectors.toList());
			app.addMatches(iMatches);
		}
	}
	
	@Override
	protected void deleteInvalidMatches(Map<String, ProductionResult> extractData) {
		if(!options.patterns.parallelizeMatchProcessing()) {
			super.deleteInvalidMatches(extractData);
			return;
		}
		
		for(String patternName : extractData.keySet()) {
			if(patterns.get(patternName) == null)
				continue;
			String pName = patterns.get(patternName);
			Collection<ProductionMatch> matches = extractData.get(patternName).getDeleteMatches();
			Collection<IMatch> iMatches = matches.parallelStream().map(m -> createMatch(m, pName)).collect(Collectors.toList());
			app.removeMatches(iMatches);
		}
	}

	@Override
	protected IMatch createMatch(ProductionMatch match, final String patternName) {
		return new HiPETGGMatch(match, patternName);
	}
	
	@Override
	public void updateMatches() {
		Timer.start();
		// Trigger the Rete network
		try {
			Map<String, ProductionResult> extractData = engine.extractData();
			times.addTo("findMatches", Timer.stop());
			addNewMatches(extractData);
			deleteInvalidMatches(extractData);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public Times getTimes() {
		return times;
	}
}
