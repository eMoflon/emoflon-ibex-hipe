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

	/**
	 * Creates a new HiPEGTEngine.
	 */
	public HiPEGTEngine() {
		this.patterns = cfactory.createObjectToObjectHashMap();
	}

	@Override
	public ResourceSet createAndPrepareResourceSet(final String workspacePath) {
		ResourceSet resourceSet = new ResourceSetImpl();
		resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
				.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
		try {
			resourceSet.getURIConverter().getURIMap().put(URI.createPlatformResourceURI("/", true), URI.createFileURI(new File(workspacePath).getCanonicalPath() + File.separator));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
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
		generateHiPEClassName();
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
	
	protected void generateHiPEClassName() {
		URI patternURI = ibexPatternSet.eResource().getURI();
		Pattern pattern = Pattern.compile("^(.*src-gen/)(.*)(api/ibex-patterns.xmi)$");
		Matcher matcher = pattern.matcher(patternURI.toString());
		matcher.matches();
		String packageName = matcher.group(2);
		
		packageName = packageName.substring(0, packageName.length()-1);
		packageName = packageName.replace("/", ".");
		
		engineClassName = packageName+".hipe.engine.HiPEEngine";
	}
	
	protected void generateHiPEClassName(String projectName) {
		engineClassName = projectName.replace("/", ".")+".hipe.engine.HiPEEngine";
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
		for(EObject content : res.getContents()) {
			if(content instanceof HiPENetwork)
				return (HiPENetwork) content;
		}
		return null;
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
	
	/**
	 * Starts the monitoring for the given patterns (i.e. starts engine)
	 * (3) should be called third.
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void monitor(final ResourceSet resourceSet) {
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
	}
	
	@SuppressWarnings("unchecked")
	protected void initEngine(final ResourceSet resourceSet) {
		
		
		Class<? extends IHiPEEngine> engineClass = null;
		try {
			engineClass = (Class<? extends IHiPEEngine>) Class.forName(engineClassName);
		} catch (ClassNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		try {
			if(engineClass == null) {
				throw new RuntimeException("Engine class: "+engineClassName+ " -> not found!");
			}
			Constructor<? extends IHiPEEngine> constructor = engineClass.getConstructor(HiPENetwork.class);
			constructor.setAccessible(true);
			
			HiPENetwork network = loadNetwork("../" + getProjectName() +"/debug/" + getNetworkFileName());
			if(network == null)
				throw new RuntimeException("No hipe-network.xmi could be found");
			engine = constructor.newInstance(network);
		} catch (InstantiationException | IllegalAccessException | NoSuchMethodException | 
				SecurityException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		
		try {
			engine.initialize();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		adapter = new HiPEContentAdapter(resourceSet, engine);
	}
	
	protected String getProjectName() {
		URI patternURI = ibexPatternSet.eResource().getURI();
		Pattern pattern = Pattern.compile("../(.*)/src-gen/(.*)(/api/ibex-patterns.xmi)$");
		Matcher matcher = pattern.matcher(patternURI.toString());
		matcher.matches();
		String packageName = matcher.group(1);
		return packageName;
	}
	
	protected String getNetworkFileName() {
		return "hipe-network.xmi";
	}
	
	@Override
	public void updateMatches() {
		// Trigger the Rete network
		try {
			Map<String, ProductionResult> extractData = engine.extractData();
			addNewMatches(extractData);
			deleteInvalidMatches(extractData);
			app.notifySubscriptions();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	private void addNewMatches(Map<String, ProductionResult> extractData) {
		for(String patternName : extractData.keySet()) {
			Collection<ProductionMatch> matches = extractData.get(patternName).getNewMatches();
			for(ProductionMatch match : matches) {
				IMatch iMatch = createMatch(match, patterns.get(patternName));
				if(iMatch.getPatternName() == null)
					continue;
				
				app.addMatch(iMatch);
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
			}
		}
	}

	@Override
	public void terminate() {
		adapter.removeAdapter();
		engine.terminate();
	}

	@Override
	public void setDebugPath(final String debugPath) {
		this.debugPath = Optional.of(debugPath);
	}
	
	protected void saveNetworkForDebugging() {
		debugPath.ifPresent(path -> {
			List<HiPENetwork> network = new LinkedList<>();
			network.add(this.network);
			EMFSaveUtils.saveModel(network, path + "/hipe-network");
		});
	}

	

	protected IMatch createMatch(final ProductionMatch match, final String patternName) {
		return new HiPEGTMatch(match, patternName);
	}

}
