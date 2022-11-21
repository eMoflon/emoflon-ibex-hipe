package org.emoflon.ibex.tgg.runtime.hipe;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
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
import org.emoflon.ibex.tgg.runtime.IBlackInterpreter;
import org.emoflon.ibex.tgg.runtime.benchmark.TimeMeasurable;
import org.emoflon.ibex.tgg.runtime.benchmark.TimeRegistry;
import org.emoflon.ibex.tgg.runtime.benchmark.Timer;
import org.emoflon.ibex.tgg.runtime.benchmark.Times;
import org.emoflon.ibex.tgg.runtime.strategies.integrate.INTEGRATE;
import org.emoflon.ibex.tgg.runtime.strategies.modules.IbexExecutable;
import org.emoflon.ibex.tgg.runtime.strategies.opt.CC;
import org.emoflon.ibex.tgg.runtime.strategies.opt.CO;
import org.emoflon.ibex.tgg.runtime.strategies.sync.INITIAL_BWD;
import org.emoflon.ibex.tgg.runtime.strategies.sync.INITIAL_FWD;
import org.emoflon.ibex.tgg.runtime.strategies.sync.SYNC;
import org.emoflon.ibex.util.config.IbexOptions;

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
	 * Creates a new HiPETGGEngine.
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
	public void initialise(IbexExecutable executable, final IbexOptions options, Registry registry, IMatchObserver matchObserver) {
		super.initialise(registry, matchObserver);
		
		this.options = options;
		this.executable = executable; 
		String cp = "";
		
		String path = executable.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
		// this is a fix for situation where emoflon is executed within an eclipse plugin
		if(!path.contains("bin/"))
			path += "bin/";
		path +=  generateHiPEClassName().replace(".", "/").replace("HiPEEngine", "ibex-patterns.xmi");
		
		File file = new File(path);
		try {
			cp = file.getCanonicalPath();
			cp = cp.replace("%20", " ");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		Resource r = null;
		try {
			//r = loadResource("file://" + executable.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()+ generateHiPEClassName().replace(".", "/").replace("HiPEEngine", "ibex-patterns.xmi"));
			r = loadResource("file://" + cp);
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
		engineClassName = generateHiPEClassName();
	}	
	
	@Override
	protected String getProjectName() {
		return options.project.name();
	}
	
	@Override
	protected String generateHiPEClassName() {
		String projectName = options.project.name();
		if(executable instanceof INITIAL_FWD) {
			return projectName.replace("/", ".")+".initfwd.hipe.engine.HiPEEngine";	
		} 
		else if(executable instanceof INITIAL_BWD) {
			return projectName.replace("/", ".")+".initbwd.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CC) {
			return projectName.replace("/", ".")+".cc.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof CO) {
			return projectName.replace("/", ".")+".co.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof SYNC) {
			return projectName.replace("/", ".")+".sync.hipe.engine.HiPEEngine";	
		}
		else if(executable instanceof INTEGRATE) {
			return projectName.replace("/", ".")+".integrate.hipe.engine.HiPEEngine";	
		}
		else {
			return projectName.replace("/", ".")+".modelgen.hipe.engine.HiPEEngine";	
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
		
		Collection<IMatch> iMatches = new LinkedList<>();
		for(String patternName : extractData.keySet()) {
			if(patterns.get(patternName) == null)
				continue;
			String pName = patterns.get(patternName);
			Collection<ProductionMatch> matches = extractData.get(patternName).getNewMatches();
			iMatches.addAll(matches.parallelStream().map(m -> createMatch(m, pName)).collect(Collectors.toList()));
		}
		app.addMatches(iMatches);
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
	
	public IbexOptions getOptions() {
		return options;
	}
	
	@Override
	protected boolean cascadingNotifications(Collection<Resource> resources) {
		return options.project.usesSmartEMF();
	}

	@Override
	protected boolean initializeLazy() {
		return options.project.usesSmartEMF();
	}
}
