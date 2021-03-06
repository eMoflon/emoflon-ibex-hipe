package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.emf.codegen.ecore.generator.Generator;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenBaseGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.util.GenModelUtil;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.Monitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.Resource.Factory.Registry;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.importer.ecore.EcoreImporter;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.modules.TGGResourceHandler;
import org.moflon.core.utilities.MoflonUtil;
import org.moflon.emf.codegen.StandalonePackageDescriptor;
import org.moflon.emf.codegen.resource.GenModelResourceFactory;

public class HiPEBuilderUtil {
	public Collection<String> metaModelImports;
	public Collection<EPackage> importedPackages = new LinkedList<>();
	private IbexOptions options;

	public HiPEBuilderUtil(IbexOptions options) {
		this.options = options;
	}

	public void loadDefaultSettings() {
		TGGResourceHandler resourceHandler = options.resourceHandler();
		resourceHandler.getResourceSet().getPackageRegistry().put("http://www.eclipse.org/emf/2002/GenModel",
				new StandalonePackageDescriptor("org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage"));

		resourceHandler.getResourceSet().getResourceFactoryRegistry().getExtensionToFactoryMap().put("genmodel",
				new GenModelResourceFactory());
		resourceHandler.getResourceSet().getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi",
				new XMIResourceFactoryImpl());
	}

	protected void generateMetaModelCode(URI base, String metaModelLocation, String genModelLocation,
			EPackage metaModel) {		
		String pluginID = options.project.name();
		
		URI metaModelUri = URI.createURI(metaModelLocation);
		metaModelUri = metaModelUri.resolve(base);

		Monitor monitor = BasicMonitor.toMonitor(new NullProgressMonitor());
		try {
			EcoreImporter importer = new EcoreImporter();
			importer.setModelLocation(metaModelUri.toString());
			importer.computeEPackages(monitor);
			importer.adjustEPackages(monitor);
			
			for(EPackage ePackage : importer.getEPackages()) {
				if(ePackage.getName().equals(metaModel.getName())) {
					importer.getEPackageConvertInfo(ePackage).setConvert(true);
				}else {
					importer.getEPackageConvertInfo(ePackage).setConvert(false);
				}
			}			
			
			importer.setGenModelContainerPath(new Path(pluginID).append("model"));
			importer.setGenModelFileName(importer.computeDefaultGenModelFileName());
			importer.prepareGenModelAndEPackages(monitor);

			GenModel genModel = importer.getGenModel();
			genModel.setModelDirectory(options.project.path() + "/gen/");
			
		    Set<GenPackage> removals = genModel.getGenPackages().stream().filter(pkg -> !pkg.getEcorePackage().getName().equals(metaModel.getName())).collect(Collectors.toSet());
			removals.forEach(pkg -> genModel.getGenPackages().remove(pkg));
			genModel.getUsedGenPackages().addAll(removals);
			
			Map<String, URI> pack2genMapEnv = EcorePlugin.getEPackageNsURIToGenModelLocationMap(false);
			Map<String, URI> pack2genMapTarget = EcorePlugin.getEPackageNsURIToGenModelLocationMap(true);

			Map<String, URI> uriName2PluginUri = new HashMap<>();
			for(URI uri : pack2genMapEnv.values()) {
				uriName2PluginUri.put(uri.toString(), uri);
			}
			Map<String, URI> uriName2ResourceUri = new HashMap<>();
			for(URI uri : pack2genMapTarget.values()) {
				uriName2ResourceUri.put(uri.toString(), uri);
			}
			
//			List<GenPackage> removalList = removals.stream().collect(Collectors.toList());
			
			// create dummy genmodels or else the genpackages can not be found and thus persisted
			for(GenPackage gp : removals) {
				// search first in environment in case that the genmodel is exported as plugin
				URI genURI = pack2genMapEnv.get(gp.getNSURI());
				if(genURI == null)
					genURI = pack2genMapTarget.get(gp.getNSURI());
				ResourceSet rs = new ResourceSetImpl();
				Resource createResource = rs.createResource(genURI);
				createResource.load(null);
				if(createResource.isLoaded()) {
					GenModel newGen = (GenModel) createResource.getContents().get(0);
					genModel.getUsedGenPackages().remove(gp);
					genModel.getUsedGenPackages().add(newGen.getGenPackages().get(0));
					fixSubPackages(newGen, pack2genMapEnv, pack2genMapTarget, uriName2PluginUri, uriName2ResourceUri);
				}
				else {
					GenModel fakeGen = GenModelFactory.eINSTANCE.createGenModel();
					fakeGen.setModelPluginID(gp.getEcorePackage().getNsPrefix());
					fakeGen.getGenPackages().add(gp);
					genModel.eResource().getContents().add(fakeGen);
				}
			}
			
			genModel.setGenerateSchema(true);
			genModel.setCanGenerate(true);
		    genModel.reconcile();

			EcoreUtil.resolveAll(importer.getGenModelResourceSet());
		    genModel.eResource().save(Collections.emptyMap());
		    
			Generator generator = GenModelUtil.createGenerator(genModel);
		    generator.setInput(genModel);
			generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, monitor);
		} catch (Exception e) {
			System.err.println("Could not generate TGG metamodel code!\nException: \n"+e.getMessage());
		}
	}

	private void fixSubPackages(GenModel genModel, Map<String, URI> pack2genMapEnv, Map<String, URI> pack2genMapTarget, Map<String, URI> uriName2PluginUri, Map<String, URI> uriName2ResourceUri) {
		Collection<GenPackage> usedPackages = genModel.getUsedGenPackages().stream().collect(Collectors.toList());
			
		for(GenPackage gp : usedPackages) {
			// if genmodel is already there -> continue
			if(gp.getGenModel() != null) {
				fixSubPackages(gp.getGenModel(), pack2genMapEnv, pack2genMapTarget, uriName2PluginUri, uriName2ResourceUri);
				continue;
			}
			
			// else try to load it from registry and continue
			try {
				if(gp.getEcorePackage() != null) {
					String nsUri = gp.getNSURI();
					URI genURI = pack2genMapEnv.get(nsUri);
					if(genURI == null)
						genURI = pack2genMapTarget.get(nsUri);
					ResourceSet rs = new ResourceSetImpl();
					Resource createResource = rs.createResource(genURI);
					createResource.load(null);
					if(createResource.isLoaded()) {
						GenModel newGen = (GenModel) createResource.getContents().get(0);
						fixSubPackages(newGen, pack2genMapEnv, pack2genMapTarget, uriName2PluginUri, uriName2ResourceUri);
					}
					continue;
				}
			} catch(Exception e) {
			}
			
			// if uri was null -> try to extract the uri from proxyURI via toString (ugly) and try again
			String gpString = gp.toString();
			if(!gpString.contains("eProxyURI"))
				throw new RuntimeException("Cannot generate GenModel for " + gpString);
			
			String genUri = gpString.trim().split("eProxyURI:")[1];
			if(genUri.contains("#")) {
				genUri = genUri.trim().split("#")[0];
			}

			String resourceNSURI = genUri;
			String pluginNSURI = resourceNSURI.replace("/resource/", "/plugin/");
			
			URI pluginUri = null;
			if(uriName2PluginUri.containsKey(pluginNSURI)) 
				pluginUri = uriName2PluginUri.get(pluginNSURI);
			else
				if(uriName2ResourceUri.containsKey(pluginNSURI))
					pluginUri = uriName2ResourceUri.get(pluginNSURI);
			
			if(setGenModel(pluginUri, genModel, gp, pack2genMapEnv, pack2genMapTarget, uriName2PluginUri, uriName2ResourceUri))
				continue;
			
			URI resourceUri = null;
			if(uriName2PluginUri.containsKey(resourceNSURI)) 
				resourceUri = uriName2PluginUri.get(resourceNSURI);
			else
				if(uriName2ResourceUri.containsKey(resourceNSURI))
					resourceUri = uriName2ResourceUri.get(resourceNSURI);
			
			if(setGenModel(resourceUri, genModel, gp, pack2genMapEnv, pack2genMapTarget, uriName2PluginUri, uriName2ResourceUri))
				continue;
			
			throw new RuntimeException("Cannot generate GenModel for " + gpString);
		}
	}
	
	public boolean setGenModel(URI uri, GenModel gen, GenPackage pkg, Map<String, URI> pack2genMapEnv, Map<String, URI> pack2genMapTarget, Map<String, URI> uriName2PluginUri, Map<String, URI> uriName2ResourceUri) {
		try {
			ResourceSet rs = new ResourceSetImpl();
			Resource createResource = rs.createResource(uri);
			createResource.load(null);
			GenModel newGen = (GenModel) createResource.getContents().get(0);
			GenPackage newPkg = (GenPackage) newGen.eContents().stream().filter(c -> c instanceof GenPackage).findFirst().get();
			gen.getUsedGenPackages().remove(pkg);
			gen.getUsedGenPackages().add(newPkg);
			fixSubPackages(newGen, pack2genMapEnv, pack2genMapTarget, uriName2PluginUri, uriName2ResourceUri);
		} catch(Exception e) {
			return false;
		}
		return true;
	}

	public Collection<EPackage> getImportedPackages() {
		return importedPackages;
	}

	public static IbexOptions registerResourceHandler(IbexOptions options, List<String> metaModelImports, boolean generateCode)
			throws IOException {
		HiPEBuilderUtil util = new HiPEBuilderUtil(options);
		options.resourceHandler(new TGGResourceHandler() {
			@Override
			protected void registerUserMetamodels() throws IOException {
				for (String imp : metaModelImports) {
					util.getImportedPackages().add(loadAndRegisterMetamodel(imp));
				}
				String metaModelLocation = options.project.name() + "/model/"
						+ MoflonUtil.lastCapitalizedSegmentOf(options.project.name()) + ".ecore";
				String genModelLocation = options.project.name() + "/model/"
						+ MoflonUtil.lastCapitalizedSegmentOf(options.project.name()) + ".genmodel";
				EPackage metaModel = loadAndRegisterCorrMetamodel();
				if(generateCode)
					util.generateMetaModelCode(base, metaModelLocation, genModelLocation, metaModel);
			}

			@Override
			protected void createAndPrepareResourceSet() {
				rs = new ResourceSetImpl();
				util.loadDefaultSettings();
			}

			@Override
			public void loadModels() throws IOException {
			}

			@Override
			public void saveModels() throws IOException {
			}
		});
		return options;
	}
}