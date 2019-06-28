package org.emoflon.ibex.gt.hipe.runtime;

import static org.emoflon.ibex.common.collections.CollectionFactory.cfactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.log4j.Logger;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.common.emf.EMFSaveUtils;
import org.emoflon.ibex.common.operational.IContextPatternInterpreter;
import org.emoflon.ibex.common.operational.IMatch;
import org.emoflon.ibex.common.operational.IMatchObserver;
import org.moflon.core.utilities.LogUtils;

import IBeXLanguage.IBeXContext;
import IBeXLanguage.IBeXContextAlternatives;
import IBeXLanguage.IBeXContextPattern;
import IBeXLanguage.IBeXLanguagePackage;
import IBeXLanguage.IBeXPatternSet;
import hipe.engine.HiPEContentAdapter;
import hipe.engine.IHiPEEngine;
import hipe.engine.match.ProductionMatch;
import hipe.engine.message.production.ProductionResult;
import hipe.network.HiPENetwork;
import hipe.searchplan.ui.HiPENetworkGraphUi;

/**
 * Engine for (unidirectional) graph transformations with HiPE.
 */
public class HiPEGTEngine implements IContextPatternInterpreter {
	private static final Logger logger = Logger.getLogger(HiPEGTEngine.class);

	/**
	 * The registry.
	 */
	protected Registry registry;

	/**
	 * The match observer.
	 */
	protected IMatchObserver app;
	
	
	protected IBeXPatternSet ibexPatternSet;

	/**
	 * The HiPE patterns.
	 */
	protected Map<String, String> patterns;
	
	protected String engineClassName;
	
	/**
	 * The pattern matcher module.
	 */
	protected IHiPEEngine engine;
	
	/**
	 * The EMF notification adapter -> delegates model notifications to the HiPE engine
	 */
	protected HiPEContentAdapter adapter;
	
	/**
	 * The Rete-Network used in the engine
	 */
	protected HiPENetwork network;

	/**
	 * The path for debugging output.
	 */
	protected Optional<String> debugPath = Optional.empty();

	private String workspacePath;

	/**
	 * Creates a new HiPEGTEngine.
	 */
	public HiPEGTEngine() {
		this.patterns = cfactory.createObjectToObjectHashMap();
	}
	
	// TODO FIXIT!
	@Override
	public ResourceSet createAndPrepareResourceSet(final String workspacePath) {
		this.workspacePath = workspacePath;
		ResourceSet resourceSet = new ResourceSetImpl();
		// In contrast to EMFDemoclesPatternMetamodelPlugin.createDefaultResourceSet, we
		// do not delegate directly to the global registry!
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		/*
		try {
			EMFDemoclesPatternMetamodelPlugin.setWorkspaceRootDirectory(resourceSet,
					new File(workspacePath).getCanonicalPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		*/
		return resourceSet;
	}
	
	/**
	 * Sets the ePackage registry and the match observer
	 * (1) should be called first.
	 */
	@Override
	public void initialise(Registry registry, IMatchObserver matchObserver) {
		this.registry = registry;
		this.app = matchObserver;
	}

	/**
	 * Sets the patterns as IBeXPatterns
	 * (2) should be called second.
	 */
	@Override
	public void initPatterns(final IBeXPatternSet ibexPatternSet) {
		this.ibexPatternSet = ibexPatternSet;
		setPatterns(this.ibexPatternSet);
	}
	
	protected void setPatterns(IBeXPatternSet ibexPatternSet) {
		this.patterns = cfactory.createObjectToObjectHashMap();

		for(IBeXContext context : ibexPatternSet.getContextPatterns()) {
			if(context instanceof IBeXContextPattern) {
				IBeXContextPattern pattern = (IBeXContextPattern) context;
				if(pattern.getSignatureNodes().isEmpty())
					continue;
				
				patterns.put(context.getName().replace("-", "_"), context.getName());
			}
			if(context instanceof IBeXContextAlternatives)
				for(IBeXContextPattern alternative : ((IBeXContextAlternatives) context).getAlternativePatterns()) {
					if(alternative.getSignatureNodes().isEmpty()) {
						continue;
					}
					
					patterns.put(alternative.getName().replace("-", "_"), alternative.getName());
				}
		}
	}
	
	protected void generateHiPEClassName(boolean generic) {
		URI patternURI = ibexPatternSet.eResource().getURI();
		Pattern pattern = Pattern.compile("^(.*src-gen/)(.*)(api/ibex-patterns.xmi)$");
		Matcher matcher = pattern.matcher(patternURI.toString());
		matcher.matches();
		String packageName = matcher.group(2);
		
		packageName = packageName.substring(0, packageName.length()-1);
		packageName = packageName.replace("/", ".");
		
		if(generic) {
			engineClassName = packageName+".hipe.generic.engine.GenericHiPEEngine";
		}
		else {
			engineClassName = packageName+".hipe.engine.HiPEEngine";
		}
	}
	
	protected void generateHiPEClassName(String projectName, boolean generic) {
		if(generic) {
			engineClassName = projectName.replace("/", ".")+".hipe.engine.generic.HiPEEngine";
		}
		else {
			engineClassName = projectName.replace("/", ".")+".hipe.engine.HiPEEngine";
		}
	}
	
	/**
	 * Starts the monitoring for the given patterns (i.e. starts engine)
	 * (3) should be called third.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void monitor(final ResourceSet resourceSet) {
		double tic = System.currentTimeMillis();
		for (Resource r : resourceSet.getResources()) {
			if ("ecore".equals(r.getURI().fileExtension())) {
				logger.warn("Are you sure your resourceSet should contain a resource for a metamodel?: " + r.getURI());
				logger.warn("You should probably initialise this metamodel and make sure your "
						+ "resourceSet only contains models to be monitored by the pattern matcher.");
			}
		}

		EcoreUtil.resolveAll(resourceSet);

		EcoreUtil.UnresolvedProxyCrossReferencer//
				.find(resourceSet)//
				.forEach((eob, settings) -> {
					logger.error("Problems resolving: " + eob);
					settings.forEach(setting -> {
						EObject o = setting.getEObject();
						EStructuralFeature f = setting.getEStructuralFeature();

						try {
							if (f.isMany()) {
								((Collection<Object>) o.eGet(f)).remove(eob);
								logger.warn(
										"Removed proxy from collection.  You should probably check why this cannot be resolved!");
							} else {
								o.eSet(f, null);
								logger.warn(
										"Removed proxy (set to null).  You should probably check why this cannot be resolved!");
							}
						} catch (Exception e) {
							logger.warn("Unable to remove proxy: " + e);
						}
					});
				});
		
		initEngine(resourceSet);
		double toc = System.currentTimeMillis();
		System.out.println("engine initialized after " + (toc-tic)/1000.0 + "s");
	}
	
	@SuppressWarnings("unchecked")
	protected void initEngine(final ResourceSet resourceSet) {
		
		
		Class<? extends IHiPEEngine> engineClass = null;
		boolean generic = false;
		generateHiPEClassName(generic);
		try {
			engineClass = (Class<? extends IHiPEEngine>) Class.forName(engineClassName);
		} catch (ClassNotFoundException e1) {
//			e1.printStackTrace();
		}

		if(engineClass == null) {
			generic = true;
			generateHiPEClassName(generic);
			try {
				engineClass = (Class<? extends IHiPEEngine>) Class.forName(engineClassName);
			} catch (ClassNotFoundException e1) {
				e1.printStackTrace();
			}
		}
		
		try {
			if(engineClass == null) {
				throw new RuntimeException("Engine class: "+engineClassName+ " -> not found!");
			}
			double tic = System.currentTimeMillis();
			Constructor<? extends IHiPEEngine> constructor = generic ? engineClass.getDeclaredConstructor(HiPENetwork.class) : engineClass.getDeclaredConstructor();
			constructor.setAccessible(true);
			if(generic) {
				String hipeFolder = workspacePath;
				URI patternURI = ibexPatternSet.eResource().getURI();
				Pattern pattern = Pattern.compile("^(.*src-gen/)(.*)(api/ibex-patterns.xmi)$");
				Matcher matcher = pattern.matcher(patternURI.toString());
				matcher.matches();
				String packageName = matcher.group(2);
				HiPENetwork network = loadNetwork(hipeFolder+"/" + packageName +"/hipe/hipe-network.xmi");
				if(network == null)
					throw new RuntimeException("No hipe-network.xmi could be fonud");
				engine = constructor.newInstance(network);
			}
			else {
				engine = constructor.newInstance();
			}
			//engine = engineClass.newInstance();
			double toc = System.currentTimeMillis();
			System.out.println("dynamic instantiation after " + (toc-tic)/1000.0 + "s");
		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | 
				SecurityException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		
		try {
			double tic = System.currentTimeMillis();
			engine.initialize();
			double toc = System.currentTimeMillis();
			System.out.println("initialization after " + (toc-tic)/1000.0 + "s");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		double tic = System.currentTimeMillis();
		adapter = new HiPEContentAdapter(resourceSet, engine);
		double toc = System.currentTimeMillis();
		System.out.println("added adapter after " + (toc-tic)/1000.0 + "s");
	}
	
	@Override
	public void updateMatches() {
		// Trigger the Rete network
		double tic = System.currentTimeMillis();
		try {
			Map<String, ProductionResult> extractData = engine.extractData();
			addNewMatches(extractData);
			double toc = System.currentTimeMillis();
			System.out.println("--> adding matches took: " + (toc-tic)/1000.0 + "s");
			deleteInvalidMatches(extractData);
			double toctoc = System.currentTimeMillis();
			System.out.println("--> deleting matches took: " + (toctoc-toc)/1000.0 + "s");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		double toc = System.currentTimeMillis();
		System.out.println("#### updated matches after " + (toc-tic)/1000.0 + "s");
	}
	
	private void addNewMatches(Map<String, ProductionResult> extractData) {
		for(String patternName : extractData.keySet()) {
			Collection<ProductionMatch> matches = extractData.get(patternName).getNewMatches();
			for(ProductionMatch match : matches) {
				IMatch iMatch = createMatch(match, patterns.get(patternName));
				if(iMatch.getPatternName() == null)
					continue;
				
				app.addMatch(iMatch);
				//System.out.println("added match: "+iMatch);
			}
		}
	}
	
	private void deleteInvalidMatches(Map<String, ProductionResult> extractData) {
		for(String patternName : extractData.keySet()) {
			Collection<ProductionMatch> matches = extractData.get(patternName).getDeleteMatches();
			for(ProductionMatch match : matches) {
				IMatch iMatch = createMatch(match, patterns.get(patternName));
				if(iMatch.getPatternName() == null)
					continue;
				
				app.removeMatch(iMatch);
				//System.out.println("deleted match: "+iMatch);
			}
		}
	}

	@Override
	public void terminate() {
		double tic = System.currentTimeMillis();
		engine.terminate();
		double toc = System.currentTimeMillis();
		System.out.println("shutdown after " + (toc-tic)/1000.0 + "s");
	}

	@Override
	public void setDebugPath(final String debugPath) {
		this.debugPath = Optional.of(debugPath);
	}
	
	protected HiPENetwork loadNetwork(String path) {
		Resource res = null;
		try {
			res = loadResource(path);
		} catch (Exception e) {
			LogUtils.error(logger, "Couldn't load ibex pattern set: \n" + e.getMessage());
			e.printStackTrace();
		}
		
		if(res == null) {
			return null;
		}
		return (HiPENetwork)res.getContents().get(0);
	}
	

	protected IMatch createMatch(final ProductionMatch match, final String patternName) {
		return new HiPEGTMatch(match, patternName);
	}

	private static Resource loadResource(String path) throws Exception {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("ibex-patterns-for-hipe", new XMIResourceFactoryImpl());
		ResourceSet rs = new ResourceSetImpl();
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
		rs.getPackageRegistry().put(IBeXLanguagePackage.eNS_URI, IBeXLanguagePackage.eINSTANCE);
		
		URI uri = URI.createFileURI(path);
		Resource modelResource = rs.getResource(uri, true);
		EcoreUtil.resolveAll(rs);
		
		if(modelResource == null)
			throw new IOException("File did not contain a vaild model.");
		return modelResource;
	}
}
