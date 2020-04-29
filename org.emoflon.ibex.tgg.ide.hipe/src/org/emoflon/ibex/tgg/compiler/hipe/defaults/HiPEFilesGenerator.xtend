package org.emoflon.ibex.tgg.compiler.hipe.defaults

import java.util.Collection
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage
import org.emoflon.ibex.tgg.compiler.defaults.DefaultFilesGenerator
import org.moflon.core.utilities.MoflonUtil

class HiPEFilesGenerator extends DefaultFilesGenerator {

	public static final String DEFAULT_REGISTRATION_HELPER = "_DefaultRegistrationHelper";
	public static final String MODELGEN_APP = "MODELGEN_App";
	public static final String SYNC_APP = "SYNC_App";
	public static final String INITIAL_FWD_APP = "INITIAL_FWD_App";
	public static final String INITIAL_BWD_APP = "INITIAL_BWD_App";
	public static final String CC_APP = "CC_App";
	public static final String CO_APP = "CO_App";
	public static final String FWD_OPT_APP = "FWD_OPT_App";
	public static final String BWD_OPT_APP = "BWD_OPT_App";
	public static final String REGISTRATION_HELPER = "HiPERegistrationHelper";

	def static String generateRegHelperFie(GenPackage corrPackage, Collection<GenPackage> sourcePackages,
		Collection<GenPackage> targetPackages) {
		var variableNames = newHashMap
		var value = '''
			package org.emoflon.ibex.tgg.run.«corrPackage.packageName.toLowerCase».config;
			
			import java.io.File;
			import java.io.IOException;
			
			import org.eclipse.emf.common.util.URI;
			import org.eclipse.emf.ecore.EPackage;
			import org.eclipse.emf.ecore.resource.Resource;
			import org.eclipse.emf.ecore.resource.ResourceSet;
			import org.emoflon.ibex.tgg.operational.csp.constraints.factories.«corrPackage.packageName.toLowerCase».UserDefinedRuntimeTGGAttrConstraintFactory;
			import org.emoflon.ibex.tgg.operational.defaults.IbexOptions;
			import org.emoflon.ibex.tgg.operational.strategies.modules.IbexExecutable;
			import org.emoflon.ibex.tgg.operational.strategies.opt.BWD_OPT;
			import org.emoflon.ibex.tgg.operational.strategies.opt.FWD_OPT;
			import org.emoflon.ibex.tgg.runtime.hipe.HiPETGGEngine;
			import org.emoflon.ibex.tgg.compiler.defaults.IRegistrationHelper;
			
				public class «REGISTRATION_HELPER» implements IRegistrationHelper {
					
					/** Create default options **/
					public final void setWorkspaceRootDirectory(ResourceSet resourceSet) throws IOException {
						final String root = "../";
						URI key = URI.createPlatformResourceURI("/", true);
						URI value = URI.createFileURI(new File(root).getCanonicalPath() + File.separatorChar);
						resourceSet.getURIConverter().getURIMap().put(key, value);
					}
				
					/** Load and register source and target metamodels */
					public void registerMetamodels(ResourceSet rs, IbexExecutable executable) throws IOException {
						
						// Set correct workspace root
						setWorkspaceRootDirectory(rs);
						
						EPackage «corrPackage.packageInterfaceName.toFirstLower» = null;
						// Load and register source metamodels
		'''
		variableNames.put(corrPackage, corrPackage.packageInterfaceName.toFirstLower)

		for (genPackage : sourcePackages) {
			var name = genPackage.packageInterfaceName.toFirstLower
			var i = 1;
			while (variableNames.containsValue(name)){
				name = genPackage.packageInterfaceName.toFirstLower + i++
			}
			variableNames.put(genPackage, name)
			value += 
			'''		EPackage «name» = null;
			'''
		}

		value += 
		'''		// Load and register target metamodels
		'''

		for (genPackage : targetPackages) {
			var name = genPackage.packageInterfaceName.toFirstLower
			var i = 1;
			while (variableNames.containsValue(name)){
				name = genPackage.packageInterfaceName.toFirstLower + i++
			}
			variableNames.put(genPackage, name)
			value += 
			'''		EPackage «name» = null;
			'''
		}

		value += 
		'''		if(executable instanceof FWD_OPT) {
						Resource res = null;
		'''
		for (i : sourcePackages) {
			value += 
			'''				res = executable.getResourceHandler().loadResource("«i.getEcorePackage().nsURI»");
							«variableNames.get(i)» = (EPackage) res.getContents().get(0);
							rs.getResources().remove(res);
			'''
		}
		value += 
		'''				res = executable.getResourceHandler().loadResource("platform:/resource/«corrPackage.genModel.modelPluginID»/model/«corrPackage.NSName.toFirstUpper».ecore");
						«variableNames.get(corrPackage)» = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
					}
					
					if(executable instanceof BWD_OPT) {
						Resource res = null;
		'''
		for (i : targetPackages) {
			value += 
			'''			res = executable.getResourceHandler().loadResource("«i.getEcorePackage().nsURI»");
						«variableNames.get(i)» = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
			'''
		}
		value += 
		'''				res = executable.getResourceHandler().loadResource("platform:/resource/«corrPackage.genModel.modelPluginID»/model/«corrPackage.NSName.toFirstUpper».ecore");
						«variableNames.get(corrPackage)» = (EPackage) res.getContents().get(0);
						rs.getResources().remove(res);
				}
		'''

		for (i : sourcePackages) {
			value += 
			'''			if(«variableNames.get(i)» == null) {
							«variableNames.get(i)» = «i.qualifiedPackageInterfaceName».eINSTANCE;
						}
			'''
		}
		for (i : targetPackages) {
			value += 
			'''			if(«variableNames.get(i)» == null) {
							«variableNames.get(i)» = «i.qualifiedPackageInterfaceName».eINSTANCE;
						}
			'''
		}

		value += 
		'''			if(«variableNames.get(corrPackage)» == null) {
						«variableNames.get(corrPackage)» = «corrPackage.qualifiedPackageInterfaceName».eINSTANCE;
						rs.getPackageRegistry().put("platform:/resource/«corrPackage.genModel.modelPluginID»/model/«corrPackage.packageName.toFirstUpper».ecore", «corrPackage.qualifiedPackageInterfaceName».eINSTANCE);
						rs.getPackageRegistry().put("platform:/plugin/«corrPackage.genModel.modelPluginID»/model/«corrPackage.packageName.toFirstUpper».ecore", «corrPackage.qualifiedPackageInterfaceName».eINSTANCE);
					}
		'''
		for (i : sourcePackages) {
			value += 
			'''		rs.getPackageRegistry().put("«i.getEcorePackage.nsURI»",«i.qualifiedPackageInterfaceName».eINSTANCE);
			'''
		}
		for (i : targetPackages) {
			value += 
			'''			rs.getPackageRegistry().put("«i.getEcorePackage.nsURI»",«i.qualifiedPackageInterfaceName».eINSTANCE);
			'''
		}
		value += 
		'''	}
			
				/** Create default options **/
				public IbexOptions createIbexOptions() {
					IbexOptions options = new IbexOptions();
					options.blackInterpreter(new HiPETGGEngine());
					options.project.name("«MoflonUtil.lastCapitalizedSegmentOf(corrPackage.genModel.modelPluginID)»");
					options.project.path("«MoflonUtil.lastSegmentOf(corrPackage.genModel.modelPluginID)»");
					options.debug.ibexDebug(false);
					options.csp.userDefinedConstraints(new UserDefinedRuntimeTGGAttrConstraintFactory());
					options.registrationHelper(this);
					return options;
				}
			}
		'''
		return value
	}
}
