package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.Path;
import org.eclipse.emf.codegen.ecore.genmodel.GenJDKLevel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenBaseGeneratorAdapter;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.tgg.compiler.defaults.IRegistrationHelper;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.modules.IbexExecutable;
import org.emoflon.ibex.tgg.operational.strategies.modules.TGGResourceHandler;
import org.moflon.core.utilities.MoflonUtil;
import org.moflon.emf.codegen.StandalonePackageDescriptor;
import org.moflon.emf.codegen.resource.GenModelResource;
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
		resourceHandler.getResourceSet().getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
	}
	
	protected void generateMetaModelCode(URI base, String metaModelLocation, String genModelLocation, EPackage metaModel) {
		URI modelDirUri = URI.createURI(options.project.path() + "/model/");
		modelDirUri =  modelDirUri.resolve(base);
		URI metaModelUri = URI.createURI(metaModelLocation);
		metaModelUri = metaModelUri.resolve(base);
		URI genModelUri = URI.createURI(genModelLocation);
		genModelUri = genModelUri.resolve(base);
		
		final GenModelResource genModelResource = (GenModelResource) options.resourceHandler().getResourceSet().createResource(genModelUri);
		GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
		
		genModelResource.getContents().add(genModel);

		adjustRegistry(genModel);

		loadDefaultGenModelContent(genModel);
		 
        //genModel.setComplianceLevel(GenJDKLevel.JDK80_LITERAL);
        genModel.setModelDirectory(options.project.path()+"/gen/");
        genModel.getForeignModel().add(new Path(metaModelUri.path()).lastSegment());
        genModel.setModelName(options.project.name());
        genModel.setModelPluginID(options.project.name());
        genModel.setSuppressEMFMetaData(false);
        genModel.setCanGenerate(true);
        genModel.setContainmentProxies(false);
        genModel.setDynamicTemplates(false);
        genModel.setGenerateSchema(true);
        genModel.setUpdateClasspath(false);
        
        List<EPackage> ePack = new LinkedList<>();
        ePack.add(options.tgg.corrMetamodel());
        for(EPackage pack : importedPackages) {
        	genModel.addImport(pack.getNsURI());
        }
        genModel.initialize(ePack);

        genModel.validate();
        genModel.reconcile();
        
        for (final GenPackage genPackage : genModel.getGenPackages()) {
			setDefaultPackagePrefixes(genPackage);
		}
        
        GenPackage genPackage = (GenPackage) genModel.getGenPackages().get(0);
        genPackage.setPrefix(options.project.name());
        
        HiPEGenGenerator generator = new HiPEGenGenerator();
        generator.setInput(genModel);
        generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, options.project.name(), new BasicMonitor.Printing(System.out), ePack.get(0).getName());

        try {
            genModelResource.getDefaultSaveOptions().put(XMLResource.OPTION_ENCODING, "UTF-8");
            genModelResource.getDefaultSaveOptions().put(XMLResource.OPTION_EXTENDED_META_DATA, Boolean.TRUE);
            genModelResource.save(Collections.EMPTY_MAP);
        } catch (IOException e) {
            String msg = null;
            if (e instanceof FileNotFoundException) {
                msg = "Unable to open output file ";
            } else {
                msg = "Unexpected IO Exception writing ";
            }
            throw new RuntimeException(msg, e);
        }
	}
	
	protected void adjustRegistry(final GenModel genModel) {
		// Ugly hack added by gervarro: GenModel has to be screwed
		ResourceSet resourceSet = options.resourceHandler().getResourceSet();
		final EPackage.Registry registry = resourceSet.getPackageRegistry();
		resourceSet.setPackageRegistry(new EPackageRegistryImpl(registry));
		genModel.getExtendedMetaData();
		resourceSet.setPackageRegistry(registry);
	}
	
	public void loadDefaultGenModelContent(final GenModel genModel) {
		genModel.setComplianceLevel(GenJDKLevel.JDK80_LITERAL);
		//genModel.setModelName(genModel.eResource().getURI().trimFileExtension().lastSegment());
		genModel.setImporterID("org.eclipse.emf.importer.ecore");
		genModel.setCodeFormatting(true);
		genModel.setOperationReflection(true);
		genModel.setUpdateClasspath(false);
		genModel.setCanGenerate(true);
	}
	
	private void setDefaultPackagePrefixes(final GenPackage genPackage) {
		genPackage.setPrefix(MoflonUtil.lastCapitalizedSegmentOf(genPackage.getPrefix()));
		for (final GenPackage subPackage : genPackage.getSubGenPackages()) {
			setDefaultPackagePrefixes(subPackage);
		}
	}

	public Collection<EPackage> getImportedPackages() {
		return importedPackages;
	}
	
	public static IbexOptions registerResourceHandler(IbexOptions options, List<String> metaModelImports) throws IOException {
		HiPEBuilderUtil util = new HiPEBuilderUtil(options);
		options.resourceHandler(new TGGResourceHandler() {
			@Override
			protected void registerUserMetamodels() throws IOException {
				for(String imp : metaModelImports) {
					util.getImportedPackages().add(loadAndRegisterMetamodel(imp));	
				}
				String metaModelLocation = options.project.path() + "/model/" + options.project.name() + ".ecore";
				String genModelLocation = options.project.path() + "/model/" + options.project.name() + ".genmodel";
				EPackage metaModel = loadAndRegisterCorrMetamodel(metaModelLocation);
				util.generateMetaModelCode(base, metaModelLocation, genModelLocation, metaModel);
			}
			
			@Override
			protected void createAndPrepareResourceSet() {
				rs = new ResourceSetImpl();
				util.loadDefaultSettings();
			}
		});
		return options;
	}
}