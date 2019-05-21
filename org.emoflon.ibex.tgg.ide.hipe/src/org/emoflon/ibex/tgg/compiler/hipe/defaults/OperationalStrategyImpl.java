package org.emoflon.ibex.tgg.compiler.hipe.defaults;

import java.io.IOException;
import java.util.List;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
import org.emoflon.ibex.tgg.operational.strategies.sync.SYNC;

public class OperationalStrategyImpl extends SYNC {

	private List<String> metaModelImports;
	
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
			loadAndRegisterMetamodel(imp);
		}
		loadAndRegisterCorrMetamodel(options.projectPath() + "/model/" + options.projectName() + ".ecore");
	}

}
