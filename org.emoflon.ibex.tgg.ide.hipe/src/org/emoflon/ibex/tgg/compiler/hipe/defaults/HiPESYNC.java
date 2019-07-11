package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.FileNotFoundException;
import java.io.IOException;
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
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.sync.SYNC;
import org.moflon.core.utilities.MoflonUtil;
import org.moflon.emf.codegen.StandalonePackageDescriptor;
import org.moflon.emf.codegen.resource.GenModelResource;
import org.moflon.emf.codegen.resource.GenModelResourceFactory;

public class HiPESYNC extends SYNC {

	private List<String> metaModelImports;
	private List<EPackage> importedPackages = new LinkedList<>();
	
	public HiPESYNC(IbexOptions options, List<String> metaModelImports) throws IOException {
		super(options);
		this.metaModelImports = metaModelImports;
		
		createAndPrepareResourceSet();
		registerInternalMetamodels();
		registerUserMetamodels();
		
		loadTGG();
		
	}
	
	@Override
	protected void createAndPrepareResourceSet() {
		 rs = new ResourceSetImpl();
		 loadDefaultSettings();
	}

	@Override
	protected void registerUserMetamodels() throws IOException {
		for(String imp : metaModelImports) {
			importedPackages.add(loadAndRegisterMetamodel(imp));	
		}
		String metaModelLocation = options.projectPath() + "/model/" + options.projectName() + ".ecore";
		String genModelLocation = options.projectPath() + "/model/" + options.projectName() + ".genmodel";
		EPackage metaModel = loadAndRegisterCorrMetamodel(metaModelLocation);
		generateMetaModelCode(metaModelLocation, genModelLocation, metaModel);
	}
	
	public void loadDefaultSettings() {
		rs.getPackageRegistry().put("http://www.eclipse.org/emf/2002/GenModel",
				new StandalonePackageDescriptor("org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage"));

		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("genmodel",
				new GenModelResourceFactory());
		rs.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi", new XMIResourceFactoryImpl());
	}
	
	protected void generateMetaModelCode(String metaModelLocation, String genModelLocation, EPackage metaModel) {
		URI modelDirUri = URI.createURI(options.projectPath() + "/model/");
		modelDirUri =  modelDirUri.resolve(base);
		URI metaModelUri = URI.createURI(metaModelLocation);
		metaModelUri = metaModelUri.resolve(base);
		URI genModelUri = URI.createURI(genModelLocation);
		genModelUri = genModelUri.resolve(base);
		
		final GenModelResource genModelResource = (GenModelResource) rs.createResource(genModelUri);
		GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
		
		genModelResource.getContents().add(genModel);

		adjustRegistry(genModel);

		loadDefaultGenModelContent(genModel);
		 
        //genModel.setComplianceLevel(GenJDKLevel.JDK80_LITERAL);
        genModel.setModelDirectory(options.projectPath()+"/gen/");
        genModel.getForeignModel().add(new Path(metaModelUri.path()).lastSegment());
        genModel.setModelName(options.projectName());
        genModel.setModelPluginID(options.projectName());
        genModel.setSuppressEMFMetaData(false);
        genModel.setCanGenerate(true);
        genModel.setContainmentProxies(false);
        genModel.setDynamicTemplates(false);
        genModel.setGenerateSchema(true);
        genModel.setUpdateClasspath(false);
        
        List<EPackage> ePack = new LinkedList<>();
        ePack.add(options.getCorrMetamodel());
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
        genPackage.setPrefix(options.projectName());
        
        HiPEGenGenerator generator = new HiPEGenGenerator();
        generator.setInput(genModel);
        generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, options.projectName(), new BasicMonitor.Printing(System.out), ePack.get(0).getName());

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
		final EPackage.Registry registry = rs.getPackageRegistry();
		rs.setPackageRegistry(new EPackageRegistryImpl(registry));
		genModel.getExtendedMetaData();
		rs.setPackageRegistry(registry);
	}
	
	public void loadDefaultGenModelContent(final GenModel genModel) {
		genModel.setComplianceLevel(GenJDKLevel.JDK80_LITERAL);
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

}
