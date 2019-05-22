package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.runtime.Path;
import org.eclipse.emf.codegen.ecore.generator.Generator;
import org.eclipse.emf.codegen.ecore.genmodel.GenJDKLevel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenBaseGeneratorAdapter;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.sync.SYNC;

public class OperationalStrategyImpl extends SYNC {

	private List<String> metaModelImports;
	private List<EPackage> importedPackages = new LinkedList<>();
	
	public OperationalStrategyImpl(IbexOptions options, List<String> metaModelImports) throws IOException {
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
		 rs.getResourceFactoryRegistry().getExtensionToFactoryMap()
			.put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
	}

	@Override
	protected void registerUserMetamodels() throws IOException {
		for(String imp : metaModelImports) {
			importedPackages.add(loadAndRegisterMetamodel(imp));	
		}
		String metaModelLocation = options.projectPath() + "/model/" + options.projectName() + ".ecore";
		String genModelLocation = options.projectPath() + "/model/" + options.projectName() + ".genmodel";
		loadAndRegisterCorrMetamodel(metaModelLocation);
		generateMetaModelCode(metaModelLocation, genModelLocation);
	}
	
	protected void generateMetaModelCode(String metaModelLocation, String genModelLocation) {
		URI modelDirUri = URI.createURI(options.projectPath() + "/model/");
		modelDirUri =  modelDirUri.resolve(base);
		URI metaModelUri = URI.createURI(metaModelLocation);
		metaModelUri = metaModelUri.resolve(base);
		URI genModelUri = URI.createURI(genModelLocation);
		genModelUri = genModelUri.resolve(base);
		
		GenModel genModel = GenModelFactory.eINSTANCE.createGenModel();
		
        genModel.setComplianceLevel(GenJDKLevel.JDK80_LITERAL);
        genModel.setModelDirectory(options.projectPath()+"/src-gen/");
        genModel.getForeignModel().add(new Path(metaModelUri.path()).lastSegment());
        genModel.setModelName(options.projectName());
        List<EPackage> ePack = new LinkedList<>();
        ePack.add(options.getCorrMetamodel());
        for(EPackage pack : importedPackages) {
        	genModel.addImport(pack.getNsURI());
        }
        genModel.initialize(ePack);
        
        genModel.reconcile();
        genModel.setCanGenerate(true);
        genModel.setValidateModel(true);
        
        GenPackage genPackage = (GenPackage)genModel.getGenPackages().get(0);
        genPackage.setPrefix(options.projectName());
        
        Generator generator = new Generator();
        generator.setInput(genModel);
        generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE, options.projectName(), new BasicMonitor.Printing(System.out));

        try {
            final XMIResourceImpl genModelResource = new XMIResourceImpl(genModelUri);
            genModelResource.getDefaultSaveOptions().put(XMLResource.OPTION_ENCODING, "UTF-8");
            genModelResource.getContents().add(genModel);
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

}
